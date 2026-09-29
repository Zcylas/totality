package zcylas.totality.api.equipment;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import zcylas.totality.Totality;
import zcylas.totality.server.TotalityFakePlayer;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.item.Soulbound;
import zcylas.totality.api.item.TotalityArmorItem;
import zcylas.totality.api.item.TotalityItemComponents;
import zcylas.totality.api.rpg.combat.ArmorClass;
import zcylas.totality.init.ModEnchantments;
import zcylas.totality.init.items.EnergyItems;
import zcylas.totality.init.items.MagicItems;
import zcylas.totality.menu.equipment.AccessoryInventoryMenu;

import java.util.List;
import java.util.function.Supplier;

/**
 * Dev-environment-gated, opt-in (live-world) self-test of the Back equipment slot, driving the REAL
 * {@link AccessoryInventoryMenu} (server side, as a click from a client is processed), the REAL
 * {@link PlayerEquipmentComponent} and its render-only {@link BackEquipmentAppearance} mirror with fake players.
 * Covers: equip/unequip, invalid-item rejection, swapping and return, shift-click routing, save/load, owner-only
 * component sync, coexistence with chest armor, that the slot grants no AC/flight/movement/storage, that the existing
 * slots are unchanged, and death: ordinary equipment drops (Attunement does not protect it), Soulbound stacks are kept
 * (equipment and vanilla inventory), keepInventory, Curse of Vanishing, the End-return copy and no duplicates.
 */
public final class BackSlotVerification {

    private static final int MENU_BACK = 50;
    private static final int MENU_FIRST_INVENTORY = 9;
    /** A chunk of this suite's own (other suites force and release chunk 0, 0 on their own schedules). */
    private static final int DROP_CHUNK = 40;
    private static final int DROP_BLOCK = DROP_CHUNK * 16;

    private BackSlotVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(BackSlotVerification::run);
        ServerTickEvents.END_SERVER_TICK.register(BackSlotVerification::onServerTick);
    }

    private static void run(MinecraftServer server) {
        if (!VerificationReporter.isDevEnvironment()) return;
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "BackSlotVerification");
        ServerPlayer a = TotalityFakePlayer.create(server.overworld(), "BackSlotA");
        ServerPlayer b = TotalityFakePlayer.create(server.overworld(), "BackSlotB");
        try {
            a.setGameMode(GameType.SURVIVAL);
            PlayerEquipmentComponent eq = EquipmentComponents.get(a);
            AccessoryInventoryMenu menu = new AccessoryInventoryMenu(0, a.getInventory());
            ItemStack cape = new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK);
            ItemStack namedCape = new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK);
            namedCape.set(DataComponents.CUSTOM_NAME, Component.literal("Second Cape"));

            // ── Slot identity and validation ─────────────────────────────────
            check(r, "menu slot 50 is the Back slot (component index 6), capacity 1", () ->
                    menu.slots.get(MENU_BACK) instanceof TotalityAccessorySlot s
                            && s.container == eq && s.getContainerSlot() == PlayerEquipmentComponent.IDX_BACK
                            && s.getMaxStackSize(cape) == 1);
            check(r, "the cape is valid Back equipment", () -> menu.slots.get(MENU_BACK).mayPlace(cape));
            for (ItemStack invalid : new ItemStack[]{new ItemStack(Items.DIRT), new ItemStack(Items.IRON_CHESTPLATE),
                    new ItemStack(Items.ELYTRA), new ItemStack(MagicItems.RING_OF_PROTECTION), new ItemStack(EnergyItems.BASIC_COPPER_PHONE)}) {
                check(r, "rejected by the Back slot: " + invalid.getItem(), () ->
                        !menu.slots.get(MENU_BACK).mayPlace(invalid) && !eq.canPlaceItem(PlayerEquipmentComponent.IDX_BACK, invalid));
            }

            // ── Equip by clicking (pick up from the inventory, place in the slot) ─
            a.getInventory().setItem(MENU_FIRST_INVENTORY, cape.copy());
            menu.clicked(MENU_FIRST_INVENTORY, 0, ContainerInput.PICKUP, a);
            menu.clicked(MENU_BACK, 0, ContainerInput.PICKUP, a);
            check(r, "equip by click: the cape is in the Back slot, nothing carried", () ->
                    eq.getItem(PlayerEquipmentComponent.IDX_BACK).is(MagicItems.CAPE_OF_THE_MOUNTEBANK) && menu.getCarried().isEmpty()
                            && a.getInventory().getItem(MENU_FIRST_INVENTORY).isEmpty());
            check(r, "equip: the render-only mirror shows the cape", () ->
                    BackEquipmentAppearance.get(a).is(MagicItems.CAPE_OF_THE_MOUNTEBANK));

            // ── Invalid item by click: refused, the cape stays ────────────────
            menu.setCarried(new ItemStack(Items.DIRT));
            menu.clicked(MENU_BACK, 0, ContainerInput.PICKUP, a);
            check(r, "invalid click: dirt refused, the cape stays, dirt still carried", () ->
                    eq.getItem(PlayerEquipmentComponent.IDX_BACK).is(MagicItems.CAPE_OF_THE_MOUNTEBANK) && menu.getCarried().is(Items.DIRT));
            menu.setCarried(ItemStack.EMPTY);

            // ── Swap: clicking with another cape exchanges them ───────────────
            menu.setCarried(namedCape.copy());
            menu.clicked(MENU_BACK, 0, ContainerInput.PICKUP, a);
            check(r, "swap: the second cape is worn, the first is returned to the cursor", () ->
                    eq.getItem(PlayerEquipmentComponent.IDX_BACK).has(DataComponents.CUSTOM_NAME)
                            && menu.getCarried().is(MagicItems.CAPE_OF_THE_MOUNTEBANK) && !menu.getCarried().has(DataComponents.CUSTOM_NAME));
            menu.setCarried(ItemStack.EMPTY);

            // ── Shift-click out (returns to the inventory) and back in ────────
            menu.quickMoveStack(a, MENU_BACK);
            check(r, "shift-click out: slot empty, cape returned to the inventory, mirror cleared", () ->
                    eq.getItem(PlayerEquipmentComponent.IDX_BACK).isEmpty() && countIn(a, MagicItems.CAPE_OF_THE_MOUNTEBANK) == 1
                            && BackEquipmentAppearance.get(a).isEmpty());
            int capeSlot = findIn(a, MagicItems.CAPE_OF_THE_MOUNTEBANK);
            int menuIndex = capeSlot < 9 ? 36 + capeSlot : capeSlot;
            menu.quickMoveStack(a, menuIndex);
            check(r, "shift-click in: a cape from the inventory goes to the Back slot", () ->
                    eq.getItem(PlayerEquipmentComponent.IDX_BACK).is(MagicItems.CAPE_OF_THE_MOUNTEBANK) && countIn(a, MagicItems.CAPE_OF_THE_MOUNTEBANK) == 0);
            a.getInventory().setItem(MENU_FIRST_INVENTORY, new ItemStack(Items.DIRT));
            menu.quickMoveStack(a, MENU_FIRST_INVENTORY);
            check(r, "shift-click: an ordinary item never goes to the Back slot", () ->
                    eq.getItem(PlayerEquipmentComponent.IDX_BACK).is(MagicItems.CAPE_OF_THE_MOUNTEBANK));
            a.getInventory().clearContent();

            // ── Independent of chest armor; grants nothing by itself ──────────
            eq.setItem(PlayerEquipmentComponent.IDX_BACK, ItemStack.EMPTY);
            a.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            int acArmorOnly = ArmorClass.calculate(a);
            double speed = a.getAttributeValue(Attributes.MOVEMENT_SPEED);
            boolean mayFly = a.getAbilities().mayfly;
            int ringAc = eq.getAcBonus(), ringSave = eq.getSaveBonus();
            eq.setItem(PlayerEquipmentComponent.IDX_BACK, cape.copy());
            check(r, "chest armor and the cape are worn together", () ->
                    a.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                            && eq.getItem(PlayerEquipmentComponent.IDX_BACK).is(MagicItems.CAPE_OF_THE_MOUNTEBANK));
            check(r, "the cape adds no AC (" + acArmorOnly + " with the chestplate alone)", () -> ArmorClass.calculate(a) == acArmorOnly);
            check(r, "no equipment AC/save bonus, flight or movement change from the Back slot", () ->
                    eq.getAcBonus() == ringAc && eq.getSaveBonus() == ringSave && a.getAbilities().mayfly == mayFly
                            && a.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed);
            check(r, "the cape is not vanilla equipment: no equippable, glider or container data, no armor category", () ->
                    !cape.has(DataComponents.EQUIPPABLE) && !cape.has(DataComponents.GLIDER) && !cape.has(DataComponents.CONTAINER)
                            && ((TotalityArmorItem) cape.getItem()).getArmorCategory() == null && ((TotalityArmorItem) cape.getItem()).getAcBonus() == 0);
            a.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);

            // ── Save / load ──────────────────────────────────────────────────
            eq.setItem(PlayerEquipmentComponent.IDX_BACK, namedCape.copy());
            TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, server.registryAccess());
            eq.writeData(out);
            PlayerEquipmentComponent loaded = EquipmentComponents.get(b);
            loaded.readData(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), out.buildResult()));
            check(r, "save/load: the Back slot persists (with its item data)", () ->
                    loaded.getItem(PlayerEquipmentComponent.IDX_BACK).has(DataComponents.CUSTOM_NAME)
                            && ItemStack.matches(loaded.getItem(PlayerEquipmentComponent.IDX_BACK), namedCape));
            check(r, "save/load: loading also restores the render-only mirror", () ->
                    BackEquipmentAppearance.get(b).has(DataComponents.CUSTOM_NAME));
            loaded.clearContent();
            check(r, "clearing the component clears the mirror", () -> BackEquipmentAppearance.get(b).isEmpty());

            // ── Sync ─────────────────────────────────────────────────────────
            check(r, "the equipment component syncs to its owner only (never applied as a watcher's own)", () ->
                    eq.shouldSyncWith(a) && !eq.shouldSyncWith(b));

            // ── Existing slots unchanged ─────────────────────────────────────
            check(r, "existing menu slots keep their component indices (46 Phone, 47/48 Rings, 49 Pouch)", () ->
                    ((TotalityAccessorySlot) menu.slots.get(46)).getContainerSlot() == PlayerEquipmentComponent.IDX_PHONE
                            && ((TotalityAccessorySlot) menu.slots.get(47)).getContainerSlot() == PlayerEquipmentComponent.IDX_RING_1
                            && ((TotalityAccessorySlot) menu.slots.get(48)).getContainerSlot() == PlayerEquipmentComponent.IDX_RING_2
                            && ((TotalityAccessorySlot) menu.slots.get(49)).getContainerSlot() == PlayerEquipmentComponent.IDX_POUCH
                            && menu.slots.size() == 51);
            check(r, "existing slot rules unchanged: Phone takes a phone, Rings a ring, Pouch anything; none take the cape except Pouch", () ->
                    menu.slots.get(46).mayPlace(new ItemStack(EnergyItems.BASIC_COPPER_PHONE)) && !menu.slots.get(46).mayPlace(cape)
                            && menu.slots.get(47).mayPlace(new ItemStack(MagicItems.RING_OF_PROTECTION)) && !menu.slots.get(47).mayPlace(cape)
                            && menu.slots.get(49).mayPlace(new ItemStack(Items.DIRT)));
            check(r, "other component slots accept what they did before (canPlaceItem only restricts Back)", () ->
                    eq.canPlaceItem(PlayerEquipmentComponent.IDX_POUCH, new ItemStack(Items.DIRT))
                            && eq.canPlaceItem(PlayerEquipmentComponent.IDX_BELT, new ItemStack(Items.DIRT)));
            eq.setItem(PlayerEquipmentComponent.IDX_RING_1, new ItemStack(MagicItems.RING_OF_PROTECTION));
            check(r, "a Ring and the cape are worn together, independently", () ->
                    eq.getItem(PlayerEquipmentComponent.IDX_RING_1).is(MagicItems.RING_OF_PROTECTION)
                            && eq.getItem(PlayerEquipmentComponent.IDX_BACK).has(DataComponents.CUSTOM_NAME));
        } finally {
            EquipmentComponents.get(a).clearContent();
            EquipmentComponents.get(b).clearContent();
            a.discard();
            b.discard();
        }
        // ── Death, respawn and Soulbound: once the forced chunk's entities are loaded ─
        ServerLevel level = server.overworld();
        level.setChunkForced(DROP_CHUNK, DROP_CHUNK, true);
        pendingDeathChecks = () -> {
            deathChecks(r, server);
            r.summarize();
        };
        pendingDeadline = server.getTickCount() + 400;
    }

    private static Runnable pendingDeathChecks;
    private static int pendingDeadline;

    private static void onServerTick(MinecraftServer server) {
        if (pendingDeathChecks == null) return;
        boolean ready = server.overworld().areEntitiesLoaded(ChunkPos.pack(DROP_CHUNK, DROP_CHUNK));
        if (!ready && server.getTickCount() < pendingDeadline) return;
        Runnable checks = pendingDeathChecks;
        pendingDeathChecks = null;
        checks.run();
    }

    /**
     * Actual death, as far as fake players allow: the vanilla inventory drop ({@link Inventory#dropAll}, with the
     * Soulbound mixin), the custom equipment drop the death mixin calls ({@link PlayerEquipmentComponent#dropOnDeath}),
     * then the two respawn copies under test (see {@link #respawn}). {@code Player#dropEquipment} itself (and its
     * keepInventory gate) and the full {@code COPY_FROM} event are exercised by a real death in the client checkpoint;
     * here keepInventory skips the drops exactly as that gate does. Runs once the suite's chunk has its entities
     * loaded, so the dropped items can be counted.
     */
    private static void deathChecks(VerificationReporter r, MinecraftServer server) {
        ServerLevel level = server.overworld();
        Holder<Enchantment> soulbound = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ModEnchantments.SOULBOUND);
        Holder<Enchantment> vanishing = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.VANISHING_CURSE);
        check(r, "Soulbound is a registered enchantment (max level 1, supports ring, cape and phone)", () ->
                soulbound.value().getMaxLevel() == 1 && soulbound.value().isSupportedItem(new ItemStack(MagicItems.RING_OF_PROTECTION))
                        && soulbound.value().isSupportedItem(new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK))
                        && soulbound.value().isSupportedItem(new ItemStack(EnergyItems.BASIC_COPPER_PHONE)));
        check(r, "the drop chunk has its entities loaded for counting drops", () -> level.areEntitiesLoaded(ChunkPos.pack(DROP_CHUNK, DROP_CHUNK)));
        ServerPlayer dead = TotalityFakePlayer.create(level, "BackSlotDead");
        ServerPlayer fresh = TotalityFakePlayer.create(level, "BackSlotFresh");
        try {
            dead.snapTo(DROP_BLOCK + 8.5, 300, DROP_BLOCK + 8.5, 0, 0);
            fresh.snapTo(DROP_BLOCK + 8.5, 300, DROP_BLOCK + 8.5, 0, 0);
            PlayerEquipmentComponent deadEq = EquipmentComponents.get(dead);
            PlayerEquipmentComponent freshEq = EquipmentComponents.get(fresh);

            // ── Mixed case: Soulbound Ring kept, ordinary (attuned) Cape dropped ─
            ItemStack attunedCape = new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK);
            attunedCape.set(TotalityItemComponents.ATTUNED_TO, dead.getUUID());
            deadEq.setItem(PlayerEquipmentComponent.IDX_BACK, attunedCape);
            deadEq.setItem(PlayerEquipmentComponent.IDX_RING_1, enchanted(new ItemStack(MagicItems.RING_OF_PROTECTION), soulbound));
            deadEq.setItem(PlayerEquipmentComponent.IDX_PHONE, new ItemStack(EnergyItems.BASIC_COPPER_PHONE));
            deadEq.setItem(PlayerEquipmentComponent.IDX_POUCH, new ItemStack(Items.DIRT));
            dead.getInventory().setItem(0, enchanted(new ItemStack(Items.DIAMOND_SWORD), soulbound));
            dead.getInventory().setItem(1, new ItemStack(Items.STONE, 5));
            dead.setItemSlot(EquipmentSlot.CHEST, enchanted(new ItemStack(Items.IRON_CHESTPLATE), soulbound));
            clearDrops(level);
            dead.getInventory().dropAll();
            deadEq.dropOnDeath();
            List<ItemEntity> drops = drops(level);
            check(r, "death: the ordinary Cape (though attuned), Phone and Pouch item drop; the Soulbound Ring does not", () ->
                    dropped(drops, MagicItems.CAPE_OF_THE_MOUNTEBANK) == 1 && dropped(drops, EnergyItems.BASIC_COPPER_PHONE) == 1
                            && dropped(drops, Items.DIRT) == 1 && dropped(drops, MagicItems.RING_OF_PROTECTION) == 0);
            check(r, "death: the vanilla inventory drops its ordinary items; Soulbound sword and chestplate stay", () ->
                    dropped(drops, Items.STONE) == 5 && dropped(drops, Items.DIAMOND_SWORD) == 0 && dropped(drops, Items.IRON_CHESTPLATE) == 0
                            && dead.getInventory().getItem(0).is(Items.DIAMOND_SWORD) && dead.getInventory().getItem(1).isEmpty()
                            && dead.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE));
            check(r, "death: the dead player's slots hold only the Soulbound Ring; the Back mirror is cleared", () ->
                    deadEq.getItem(PlayerEquipmentComponent.IDX_RING_1).is(MagicItems.RING_OF_PROTECTION)
                            && deadEq.getItem(PlayerEquipmentComponent.IDX_BACK).isEmpty() && deadEq.getItem(PlayerEquipmentComponent.IDX_PHONE).isEmpty()
                            && deadEq.getItem(PlayerEquipmentComponent.IDX_POUCH).isEmpty() && BackEquipmentAppearance.get(dead).isEmpty());
            respawn(server, dead, fresh, false);
            check(r, "respawn: exactly the Soulbound items come back (Ring in its slot, sword and chestplate in theirs), once", () ->
                    freshEq.getItem(PlayerEquipmentComponent.IDX_RING_1).is(MagicItems.RING_OF_PROTECTION)
                            && freshEq.getItem(PlayerEquipmentComponent.IDX_BACK).isEmpty() && freshEq.getItem(PlayerEquipmentComponent.IDX_PHONE).isEmpty()
                            && fresh.getInventory().getItem(0).is(Items.DIAMOND_SWORD) && fresh.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                            && countIn(fresh, Items.DIAMOND_SWORD) == 1 && fresh.getInventory().getItem(1).isEmpty());
            check(r, "respawn: no stale Back mirror on the new player", () -> BackEquipmentAppearance.get(fresh).isEmpty());
            reset(level, dead, fresh);

            // ── Soulbound Cape: kept in the Back slot, still drawn ───────────
            deadEq.setItem(PlayerEquipmentComponent.IDX_BACK, enchanted(new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK), soulbound));
            deadEq.setItem(PlayerEquipmentComponent.IDX_RING_2, new ItemStack(MagicItems.RING_OF_PROTECTION));
            dead.getInventory().dropAll();
            deadEq.dropOnDeath();
            List<ItemEntity> drops2 = drops(level);
            respawn(server, dead, fresh, false);
            check(r, "Soulbound Cape: kept in the Back slot and mirrored on the new player; the ordinary Ring drops", () ->
                    dropped(drops2, MagicItems.CAPE_OF_THE_MOUNTEBANK) == 0 && dropped(drops2, MagicItems.RING_OF_PROTECTION) == 1
                            && freshEq.getItem(PlayerEquipmentComponent.IDX_BACK).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)
                            && freshEq.getItem(PlayerEquipmentComponent.IDX_RING_2).isEmpty()
                            && BackEquipmentAppearance.get(fresh).is(MagicItems.CAPE_OF_THE_MOUNTEBANK));
            reset(level, dead, fresh);

            // ── Curse of Vanishing wins: destroyed, never dropped ────────────
            ItemStack cursed = enchanted(new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK), vanishing);
            deadEq.setItem(PlayerEquipmentComponent.IDX_BACK, cursed);
            deadEq.dropOnDeath();
            check(r, "Curse of Vanishing: an equipped cursed Cape is destroyed, not dropped", () ->
                    dropped(drops(level), MagicItems.CAPE_OF_THE_MOUNTEBANK) == 0 && deadEq.getItem(PlayerEquipmentComponent.IDX_BACK).isEmpty());
            reset(level, dead, fresh);

            // ── keepInventory: nothing drops; everything is carried once ─────
            boolean keepBefore = level.getGameRules().get(GameRules.KEEP_INVENTORY);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule keep_inventory true");
            try {
                deadEq.setItem(PlayerEquipmentComponent.IDX_BACK, new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK));
                deadEq.setItem(PlayerEquipmentComponent.IDX_RING_1, new ItemStack(MagicItems.RING_OF_PROTECTION));
                dead.getInventory().setItem(0, enchanted(new ItemStack(Items.DIAMOND_SWORD), soulbound));
                // (with keepInventory, Player#dropEquipment drops nothing, so no drop step runs)
                respawn(server, dead, fresh, false);
                check(r, "keepInventory: the ordinary Cape and Ring are carried over", () ->
                        freshEq.getItem(PlayerEquipmentComponent.IDX_BACK).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)
                                && freshEq.getItem(PlayerEquipmentComponent.IDX_RING_1).is(MagicItems.RING_OF_PROTECTION)
                                && BackEquipmentAppearance.get(fresh).is(MagicItems.CAPE_OF_THE_MOUNTEBANK));
                check(r, "keepInventory: Soulbound adds no second copy (vanilla transfers the inventory itself)", () ->
                        fresh.getInventory().getItem(0).isEmpty());
            } finally {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule keep_inventory " + keepBefore);
            }
            reset(level, dead, fresh);

            // ── Leaving the End (alive): a lossless copy, not a death ────────
            deadEq.setItem(PlayerEquipmentComponent.IDX_BACK, new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK));
            respawn(server, dead, fresh, true);
            check(r, "returning from the End is not a death: the ordinary Cape is carried, nothing dropped", () ->
                    freshEq.getItem(PlayerEquipmentComponent.IDX_BACK).is(MagicItems.CAPE_OF_THE_MOUNTEBANK) && drops(level).isEmpty());
        } finally {
            reset(level, dead, fresh);
            dead.discard();
            fresh.discard();
            level.setChunkForced(DROP_CHUNK, DROP_CHUNK, false);
        }
    }

    /**
     * The respawn copies this suite is about, called directly: the equipment component's registered strategy
     * ({@code RespawnStrategy.ALWAYS_COPY}, i.e. {@code copyFrom}) and Soulbound's {@code COPY_FROM} handler. Firing the
     * whole event on fake players would also run every other system's respawn handler and disturb other suites.
     */
    private static void respawn(MinecraftServer server, ServerPlayer dead, ServerPlayer fresh, boolean alive) {
        EquipmentComponents.get(fresh).copyFrom(EquipmentComponents.get(dead), server.registryAccess());
        Soulbound.onCopyFrom(dead, fresh, alive);
    }

    private static ItemStack enchanted(ItemStack stack, Holder<Enchantment> enchantment) {
        stack.enchant(enchantment, 1);
        return stack;
    }

    private static List<ItemEntity> drops(ServerLevel level) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(DROP_BLOCK, -64, DROP_BLOCK, DROP_BLOCK + 16, 400, DROP_BLOCK + 16));
    }

    private static int dropped(List<ItemEntity> drops, Item item) {
        return drops.stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static void clearDrops(ServerLevel level) {
        drops(level).forEach(ItemEntity::discard);
    }

    private static void reset(ServerLevel level, ServerPlayer... players) {
        clearDrops(level);
        for (ServerPlayer p : players) {
            EquipmentComponents.get(p).clearContent();
            p.getInventory().clearContent();
        }
    }

    private static int countIn(ServerPlayer p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) n++;
        return n;
    }

    private static int findIn(ServerPlayer p, Item item) {
        for (int i = 0; i < 36; i++) if (p.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private static void check(VerificationReporter r, String label, Supplier<Boolean> body) {
        try {
            r.check(label, body.get(), "");
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}

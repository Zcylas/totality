package zcylas.totality.client.hologram.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.AABB;
import zcylas.totality.api.equipment.BackEquipmentAppearance;
import zcylas.totality.api.equipment.EquipmentComponents;
import zcylas.totality.api.equipment.PlayerEquipmentComponent;
import zcylas.totality.client.equipment.ClientEquipmentManager;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.init.ModEnchantments;
import zcylas.totality.init.items.EnergyItems;
import zcylas.totality.init.items.MagicItems;
import zcylas.totality.menu.equipment.AccessoryInventoryMenu;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Back slot checkpoint in the real client (scene 51; opt-in dev capture, inert in normal play). Every change goes
 * through the real paths: the Equipment menu is opened by the server as its button's payload handler does, and items are moved
 * with the client's own container clicks ({@code MultiPlayerGameMode#handleContainerInput}), validated by the server
 * and synced back. {@code -Dtotality.back.capture.phase=equip} (default) equips and swaps; {@code =reload} is run in a
 * second client launch on the same world to check persistence, then dies for real (/kill) under the death rules:
 * ordinary equipment drops, Soulbound stays, keepInventory keeps. Prefix {@code BS_}.
 */
final class BackSlotCapture {

    private BackSlotCapture() {}

    private static final String PHASE = System.getProperty("totality.back.capture.phase", "equip");
    private static final int BACK = 50;

    private static Step click(String label, IntSupplier slot) {
        return HologramCapture.run("click " + label, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot.getAsInt(), 0, ContainerInput.PICKUP, mc.player);
        });
    }

    private static int menuSlotOf(Item item, boolean named) {
        var slots = Minecraft.getInstance().player.containerMenu.slots;
        for (int i = 9; i < 45; i++) {
            ItemStack s = slots.get(i).getItem();
            if (s.is(item) && s.has(DataComponents.CUSTOM_NAME) == named) return i;
        }
        return -1;
    }

    private static int emptyInventorySlot() {
        var slots = Minecraft.getInstance().player.containerMenu.slots;
        for (int i = 9; i < 45; i++) if (!slots.get(i).hasItem()) return i;
        return -1;
    }

    /** A PASS/FAIL line about the authoritative (server) state, evaluated on the server thread. */
    private static Step serverCheck(String label, Predicate<ServerPlayer> condition) {
        return mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> HologramCapture.log(
                    (condition.test(server.getPlayerList().getPlayers().getFirst()) ? "PASS: " : "FAIL: ") + "server: " + label));
            return true;
        };
    }

    private static ItemStack serverBack(ServerPlayer p) {
        return EquipmentComponents.get(p).getItem(PlayerEquipmentComponent.IDX_BACK);
    }

    private static boolean clientSees(Predicate<ItemStack> test) {
        Minecraft mc = Minecraft.getInstance();
        return test.test(mc.player.containerMenu.slots.get(BACK).getItem())
                && test.test(ClientEquipmentManager.getStack(PlayerEquipmentComponent.IDX_BACK))
                && test.test(BackEquipmentAppearance.get(mc.player));
    }

    /**
     * The integrated server opens the Equipment menu for the player, as the screen button's payload handler does
     * (capture code never sends packets itself; the menu, its clicks and the sync are all real).
     */
    static void openOnServer() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p instanceof TotalityFakePlayer) continue;
                p.openMenu(new SimpleMenuProvider((id, inventory, player) -> new AccessoryInventoryMenu(id, inventory), Component.empty()));
            }
        });
    }

    private static Step openScreen() {
        return HologramCapture.until("Equipment screen open", () ->
                        Minecraft.getInstance().player.containerMenu instanceof AccessoryInventoryMenu,
                () -> {
                    if (Minecraft.getInstance().player.containerMenu instanceof AccessoryInventoryMenu) return;
                    openOnServer();
                }, 60);
    }

    private static Step serverRun(String label, Consumer<ServerPlayer> action) {
        return mc -> {
            HologramCapture.log("run: " + label);
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> action.accept(server.getPlayerList().getPlayers().getFirst()));
            return true;
        };
    }

    private static Holder<Enchantment> soulbound(ServerPlayer p) {
        return p.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ModEnchantments.SOULBOUND);
    }

    private static ItemStack soulbound(ServerPlayer p, ItemStack stack) {
        stack.enchant(soulbound(p), 1);
        return stack;
    }

    private static final double[] DEATH_POS = new double[3];

    private static List<ItemEntity> dropsAtDeath(ServerPlayer p) {
        return p.level().getEntitiesOfClass(ItemEntity.class, new AABB(DEATH_POS[0] - 6, DEATH_POS[1] - 6, DEATH_POS[2] - 6,
                DEATH_POS[0] + 6, DEATH_POS[1] + 6, DEATH_POS[2] + 6));
    }

    private static int dropped(ServerPlayer p, Item item) {
        return dropsAtDeath(p).stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static int inInventory(ServerPlayer p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
        }
        return n;
    }

    /** An actual death: walk 24 blocks from the spawn (so the respawned player cannot pick the drops up), /kill, respawn. */
    private static void die(List<Step> s, boolean keepInventory) {
        s.add(HologramCapture.command("gamerule keep_inventory " + keepInventory));
        s.add(HologramCapture.command("execute at @s run tp @s ~24 ~ ~"));
        s.add(HologramCapture.waitTicks(10));
        s.add(serverRun("remember the death position", p -> {
            DEATH_POS[0] = p.getX();
            DEATH_POS[1] = p.getY();
            DEATH_POS[2] = p.getZ();
        }));
        s.add(HologramCapture.command("kill @s"));
        s.add(HologramCapture.waitTicks(30));
        s.add(HologramCapture.run("respawn", () -> Minecraft.getInstance().player.respawn()));
        s.add(HologramCapture.waitTicks(40));
    }

    /** Real deaths (/kill) under the canonical rules: ordinary equipment drops, Soulbound stays, keepInventory keeps. */
    private static void deaths(List<Step> s) {
        // ── Mixed: ordinary (named) Cape and Phone drop; Soulbound Ring and sword stay ─
        s.add(HologramCapture.command("clear @s"));                      // the vanilla inventory only; equipment stays
        s.add(serverRun("equip a Soulbound Ring, an ordinary Phone; a Soulbound sword and 5 stone in the hotbar", p -> {
            PlayerEquipmentComponent eq = EquipmentComponents.get(p);
            eq.setItem(PlayerEquipmentComponent.IDX_RING_1, soulbound(p, new ItemStack(MagicItems.RING_OF_PROTECTION)));
            eq.setItem(PlayerEquipmentComponent.IDX_PHONE, new ItemStack(EnergyItems.BASIC_COPPER_PHONE));
            eq.sync();
            p.getInventory().setItem(0, soulbound(p, new ItemStack(Items.DIAMOND_SWORD)));
            p.getInventory().setItem(1, new ItemStack(Items.STONE, 5));
        }));
        s.add(HologramCapture.waitTicks(5));
        die(s, false);
        s.add(serverCheck("death: the ordinary Cape and Phone dropped where the player died; the Soulbound Ring did not", p ->
                dropped(p, MagicItems.CAPE_OF_THE_MOUNTEBANK) == 1 && dropped(p, EnergyItems.BASIC_COPPER_PHONE) == 1
                        && dropped(p, MagicItems.RING_OF_PROTECTION) == 0 && dropped(p, Items.STONE) == 5 && dropped(p, Items.DIAMOND_SWORD) == 0));
        s.add(serverCheck("respawn: Back and Phone empty, the Soulbound Ring in its slot, the Soulbound sword once, no stone", p ->
                serverBack(p).isEmpty() && EquipmentComponents.get(p).getItem(PlayerEquipmentComponent.IDX_PHONE).isEmpty()
                        && EquipmentComponents.get(p).getItem(PlayerEquipmentComponent.IDX_RING_1).is(MagicItems.RING_OF_PROTECTION)
                        && inInventory(p, Items.DIAMOND_SWORD) == 1 && inInventory(p, Items.STONE) == 0
                        && inInventory(p, MagicItems.CAPE_OF_THE_MOUNTEBANK) == 0));
        s.add(HologramCapture.check("client: after respawn the cache shows no Back item but the Ring; no stale Back mirror",
                () -> ClientEquipmentManager.getStack(PlayerEquipmentComponent.IDX_BACK).isEmpty()
                        && ClientEquipmentManager.getStack(PlayerEquipmentComponent.IDX_RING_1).is(MagicItems.RING_OF_PROTECTION)
                        && BackEquipmentAppearance.get(Minecraft.getInstance().player).isEmpty()));
        s.add(serverRun("clear the drops", p -> dropsAtDeath(p).forEach(ItemEntity::discard)));

        // ── keepInventory: an ordinary Cape is kept ──────────────────────
        s.add(serverRun("equip an ordinary Cape", p -> {
            EquipmentComponents.get(p).setItem(PlayerEquipmentComponent.IDX_BACK, new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK));
            EquipmentComponents.get(p).sync();
        }));
        s.add(HologramCapture.waitTicks(5));
        die(s, true);
        s.add(serverCheck("keepInventory: nothing dropped; the ordinary Cape is still worn", p ->
                dropsAtDeath(p).isEmpty() && serverBack(p).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)));
        s.add(HologramCapture.check("client: keepInventory respawn shows the Cape in the cache and the render mirror",
                () -> ClientEquipmentManager.getStack(PlayerEquipmentComponent.IDX_BACK).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)
                        && BackEquipmentAppearance.get(Minecraft.getInstance().player).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)));

        // ── A Soulbound Cape stays, and is still drawn after respawn ─────
        s.add(serverRun("make the worn Cape Soulbound", p -> {
            EquipmentComponents.get(p).setItem(PlayerEquipmentComponent.IDX_BACK, soulbound(p, new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK)));
            EquipmentComponents.get(p).sync();
        }));
        s.add(HologramCapture.waitTicks(5));
        die(s, false);
        s.add(serverCheck("Soulbound Cape: not dropped, still worn after respawn", p ->
                dropped(p, MagicItems.CAPE_OF_THE_MOUNTEBANK) == 0 && serverBack(p).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)));
        s.add(HologramCapture.check("client: the Soulbound Cape is in the cache and the render mirror after respawn",
                () -> ClientEquipmentManager.getStack(PlayerEquipmentComponent.IDX_BACK).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)
                        && BackEquipmentAppearance.get(Minecraft.getInstance().player).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)));
        s.add(HologramCapture.command("gamerule keep_inventory false"));
    }

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        s.add(HologramCapture.command("gamerule send_command_feedback false"));
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(HologramCapture.command("time set 6000"));
        if (PHASE.equals("reload")) {
            // ── Second launch: the Back slot came back from disk ─────────────
            s.add(HologramCapture.waitTicks(20));
            s.add(serverCheck("after a client restart the Back slot still holds the second cape", p ->
                    serverBack(p).is(MagicItems.CAPE_OF_THE_MOUNTEBANK) && serverBack(p).has(DataComponents.CUSTOM_NAME)));
            s.add(HologramCapture.check("client: after the restart the local cache and the render mirror show it",
                    () -> ClientEquipmentManager.getStack(PlayerEquipmentComponent.IDX_BACK).has(DataComponents.CUSTOM_NAME)
                            && BackEquipmentAppearance.get(Minecraft.getInstance().player).has(DataComponents.CUSTOM_NAME)));
            s.add(HologramCapture.run("allow screens", () -> HologramCapture.allowScreens = true));
            s.add(openScreen());
            s.add(HologramCapture.waitTicks(10));
            s.add(HologramCapture.check("client: the reopened screen shows it in slot 50",
                    () -> clientSees(st -> st.has(DataComponents.CUSTOM_NAME))));
            s.add(HologramCapture.screenshot("BS_after_restart"));
            s.add(HologramCapture.run("close screen", () -> Minecraft.getInstance().player.closeContainer()));
            s.add(HologramCapture.run("screens back to normal", () -> HologramCapture.allowScreens = false));
            deaths(s);
            return s;
        }
        // ── First launch: equip, reject, swap, coexist with chest armor ──────
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.run("empty the equipment slots", () -> Minecraft.getInstance().getSingleplayerServer().execute(() ->
                EquipmentComponents.get(Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers().getFirst())
                        .clearContent())));
        s.add(HologramCapture.command("give @s totality:cape_of_the_mountebank"));
        s.add(HologramCapture.command("give @s totality:cape_of_the_mountebank[custom_name='Second Cape']"));
        s.add(HologramCapture.command("give @s minecraft:dirt 4"));
        s.add(HologramCapture.command("give @s minecraft:iron_chestplate"));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.run("allow screens", () -> HologramCapture.allowScreens = true));
        s.add(openScreen());
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.screenshot("BS_empty_slot"));

        s.add(click("the cape (pick up)", () -> menuSlotOf(MagicItems.CAPE_OF_THE_MOUNTEBANK, false)));
        s.add(HologramCapture.waitTicks(3));
        s.add(click("the Back slot (place)", () -> BACK));
        s.add(HologramCapture.waitTicks(6));
        s.add(serverCheck("equip by click: the server's Back slot holds the cape", p -> serverBack(p).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)));
        s.add(HologramCapture.check("client: slot 50, the local cache and the render mirror all show the cape",
                () -> clientSees(st -> st.is(MagicItems.CAPE_OF_THE_MOUNTEBANK) && !st.has(DataComponents.CUSTOM_NAME))
                        && Minecraft.getInstance().player.containerMenu.getCarried().isEmpty()));
        s.add(HologramCapture.screenshot("BS_equipped"));

        s.add(click("dirt (pick up)", () -> menuSlotOf(Items.DIRT, false)));
        s.add(HologramCapture.waitTicks(3));
        s.add(click("the Back slot with dirt", () -> BACK));
        s.add(HologramCapture.waitTicks(6));
        s.add(serverCheck("dirt was refused: the Back slot still holds the cape", p -> serverBack(p).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)
                && !serverBack(p).is(Items.DIRT)));
        s.add(HologramCapture.check("client: the dirt is still on the cursor, the cape still shown",
                () -> Minecraft.getInstance().player.containerMenu.getCarried().is(Items.DIRT)
                        && clientSees(st -> st.is(MagicItems.CAPE_OF_THE_MOUNTEBANK))));
        s.add(HologramCapture.screenshot("BS_dirt_refused"));
        s.add(click("an empty slot (put the dirt back)", BackSlotCapture::emptyInventorySlot));
        s.add(HologramCapture.waitTicks(3));

        s.add(HologramCapture.run("shift-click the chestplate (vanilla chest slot)", () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, menuSlotOf(Items.IRON_CHESTPLATE, false), 0,
                    ContainerInput.QUICK_MOVE, mc.player);
        }));
        s.add(HologramCapture.waitTicks(6));
        s.add(serverCheck("chest armor and the cape are worn together", p ->
                p.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                        && serverBack(p).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)));

        s.add(click("the second cape (pick up)", () -> menuSlotOf(MagicItems.CAPE_OF_THE_MOUNTEBANK, true)));
        s.add(HologramCapture.waitTicks(3));
        s.add(click("the Back slot (swap)", () -> BACK));
        s.add(HologramCapture.waitTicks(6));
        s.add(serverCheck("swap: the second cape is worn", p -> serverBack(p).has(DataComponents.CUSTOM_NAME)));
        s.add(HologramCapture.check("client: the first cape is returned to the cursor, the second shown in the slot",
                () -> Minecraft.getInstance().player.containerMenu.getCarried().is(MagicItems.CAPE_OF_THE_MOUNTEBANK)
                        && !Minecraft.getInstance().player.containerMenu.getCarried().has(DataComponents.CUSTOM_NAME)
                        && clientSees(st -> st.has(DataComponents.CUSTOM_NAME))));
        s.add(HologramCapture.screenshot("BS_swapped"));
        s.add(click("an empty slot (put the first cape away)", BackSlotCapture::emptyInventorySlot));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.screenshot("BS_with_chestplate"));
        s.add(HologramCapture.run("close screen", () -> Minecraft.getInstance().player.closeContainer()));
        s.add(HologramCapture.run("screens back to normal", () -> HologramCapture.allowScreens = false));
        s.add(HologramCapture.waitTicks(10));
        return s;
    }
}

package zcylas.totality.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.mining.BlockBreaking;
import zcylas.totality.api.mining.BlockProfile;
import zcylas.totality.api.mining.BlockProfiles;
import zcylas.totality.api.mining.MiningImpact;
import zcylas.totality.api.mining.MiningOwnership;
import zcylas.totality.api.mining.MiningResult;
import zcylas.totality.api.mining.MiningSource;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.contributor.BlockDurabilityContributor;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Block Breaking V2 Pass 3 in a real client and a real integrated world (real player, ticking chunks, default game
 * rules): Tooltip V2 block rows agree with the mining engine's resolved profile for EVERY registered BlockItem, and the
 * vanilla behaviours the dataset must preserve — Silverfish release from Infested blocks (and Silk Touch suppression),
 * container contents on break, and vanilla-owned Cobweb with its Sword (string) / Shears (cobweb) drops. Results go to
 * {@code totality-block-breaking-pass3-results.txt}; the test fails at the end if any check failed.
 */
public class BlockBreakingPass3ClientGameTest implements FabricClientGameTest {

    private final List<String> results = new ArrayList<>();
    private int failures;

    private synchronized void check(String name, boolean ok, Object detail) {
        String line = (ok ? "PASS " : "FAIL ") + name + " — " + detail;
        results.add(line);
        System.out.println("[BlockBreakingPass3] " + line);
        if (!ok) failures++;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("time set day");
            context.runOnClient(this::tooltipEngineAgreement);
            world.getServer().runOnServer(this::vanillaBehaviour);
            screenshots(context, world);
        }
        context.runOnClient(mc -> {
            try {
                Files.write(mc.gameDirectory.toPath().resolve("totality-block-breaking-pass3-results.txt"), results);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
        if (failures > 0) throw new AssertionError(failures + " Block Breaking Pass 3 check(s) failed");
    }

    // ── Tooltip V2 block rows == engine profile, for every BlockItem ─────────────────────────

    private void tooltipEngineAgreement(Minecraft mc) {
        BlockDurabilityContributor contributor = new BlockDurabilityContributor();
        int checked = 0, ordinary = 0, silent = 0;
        List<String> mismatches = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof BlockItem blockItem)) continue;
            checked++;
            ItemStack stack = new ItemStack(item);
            List<Component> vanilla = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
            TooltipContext ctx = TooltipContext.hover(stack, TooltipDisclosureLevel.DEFAULT, vanilla, Optional.empty(), mc.player, mc.level);
            List<String> rows = new ArrayList<>();
            for (TooltipSection s : contributor.contribute(ctx)) {
                if (s instanceof TooltipSection.IconStatRow row) rows.add(row.label() + ": " + row.value());
            }
            BlockProfile.Resolved p = BlockProfiles.resolveStatic(blockItem.getBlock().defaultBlockState());
            List<String> expected = new ArrayList<>();
            if (p.ordinary()) {
                ordinary++;
                expected.add("Block Durability: " + Math.round(p.maxDurability()));
                expected.add("Required Mining Tier: " + p.requiredTier());
                if (p.tools().neutral()) expected.add("Effective Tool: Any");
                else if (!p.tools().tools().isEmpty()) expected.add("Effective Tool: " + String.join(", ",
                        p.tools().tools().stream().map(t -> t.name().charAt(0) + t.name().substring(1).toLowerCase()).toList()));
            } else {
                silent++;
            }
            if (!rows.equals(expected)) mismatches.add(BuiltInRegistries.ITEM.getKey(item) + " rows=" + rows + " expected=" + expected);
        }
        check("tooltip_engine_agreement.all_block_items", mismatches.isEmpty() && checked > 1000,
                "checked=" + checked + " ordinary=" + ordinary + " no-rows(SPECIAL/UNBREAKABLE/NA)=" + silent
                        + (mismatches.isEmpty() ? "" : " first mismatches=" + mismatches.subList(0, Math.min(5, mismatches.size()))));
        check("tooltip.special_blocks_show_no_block_durability",
                rows(mc, contributor, Items.COBWEB).isEmpty() && rows(mc, contributor, Items.TORCH).isEmpty()
                        && rows(mc, contributor, Items.WHEAT_SEEDS).isEmpty() && rows(mc, contributor, Items.BEDROCK).isEmpty(), "");
        check("tooltip.neutral_glass", rows(mc, contributor, Items.GLASS).contains("Effective Tool: Any")
                && rows(mc, contributor, Items.GLASS).contains("Block Durability: 50"), rows(mc, contributor, Items.GLASS));
        check("tooltip.deepslate_anchor", rows(mc, contributor, Items.DEEPSLATE).contains("Block Durability: 150"), rows(mc, contributor, Items.DEEPSLATE));
    }

    private static List<String> rows(Minecraft mc, BlockDurabilityContributor contributor, Item item) {
        ItemStack stack = new ItemStack(item);
        TooltipContext ctx = TooltipContext.hover(stack, TooltipDisclosureLevel.DEFAULT,
                stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL), Optional.empty(), mc.player, mc.level);
        List<String> out = new ArrayList<>();
        for (TooltipSection s : contributor.contribute(ctx)) if (s instanceof TooltipSection.IconStatRow row) out.add(row.label() + ": " + row.value());
        return out;
    }

    // ── preserved vanilla behaviour in a real world ──────────────────────────────────────────

    private void vanillaBehaviour(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = (ServerLevel) player.level();
        check("world.survival_player", player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, player.gameMode.getGameModeForPlayer());
        BlockPos pos = player.blockPosition().east(2);
        var enchants = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        AABB around = new AABB(pos).inflate(4);

        // Infested Stone: Silverfish released on break; Silk Touch prevents it (vanilla PREVENTS_INFESTED_SPAWNS).
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_PICKAXE));
        level.setBlockAndUpdate(pos, Blocks.INFESTED_STONE.defaultBlockState());
        MiningResult infested = strike(level, player, pos, 100f);
        int silverfish = level.getEntitiesOfClass(Silverfish.class, around).size();
        check("infested_stone.silverfish_released", infested.outcome() == MiningResult.Outcome.BROKEN && silverfish >= 1,
                infested + " silverfish=" + silverfish);
        level.getEntitiesOfClass(Silverfish.class, around).forEach(e -> e.discard());
        ItemStack silk = new ItemStack(Items.NETHERITE_PICKAXE);
        silk.enchant(enchants.getOrThrow(Enchantments.SILK_TOUCH), 1);
        player.setItemSlot(EquipmentSlot.MAINHAND, silk);
        level.setBlockAndUpdate(pos, Blocks.INFESTED_STONE.defaultBlockState());
        MiningResult silked = strike(level, player, pos, 100f);
        int silkedFish = level.getEntitiesOfClass(Silverfish.class, around).size();
        check("infested_stone.silk_touch_no_silverfish", silked.outcome() == MiningResult.Outcome.BROKEN && silkedFish == 0,
                silked + " silverfish=" + silkedFish);
        clearItems(level, around);

        // Chest: contents survive damage and are dropped by the vanilla terminal break.
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        MiningResult damaged = strike(level, player, pos, 40f);
        boolean kept = level.getBlockEntity(pos) instanceof ChestBlockEntity chest && chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 3;
        MiningResult broken = strike(level, player, pos, 100f);
        int diamonds = level.getEntitiesOfClass(ItemEntity.class, around).stream().filter(e -> e.getItem().is(Items.DIAMOND))
                .mapToInt(e -> e.getItem().getCount()).sum();
        check("chest.contents_kept_while_damaged_and_dropped_on_break",
                damaged.outcome() == MiningResult.Outcome.DAMAGED && kept && broken.outcome() == MiningResult.Outcome.BROKEN && diamonds == 3,
                damaged + " kept=" + kept + " " + broken + " diamonds=" + diamonds);
        clearItems(level, around);

        // Pass 3 reconciliation: block-entity entries keep their inventories while damaged and drop them on the vanilla break.
        for (var entry : List.of(new Object[]{BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("copper_chest")), Items.NETHERITE_PICKAXE, 150f},
                new Object[]{Blocks.OAK_SHELF, Items.NETHERITE_AXE, 100f}, new Object[]{Blocks.CRAFTER, Items.NETHERITE_PICKAXE, 150f})) {
            net.minecraft.world.level.block.Block block = (net.minecraft.world.level.block.Block) entry[0];
            float max = (Float) entry[2];
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack((Item) entry[1]));
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            boolean seeded = level.getBlockEntity(pos) instanceof net.minecraft.world.Container c && seed(c);
            MiningResult hit = strike(level, player, pos, 40f);
            boolean keptItems = level.getBlockEntity(pos) instanceof net.minecraft.world.Container c && c.getItem(0).is(Items.DIAMOND);
            MiningResult end = strike(level, player, pos, max);
            int dropped = level.getEntitiesOfClass(ItemEntity.class, around).stream().filter(e -> e.getItem().is(Items.DIAMOND))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            check("reconciliation." + BuiltInRegistries.BLOCK.getKey(block).getPath() + ".contents_kept_then_dropped",
                    seeded && hit.outcome() == MiningResult.Outcome.DAMAGED && hit.max() == max && keptItems
                            && end.outcome() == MiningResult.Outcome.BROKEN && dropped == 1,
                    "seeded=" + seeded + " " + hit + " kept=" + keptItems + " " + end + " diamonds=" + dropped);
            clearItems(level, around);
        }
        // Mob head: ordinary 50 HP, neutral; the vanilla break still drops the head item.
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        level.setBlockAndUpdate(pos, Blocks.SKELETON_SKULL.defaultBlockState());
        MiningResult skull = strike(level, player, pos, 50f);
        boolean skullDrop = level.getEntitiesOfClass(ItemEntity.class, around).stream().anyMatch(e -> e.getItem().is(Items.SKELETON_SKULL));
        check("reconciliation.skeleton_skull.50hp_vanilla_drop", skull.outcome() == MiningResult.Outcome.BROKEN && skull.max() == 50f && skullDrop,
                skull + " drop=" + skullDrop);
        clearItems(level, around);

        // Cobweb: SPECIAL, vanilla-owned; Totality never strikes it; vanilla Sword -> string, Shears -> cobweb.
        level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
        boolean notOwned = !MiningOwnership.owns(GameType.SURVIVAL, new ItemStack(Items.IRON_SWORD), level, pos, level.getBlockState(pos))
                && !MiningOwnership.owns(GameType.SURVIVAL, new ItemStack(Items.SHEARS), level, pos, level.getBlockState(pos))
                && !MiningOwnership.owns(GameType.SURVIVAL, ItemStack.EMPTY, level, pos, level.getBlockState(pos));
        MiningResult webStrike = strike(level, player, pos, 100f);
        check("cobweb.special_vanilla_owned", notOwned && webStrike.outcome() == MiningResult.Outcome.INVALID
                && level.getBlockState(pos).is(Blocks.COBWEB), "owned=" + !notOwned + " " + webStrike);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        player.gameMode.destroyBlock(pos);
        boolean string = level.getEntitiesOfClass(ItemEntity.class, around).stream().anyMatch(e -> e.getItem().is(Items.STRING));
        clearItems(level, around);
        level.setBlockAndUpdate(pos, Blocks.COBWEB.defaultBlockState());
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.SHEARS));
        player.gameMode.destroyBlock(pos);
        boolean web = level.getEntitiesOfClass(ItemEntity.class, around).stream().anyMatch(e -> e.getItem().is(Items.COBWEB));
        check("cobweb.vanilla_drops_sword_string_shears_cobweb", string && web, "string=" + string + " cobweb=" + web);
        clearItems(level, around);
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    // ── Tooltip screenshots through the genuine inventory hover path ─────────────────────────

    private static final Item[] SHOTS = {Items.GLASS, Items.DEEPSLATE_TILE_SLAB, Items.OAK_LEAVES, Items.COBWEB, Items.CRACKED_STONE_BRICKS, Items.SNOW,
            BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("copper_chest")), Items.SKELETON_SKULL, Items.CRIMSON_SLAB};
    private static final String[] SHOT_NAMES = {"p3_01_glass_no_preferred_tool", "p3_02_deepslate_tile_slab_75", "p3_03_oak_leaves_hoe_25",
            "p3_04_cobweb_special_no_block_rows", "p3_05_cracked_stone_bricks_exact_75", "p3_06_snow_layer_5",
            "p3r_07_copper_chest_150", "p3r_08_skeleton_skull_neutral_50", "p3r_09_crimson_slab_50"};

    private void screenshots(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            for (int i = 0; i < SHOTS.length; i++) player.getInventory().setItem(9 + i, new ItemStack(SHOTS[i]));
        });
        context.waitTicks(5);
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitForScreen(InventoryScreen.class);
        context.runOnClient(mc -> {
            mc.options.guiScale().set(2);
            mc.resizeGui();
        });
        context.waitTicks(2);
        for (int i = 0; i < SHOTS.length; i++) shot(context, SHOT_NAMES[i], 9 + i);
        context.setScreen(() -> null);
    }

    /** Moves the real cursor over the inventory slot, verifies the screen reports it hovered, then captures. */
    private void shot(ClientGameTestContext context, String name, int inventorySlot) {
        double[] cursor = context.computeOnClient(mc -> {
            InventoryScreen screen = (InventoryScreen) mc.gui.screen();
            Slot target = screen.getMenu().slots.stream()
                    .filter(sl -> sl.container == mc.player.getInventory() && sl.getContainerSlot() == inventorySlot)
                    .findFirst().orElseThrow();
            double guiX = (int) screenField(screen, "leftPos") + target.x + 8, guiY = (int) screenField(screen, "topPos") + target.y + 8;
            double ratio = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
            return new double[]{guiX * ratio, guiY * ratio};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(3);
        context.runOnClient(mc -> {
            mc.gui.toastManager().clear();
            Slot slot = (Slot) screenField(mc.gui.screen(), "hoveredSlot");
            check("screenshot.hovered." + name, slot != null && slot.getContainerSlot() == inventorySlot && !slot.getItem().isEmpty(),
                    slot == null ? "none" : BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()));
        });
        Path file = context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
        results.add("SHOT " + name + " -> " + file.getFileName());
    }

    private static Object screenField(Object screen, String name) {
        try {
            Field f = AbstractContainerScreen.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean seed(net.minecraft.world.Container container) {
        container.setItem(0, new ItemStack(Items.DIAMOND));
        return container.getItem(0).is(Items.DIAMOND);
    }

    private static MiningResult strike(ServerLevel level, ServerPlayer player, BlockPos pos, float damage) {
        return BlockBreaking.applyImpact(level, new MiningImpact(pos, Direction.UP, Vec3.atCenterOf(pos), damage, 4,
                MiningSource.of(MiningSource.Kind.PLAYER_TOOL, player)));
    }

    private static void clearItems(ServerLevel level, AABB box) {
        level.getEntitiesOfClass(ItemEntity.class, box).forEach(e -> e.discard());
    }
}

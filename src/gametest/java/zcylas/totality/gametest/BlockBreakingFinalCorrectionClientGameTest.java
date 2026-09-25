package zcylas.totality.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import zcylas.totality.api.mining.BlockDamageStorage;
import zcylas.totality.api.mining.BlockProfile;
import zcylas.totality.api.mining.BlockProfiles;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.TooltipRouting;
import zcylas.totality.client.tooltip.contributor.BlockDurabilityContributor;
import zcylas.totality.client.tooltip.contributor.EnchantmentsContributor;
import zcylas.totality.client.tooltip.contributor.ExternalContentContributor;
import zcylas.totality.client.tooltip.contributor.TooltipContributor;
import zcylas.totality.client.tooltip.contributor.TooltipContributorRegistry;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Block Breaking V2 final correction pass, in a real client and world:
 * <ul>
 *   <li>Repeater, Comparator, End Rod and Scaffolding are SPECIAL / vanilla-owned: no Block Durability, Required Mining Tier or
 *       Effective Tool row from ANY contributor, the tooltip agrees with the engine profile, and a survival player breaks them
 *       with vanilla timing and drops, with no Totality damage record.</li>
 *   <li>Single-level enchantments (Mending, Silk Touch, curses) show no redundant numeral in DEFAULT or SHIFT; multi-level
 *       enchantments keep theirs; curse names stay red.</li>
 *   <li>The tool-neutral "Effective Tool: Any" row, screenshotted at GUI scales 1-4.</li>
 * </ul>
 * Results go to {@code totality-block-breaking-final-correction-results.txt}; the test fails at the end if any check failed.
 */
public class BlockBreakingFinalCorrectionClientGameTest implements FabricClientGameTest {

    private static final List<Item> SPECIAL_ITEMS = List.of(Items.REPEATER, Items.COMPARATOR, Items.END_ROD, Items.SCAFFOLDING);
    private static final Set<String> MINING_ROWS = Set.of("Block Durability", "Required Mining Tier", "Effective Tool");

    private final List<String> results = new ArrayList<>();
    private int failures;

    private synchronized void check(String name, boolean ok, Object detail) {
        String line = (ok ? "PASS " : "FAIL ") + name + " — " + detail;
        results.add(line);
        System.out.println("[BlockBreakingFinalCorrection] " + line);
        if (!ok) failures++;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1920, 1080);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("time set day");
            world.getServer().runCommand("weather clear");
            context.setScreen(() -> null);
            context.runOnClient(this::specialTooltips);
            context.runOnClient(this::enchantmentRows);
            world.getServer().runOnServer(this::vanillaBreaking);
            screenshots(context, world);
        }
        context.runOnClient(mc -> {
            try {
                Files.write(mc.gameDirectory.toPath().resolve("totality-block-breaking-final-correction-results.txt"), results);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
        if (failures > 0) throw new AssertionError(failures + " final-correction check(s) failed");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    private static TooltipContext ctx(Minecraft mc, ItemStack stack, TooltipDisclosureLevel level) {
        return TooltipContext.hover(stack, level, stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL),
                Optional.empty(), mc.player, mc.level);
    }

    /** Every label/value row the whole Tooltip V2 document (all registered contributors) produces for the stack. */
    private static List<String> documentRows(Minecraft mc, ItemStack stack) {
        List<String> out = new ArrayList<>();
        TooltipContext c = ctx(mc, stack, TooltipDisclosureLevel.DEFAULT);
        for (TooltipContributor contributor : TooltipContributorRegistry.ordered()) {
            for (TooltipSection s : contributor.contribute(c)) {
                if (s instanceof TooltipSection.IconStatRow row) out.add(row.label() + ": " + row.value());
                if (s instanceof TooltipSection.StatRow row) out.add(row.label() + ": " + row.value());
            }
        }
        return out;
    }

    private static List<TooltipSection.StatRow> enchantRows(Minecraft mc, ItemStack stack, TooltipDisclosureLevel level) {
        List<TooltipSection.StatRow> rows = new ArrayList<>();
        for (TooltipSection s : new EnchantmentsContributor().contribute(ctx(mc, stack, level))) rows.add((TooltipSection.StatRow) s);
        return rows;
    }

    private static TooltipSection.StatRow row(List<TooltipSection.StatRow> rows, ResourceKey<Enchantment> key) {
        String name = Component.translatable("enchantment.minecraft." + key.identifier().getPath()).getString();
        return rows.stream().filter(r -> r.label().equals(name)).findFirst().orElse(null);
    }

    private static boolean isRed(int color) {
        return ((color >> 16) & 0xFF) >= 0xC0 && ((color >> 8) & 0xFF) < 0x80;
    }

    static ItemStack silkPickaxe(HolderLookup.Provider r) {
        var lookup = r.lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack s = new ItemStack(Items.DIAMOND_PICKAXE);
        s.enchant(lookup.getOrThrow(Enchantments.EFFICIENCY), 5);
        s.enchant(lookup.getOrThrow(Enchantments.UNBREAKING), 3);
        s.enchant(lookup.getOrThrow(Enchantments.SILK_TOUCH), 1);
        s.enchant(lookup.getOrThrow(Enchantments.MENDING), 1);
        return s;
    }

    static ItemStack curseBook(HolderLookup.Provider r) {
        var lookup = r.lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(lookup.getOrThrow(Enchantments.PROTECTION), 4);
        stored.set(lookup.getOrThrow(Enchantments.BINDING_CURSE), 1);
        stored.set(lookup.getOrThrow(Enchantments.VANISHING_CURSE), 1);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return book;
    }

    // ── 1. SPECIAL blocks: no mining rows anywhere in the document; tooltip == engine ────────────

    private void specialTooltips(Minecraft mc) {
        BlockDurabilityContributor contributor = new BlockDurabilityContributor();
        for (Item item : SPECIAL_ITEMS) {
            ItemStack stack = new ItemStack(item);
            BlockProfile.Resolved p = BlockProfiles.resolveStatic(((net.minecraft.world.item.BlockItem) item).getBlock().defaultBlockState());
            List<String> doc = documentRows(mc, stack);
            boolean noMiningRows = doc.stream().noneMatch(r -> MINING_ROWS.contains(r.substring(0, r.indexOf(':'))));
            check("special_tooltip." + BuiltInRegistries.ITEM.getKey(item).getPath(),
                    p.classification() == BlockProfile.Classification.SPECIAL && !p.ordinary()
                            && contributor.contribute(ctx(mc, stack, TooltipDisclosureLevel.DEFAULT)).isEmpty()
                            && contributor.contribute(ctx(mc, stack, TooltipDisclosureLevel.DETAILS_AND_TECHNICAL)).isEmpty() && noMiningRows,
                    p.classification() + " rows=" + doc);
        }
        List<String> glass = documentRows(mc, new ItemStack(Items.GLASS));
        check("neutral_glass.rows_unchanged", glass.contains("Block Durability: 50") && glass.contains("Required Mining Tier: 0")
                && glass.contains("Effective Tool: Any"), glass);
        List<String> stone = documentRows(mc, new ItemStack(Items.STONE));
        check("ordinary_stone.rows_unchanged", stone.contains("Block Durability: 100") && stone.contains("Effective Tool: Pickaxe"), stone);
        List<String> glowstone = documentRows(mc, new ItemStack(Items.GLOWSTONE));
        check("glowstone_kept_its_row_split_from_end_rod", glowstone.contains("Block Durability: 50")
                && glowstone.contains("Effective Tool: Any"), glowstone);
    }

    // ── 2. single-level enchantments, curses, DEFAULT vs SHIFT ─────────────────────────────────

    private void enchantmentRows(Minecraft mc) {
        HolderLookup.Provider r = mc.level.registryAccess();
        for (TooltipDisclosureLevel level : List.of(TooltipDisclosureLevel.DEFAULT, TooltipDisclosureLevel.DETAILS)) {
            boolean details = level == TooltipDisclosureLevel.DETAILS;
            List<TooltipSection.StatRow> pick = enchantRows(mc, silkPickaxe(r), level);
            var silk = row(pick, Enchantments.SILK_TOUCH);
            var mending = row(pick, Enchantments.MENDING);
            var efficiency = row(pick, Enchantments.EFFICIENCY);
            var unbreaking = row(pick, Enchantments.UNBREAKING);
            check("single_level.no_numeral." + level, pick.size() == 4 && silk != null && silk.value().isEmpty()
                    && mending != null && mending.value().isEmpty(), pick.stream().map(x -> x.label() + "|" + x.value()).toList());
            check("multi_level.keeps_numeral." + level, efficiency != null && unbreaking != null
                    && efficiency.value().equals(details ? "V / V" : "V") && unbreaking.value().equals(details ? "III / III" : "III"),
                    efficiency == null || unbreaking == null ? "missing" : efficiency.value() + ", " + unbreaking.value());
            check("non_curse.default_label_color." + level, pick.stream().allMatch(x -> x.labelColor() == TooltipSection.StatRow.DEFAULT_LABEL_COLOR), "");

            List<TooltipSection.StatRow> book = enchantRows(mc, curseBook(r), level);
            var binding = row(book, Enchantments.BINDING_CURSE);
            var vanishing = row(book, Enchantments.VANISHING_CURSE);
            var protection = row(book, Enchantments.PROTECTION);
            check("curse.red_name_without_numeral." + level, book.size() == 3 && binding != null && vanishing != null
                    && binding.value().isEmpty() && vanishing.value().isEmpty() && isRed(binding.labelColor()) && isRed(vanishing.labelColor())
                    && isRed(binding.valueColor()), book.stream().map(x -> x.label() + "|" + x.value() + "|" + Integer.toHexString(x.labelColor())).toList());
            check("curse.others_not_red." + level, protection != null && !isRed(protection.valueColor())
                    && protection.labelColor() == TooltipSection.StatRow.DEFAULT_LABEL_COLOR
                    && protection.value().equals(details ? "IV / IV" : "IV"), protection == null ? "missing" : protection.value());
        }
        // The raw vanilla lines these rows represent are still removed exactly once (the vanilla line is unchanged).
        for (ItemStack s : List.of(silkPickaxe(r), curseBook(r))) {
            List<String> ext = new ArrayList<>();
            for (TooltipSection sec : new ExternalContentContributor().contribute(ctx(mc, s, TooltipDisclosureLevel.DEFAULT))) {
                if (sec instanceof TooltipSection.ExternalContent e) e.lines().forEach(l -> ext.add(l.getString()));
            }
            List<TooltipSection.StatRow> rs = enchantRows(mc, s, TooltipDisclosureLevel.DEFAULT);
            check("no_duplicate_raw_line." + BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(),
                    rs.stream().noneMatch(row -> ext.stream().anyMatch(l -> l.startsWith(row.label()))), ext);
        }
        // Actual levels are untouched: the stack still carries Mending 1 / Silk Touch 1.
        ItemStack pick = silkPickaxe(r);
        var lookup = r.lookupOrThrow(Registries.ENCHANTMENT);
        check("levels_unchanged", pick.getEnchantments().getLevel(lookup.getOrThrow(Enchantments.MENDING)) == 1
                && pick.getEnchantments().getLevel(lookup.getOrThrow(Enchantments.SILK_TOUCH)) == 1
                && pick.getEnchantments().getLevel(lookup.getOrThrow(Enchantments.EFFICIENCY)) == 5, pick.getEnchantments());
    }

    // ── 3. vanilla break timing, drops and ownership in a real world ────────────────────────────

    private void vanillaBreaking(MinecraftServer server) {
        ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = (ServerLevel) player.level();
        BlockPos base = player.blockPosition().east(3);
        for (int i = 0; i < SPECIAL_ITEMS.size(); i++) {
            Item item = SPECIAL_ITEMS.get(i);
            Block block = ((net.minecraft.world.item.BlockItem) item).getBlock();
            BlockPos pos = base.offset(0, 0, i * 2);
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            BlockState placed = level.getBlockState(pos);
            float progress = placed.getDestroyProgress(player, level, pos);
            boolean noRecordBefore = BlockDamageStorage.get(level).get(pos, placed) == null;
            boolean broken = player.gameMode.destroyBlock(pos);
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5));
            boolean dropped = drops.stream().anyMatch(e -> e.getItem().is(item));
            boolean noRecord = noRecordBefore && BlockDamageStorage.get(level).get(pos, placed) == null;
            check("vanilla_break." + BuiltInRegistries.BLOCK.getKey(block).getPath(),
                    player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && placed.is(block) && progress >= 1f
                            && broken && level.getBlockState(pos).isAir() && dropped && noRecord,
                    "destroyProgress=" + progress + " broken=" + broken + " dropped=" + dropped + " noRecord=" + noRecord);
            drops.forEach(ItemEntity::discard);
        }
    }

    // ── 4. screenshots through the genuine inventory hover path ─────────────────────────────────

    private static final int GLASS = 9, REPEATER = 10, COMPARATOR = 11, END_ROD = 12, SCAFFOLDING = 13, PICK = 14, BOOK = 15, STONE = 16;

    private void screenshots(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            HolderLookup.Provider r = server.registryAccess();
            List<ItemStack> stacks = List.of(new ItemStack(Items.GLASS), new ItemStack(Items.REPEATER), new ItemStack(Items.COMPARATOR),
                    new ItemStack(Items.END_ROD), new ItemStack(Items.SCAFFOLDING), silkPickaxe(r), curseBook(r), new ItemStack(Items.STONE));
            for (int i = 0; i < stacks.size(); i++) player.getInventory().setItem(9 + i, stacks.get(i));
        });
        context.waitTicks(5);
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitForScreen(InventoryScreen.class);

        for (int scale : new int[]{1, 2, 3, 4}) {
            guiScale(context, scale);
            shot(context, "fc_gui" + scale + "_glass_effective_tool_any", GLASS);
        }
        guiScale(context, 2);
        shot(context, "fc_gui2_repeater_special_no_mining_rows", REPEATER);
        shot(context, "fc_gui2_comparator_special_no_mining_rows", COMPARATOR);
        shot(context, "fc_gui2_end_rod_special_no_mining_rows", END_ROD);
        shot(context, "fc_gui2_scaffolding_special_no_mining_rows", SCAFFOLDING);
        shot(context, "fc_gui2_silk_touch_mending_no_numeral", PICK);
        shot(context, "fc_gui2_curses_red_without_numeral", BOOK);
        context.getInput().holdShift();
        shot(context, "fc_gui2_silk_touch_mending_SHIFT", PICK);
        shot(context, "fc_gui2_curses_SHIFT", BOOK);
        context.getInput().releaseShift();
        guiScale(context, 4);
        shot(context, "fc_gui4_silk_touch_mending_no_numeral", PICK);

        // The window size the Pass 3 wrap was observed at: the narrow panel forces the tool-neutral row to wrap.
        context.getInput().resizeWindow(854, 480);
        context.waitTicks(2);
        for (int scale : new int[]{1, 2}) {
            guiScale(context, scale);
            shot(context, "fc_854x480_gui" + scale + "_glass_effective_tool_any", GLASS);
        }
        shot(context, "fc_854x480_gui2_stone_pickaxe_row", STONE);
        context.getInput().resizeWindow(1920, 1080);
        context.setScreen(() -> null);
    }

    private static void guiScale(ClientGameTestContext context, int scale) {
        context.runOnClient(mc -> {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
        });
        context.waitTicks(2);
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

    /** Moves the real cursor over the slot, verifies the screen itself reports it hovered, then captures. */
    private void shot(ClientGameTestContext context, String name, int inventorySlot) {
        double[] cursor = context.computeOnClient(mc -> {
            InventoryScreen screen = (InventoryScreen) mc.gui.screen();
            Slot target = screen.getMenu().slots.stream()
                    .filter(s -> s.container == mc.player.getInventory() && s.getContainerSlot() == inventorySlot)
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
            ItemStack stack = slot == null ? ItemStack.EMPTY : slot.getItem();
            check("screenshot.hovered." + name, slot != null && slot.getContainerSlot() == inventorySlot && !stack.isEmpty(),
                    BuiltInRegistries.ITEM.getKey(stack.getItem()) + " route=" + TooltipRouting.of(stack) + " disclosure="
                            + TooltipDisclosureLevel.resolve() + " gui=" + mc.options.guiScale().get());
        });
        Path file = context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
        results.add("SHOT " + name + " -> " + file.getFileName());
    }
}

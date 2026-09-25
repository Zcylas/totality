package zcylas.totality.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarityResolver;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.TooltipRouting;
import zcylas.totality.client.tooltip.TotalityTooltipRenderer;
import zcylas.totality.client.tooltip.contributor.EnchantmentsContributor;
import zcylas.totality.client.tooltip.contributor.ExternalContentContributor;
import zcylas.totality.client.tooltip.contributor.TooltipContributor;
import zcylas.totality.client.tooltip.contributor.TooltipContributorRegistry;
import zcylas.totality.client.tooltip.group.TooltipGroups;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Tooltip V2 ENCHANTMENTS integration — real registered items and real enchantment registries in a real client, the
 * item's genuine vanilla tooltip lines as input, real SHIFT/CTRL holds, and screenshots through the genuine inventory
 * hover path. Results go to {@code totality-enchantments-results.txt}; the test fails at the end if any check failed.
 */
public class TooltipV2EnchantmentsClientGameTest implements FabricClientGameTest {

    private static final ResourceKey<Enchantment> IMPACT =
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("totality", "impact"));
    static final String LORE_LINE = "Engraved by a village smith";

    private final List<String> results = new ArrayList<>();
    private int failures;

    private void check(String name, boolean ok, Object detail) {
        String line = (ok ? "PASS " : "FAIL ") + name + " — " + detail;
        results.add(line);
        System.out.println("[TooltipV2Enchantments] " + line);
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
            context.runOnClient(this::contentChecks);
            disclosureStates(context);
            screenshots(context, world);
        }
        context.runOnClient(mc -> {
            try {
                Files.write(mc.gameDirectory.toPath().resolve("totality-enchantments-results.txt"), results);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
        if (failures > 0) throw new AssertionError(failures + " enchantment check(s) failed — see totality-enchantments-results.txt");
    }

    // ── Real stacks ──────────────────────────────────────────────────────────────

    private static ItemStack enchant(HolderLookup.Provider registries, ItemStack stack, Object... keyLevel) {
        var lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        for (int i = 0; i < keyLevel.length; i += 2) {
            @SuppressWarnings("unchecked") ResourceKey<Enchantment> key = (ResourceKey<Enchantment>) keyLevel[i];
            stack.enchant(lookup.getOrThrow(key), (Integer) keyLevel[i + 1]);
        }
        return stack;
    }

    static ItemStack sharpSword(HolderLookup.Provider r) {
        return enchant(r, new ItemStack(Items.IRON_SWORD), Enchantments.SHARPNESS, 5);
    }

    static ItemStack multiSword(HolderLookup.Provider r) {
        ItemStack s = enchant(r, new ItemStack(Items.DIAMOND_SWORD), Enchantments.SHARPNESS, 5, Enchantments.UNBREAKING, 3,
                Enchantments.MENDING, 1, Enchantments.LOOTING, 3);
        s.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(LORE_LINE))));
        return s;
    }

    static ItemStack book(HolderLookup.Provider r) {
        var lookup = r.lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(lookup.getOrThrow(Enchantments.PROTECTION), 4);
        stored.set(lookup.getOrThrow(Enchantments.BINDING_CURSE), 1);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        return book;
    }

    static ItemStack cursedChestplate(HolderLookup.Provider r) {
        return enchant(r, new ItemStack(Items.IRON_CHESTPLATE), Enchantments.PROTECTION, 2, Enchantments.VANISHING_CURSE, 1);
    }

    static ItemStack impactPickaxe(HolderLookup.Provider r) {
        return enchant(r, new ItemStack(Items.IRON_PICKAXE), IMPACT, 3);
    }

    static ItemStack enchantedShuriken(HolderLookup.Provider r) {
        return enchant(r, new ItemStack(item("totality:netherite_shuriken")), Enchantments.SHARPNESS, 2);
    }

    static ItemStack enchantedExternal(HolderLookup.Provider r) {
        return enchant(r, new ItemStack(item(GameTestExternalItems.SAMPLE_RELIC_ID.toString())), Enchantments.UNBREAKING, 2);
    }

    private static Item item(String id) {
        Identifier key = Identifier.parse(id);
        Item item = BuiltInRegistries.ITEM.getValue(key);
        if (item == null || !BuiltInRegistries.ITEM.getKey(item).equals(key)) throw new IllegalStateException("not registered: " + id);
        return item;
    }

    private static List<Component> vanillaLines(Minecraft mc, ItemStack stack) {
        return stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
    }

    private static TooltipContext ctx(Minecraft mc, ItemStack stack, TooltipDisclosureLevel level) {
        return TooltipContext.hover(stack, level, vanillaLines(mc, stack), Optional.empty(), mc.player, mc.level);
    }

    private static List<TooltipSection.StatRow> rows(Minecraft mc, ItemStack stack, TooltipDisclosureLevel level) {
        List<TooltipSection.StatRow> rows = new ArrayList<>();
        for (TooltipSection s : new EnchantmentsContributor().contribute(ctx(mc, stack, level))) rows.add((TooltipSection.StatRow) s);
        return rows;
    }

    private static List<String> labels(List<TooltipSection.StatRow> rows) {
        return rows.stream().map(r -> (r.label() + " " + r.value()).strip()).toList();
    }

    /** Every string the V2 body would show from the raw vanilla lines (ExternalContent), with the real original lines. */
    private static List<String> external(Minecraft mc, ItemStack stack) {
        List<String> out = new ArrayList<>();
        for (TooltipSection s : new ExternalContentContributor().contribute(ctx(mc, stack, TooltipDisclosureLevel.DEFAULT))) {
            if (s instanceof TooltipSection.ExternalContent ext) ext.lines().forEach(l -> out.add(l.getString()));
        }
        return out;
    }

    private static boolean isRed(int color) {
        return ((color >> 16) & 0xFF) >= 0xC0 && ((color >> 8) & 0xFF) < 0x80;
    }

    // ── Content checks ───────────────────────────────────────────────────────────

    private void contentChecks(Minecraft mc) {
        HolderLookup.Provider r = mc.level.registryAccess();
        String sharpness = Component.translatable("enchantment.minecraft.sharpness").getString();

        // 1. Ordinary enchanted sword.
        List<TooltipSection.StatRow> sharp = rows(mc, sharpSword(r), TooltipDisclosureLevel.DEFAULT);
        check("sword.single_row", labels(sharp).equals(List.of(sharpness + " V")), labels(sharp));
        check("sword.group_is_enchantments", new EnchantmentsContributor().bodyGroup(null) == TooltipGroups.ENCHANTMENTS, "ENCHANTMENTS");

        // 2 + 6. Multiple enchantments, actual levels (single-level Mending shows no redundant I), vanilla tooltip order.
        ItemStack multi = multiSword(r);
        List<TooltipSection.StatRow> multiRows = rows(mc, multi, TooltipDisclosureLevel.DEFAULT);
        List<String> multiLabels = labels(multiRows);
        check("multi.four_rows_with_levels", new LinkedHashSet<>(multiLabels).equals(new LinkedHashSet<>(List.of(sharpness + " V",
                        Component.translatable("enchantment.minecraft.unbreaking").getString() + " III",
                        Component.translatable("enchantment.minecraft.mending").getString(),
                        Component.translatable("enchantment.minecraft.looting").getString() + " III"))) && multiLabels.size() == 4,
                multiLabels);
        List<String> vanilla = vanillaLines(mc, multi).stream().map(Component::getString).toList();
        List<Integer> order = multiRows.stream().map(row -> indexStartingWith(vanilla, row.label())).toList();
        check("multi.vanilla_tooltip_order", order.stream().allMatch(i -> i >= 0) && isAscending(order), "vanilla indices " + order);

        // 3. Enchanted book: stored enchantments, including a curse.
        List<TooltipSection.StatRow> bookRows = rows(mc, book(r), TooltipDisclosureLevel.DEFAULT);
        check("book.stored_enchantments", bookRows.size() == 2 && labels(bookRows).contains(
                Component.translatable("enchantment.minecraft.protection").getString() + " IV"), labels(bookRows));

        // 4. Curses are marked, not read as ordinary positive enchantments.
        for (ItemStack cursed : List.of(book(r), cursedChestplate(r))) {
            List<TooltipSection.StatRow> cr = rows(mc, cursed, TooltipDisclosureLevel.DEFAULT);
            boolean curseRed = cr.stream().filter(x -> x.label().startsWith(Component.translatable("enchantment.minecraft." +
                    (cursed.is(Items.ENCHANTED_BOOK) ? "binding_curse" : "vanishing_curse")).getString())).allMatch(x -> isRed(x.valueColor()));
            boolean othersNotRed = cr.stream().filter(x -> !x.label().contains("Curse")).noneMatch(x -> isRed(x.valueColor()));
            check("curse.marked_red." + BuiltInRegistries.ITEM.getKey(cursed.getItem()), curseRed && othersNotRed && cr.size() == 2, labels(cr));
        }

        // Registered Totality enchantment (data-driven totality:impact) — name from the registry/localization.
        List<TooltipSection.StatRow> impact = rows(mc, impactPickaxe(r), TooltipDisclosureLevel.DEFAULT);
        check("totality_enchantment.impact", labels(impact).equals(List.of(Component.translatable("enchantment.totality.impact").getString() + " III")),
                labels(impact));

        // 5. No enchantments -> no group.
        check("none.no_rows", rows(mc, new ItemStack(Items.DIAMOND_SWORD), TooltipDisclosureLevel.DEFAULT).isEmpty()
                && new EnchantmentsContributor().availableDisclosureLevels(ctx(mc, new ItemStack(Items.DIAMOND_SWORD),
                TooltipDisclosureLevel.DEFAULT)).isEmpty(), "no ENCHANTMENTS rows or SHIFT offer");

        // Concealed enchantments stay concealed: TooltipDisplay hiding ENCHANTMENTS -> no rows and no raw lines.
        ItemStack hidden = sharpSword(r);
        hidden.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.ENCHANTMENTS, true));
        check("hidden.respected", rows(mc, hidden, TooltipDisclosureLevel.DEFAULT).isEmpty()
                && external(mc, hidden).stream().noneMatch(s -> s.startsWith(sharpness)), external(mc, hidden));

        // 8 + 9. No duplicated enchantment lines; unrelated vanilla text (attributes, lore) preserved.
        for (ItemStack s : List.of(sharpSword(r), multi, book(r), cursedChestplate(r), impactPickaxe(r))) {
            List<String> ext = external(mc, s);
            List<TooltipSection.StatRow> rs = rows(mc, s, TooltipDisclosureLevel.DEFAULT);
            boolean noDuplicate = rs.stream().noneMatch(row -> ext.stream().anyMatch(l -> l.startsWith(row.label())));
            check("no_duplicates." + BuiltInRegistries.ITEM.getKey(s.getItem()), noDuplicate, "external=" + ext);
        }
        List<String> multiExternal = external(mc, multi);
        check("preserved.lore", multiExternal.contains(LORE_LINE), multiExternal);
        check("preserved.attributes", multiExternal.stream().anyMatch(l -> l.contains(
                Component.translatable("attribute.name.attack_damage").getString())), multiExternal);

        // 7. Rarity is unaffected by enchantments.
        check("rarity.enchanted_vanilla_stays_common", ItemRarityResolver.resolve(multi).equals(Optional.of(ItemRarity.COMMON)),
                ItemRarityResolver.resolve(multi) + " (vanilla's own: " + multi.getRarity() + ")");
        check("rarity.enchanted_totality_keeps_authored", ItemRarityResolver.resolve(enchantedShuriken(r)).equals(Optional.of(ItemRarity.EPIC)),
                ItemRarityResolver.resolve(enchantedShuriken(r)));

        // 11. External routing unchanged even when enchanted.
        ItemStack ext = enchantedExternal(r);
        check("external.routing_unchanged", TooltipRouting.of(ext) == TooltipRouting.EXTERNAL_ITEM && !TotalityTooltipRenderer.isEligible(ext),
                TooltipRouting.of(ext));
        check("external.original_enchantment_line_kept", vanillaLines(mc, ext).stream().anyMatch(l -> l.getString().startsWith(
                Component.translatable("enchantment.minecraft.unbreaking").getString())), "vanilla still prints its own line");

        // Whole-document sanity: all contributors together still produce exactly one row per enchantment.
        TooltipContext full = ctx(mc, multi, TooltipDisclosureLevel.DEFAULT);
        int rowsInDoc = 0;
        for (TooltipContributor c : TooltipContributorRegistry.ordered()) {
            for (TooltipSection s : c.contribute(full)) {
                if (s instanceof TooltipSection.StatRow row && multiRows.stream().anyMatch(m -> m.label().equals(row.label()))) rowsInDoc++;
            }
        }
        check("document.one_row_per_enchantment", rowsInDoc == multiRows.size(), rowsInDoc + " rows for " + multiRows.size() + " enchantments");
    }

    private static int indexStartingWith(List<String> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).startsWith(prefix)) return i;
        return -1;
    }

    private static boolean isAscending(List<Integer> values) {
        for (int i = 1; i < values.size(); i++) if (values.get(i) <= values.get(i - 1)) return false;
        return true;
    }

    // ── 10. DEFAULT / SHIFT / CTRL / SHIFT+CTRL with real key holds ─────────────────

    private record State(String name, boolean shift, boolean ctrl, TooltipDisclosureLevel expected, String sharpnessValue) {}

    private void disclosureStates(ClientGameTestContext context) {
        List<State> states = List.of(
                new State("DEFAULT", false, false, TooltipDisclosureLevel.DEFAULT, "V"),
                new State("SHIFT", true, false, TooltipDisclosureLevel.DETAILS, "V / V"),
                new State("CTRL", false, true, TooltipDisclosureLevel.TECHNICAL, "V"),
                new State("SHIFT+CTRL", true, true, TooltipDisclosureLevel.DETAILS_AND_TECHNICAL, "V / V"));
        for (State state : states) {
            if (state.shift()) context.getInput().holdShift();
            if (state.ctrl()) context.getInput().holdControl();
            context.waitTick();
            context.runOnClient(mc -> {
                TooltipDisclosureLevel level = TooltipDisclosureLevel.resolve();
                ItemStack multi = multiSword(mc.level.registryAccess());
                List<TooltipSection.StatRow> rs = rows(mc, multi, level);
                String sharpValue = rs.stream().filter(x -> x.label().equals(
                        Component.translatable("enchantment.minecraft.sharpness").getString())).map(TooltipSection.StatRow::value).findFirst().orElse("?");
                check("disclosure." + state.name(), level == state.expected() && sharpValue.equals(state.sharpnessValue())
                        && rs.size() == 4, level + " sharpness=" + sharpValue);
                check("renderer_stable." + state.name(), TotalityTooltipRenderer.isEligible(multi)
                        && !TotalityTooltipRenderer.isEligible(enchantedExternal(mc.level.registryAccess())), TooltipRouting.of(multi));
            });
            if (state.shift()) context.getInput().releaseShift();
            if (state.ctrl()) context.getInput().releaseControl();
            context.waitTick();
        }
    }

    // ── Screenshots through the genuine inventory hover path ──────────────────────

    private static final int SHARP = 9, MULTI = 10, BOOK = 11, CURSED = 12, PLAIN = 13, IMPACT_PICK = 14, SHURIKEN = 15, EXTERNAL = 16;

    private void screenshots(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().get(0);
            HolderLookup.Provider r = server.registryAccess();
            List<ItemStack> stacks = List.of(sharpSword(r), multiSword(r), book(r), cursedChestplate(r), new ItemStack(Items.DIAMOND_SWORD),
                    impactPickaxe(r), enchantedShuriken(r), enchantedExternal(r));
            for (int i = 0; i < stacks.size(); i++) player.getInventory().setItem(9 + i, stacks.get(i));
        });
        context.waitTicks(5);
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitForScreen(InventoryScreen.class);

        guiScale(context, 2);
        shot(context, "gui2_01_sword_sharpness_V", SHARP);
        shot(context, "gui2_02_multiple_enchantments_with_lore", MULTI);
        shot(context, "gui2_03_enchanted_book_stored_with_curse", BOOK);
        shot(context, "gui2_04_chestplate_with_curse_of_vanishing", CURSED);
        shot(context, "gui2_05_unenchanted_sword_no_group", PLAIN);
        shot(context, "gui2_06_totality_impact_enchantment", IMPACT_PICK);
        shot(context, "gui2_07_enchanted_totality_shuriken_keeps_EPIC", SHURIKEN);
        shot(context, "gui2_08_enchanted_external_item_original_tooltip", EXTERNAL);
        context.getInput().holdShift();
        shot(context, "gui2_09_multiple_SHIFT", MULTI);
        context.getInput().releaseShift();
        context.getInput().holdControl();
        shot(context, "gui2_10_multiple_CTRL", MULTI);
        context.getInput().holdShift();
        shot(context, "gui2_11_multiple_SHIFT_CTRL", MULTI);
        context.getInput().releaseShift();
        context.getInput().releaseControl();

        for (int scale : new int[]{1, 4}) {
            guiScale(context, scale);
            shot(context, "gui" + scale + "_01_multiple_enchantments_with_lore", MULTI);
            shot(context, "gui" + scale + "_02_enchanted_book_stored_with_curse", BOOK);
            shot(context, "gui" + scale + "_03_chestplate_with_curse_of_vanishing", CURSED);
        }
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

package zcylas.totality.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
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
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantments;
import zcylas.totality.api.core.rpgutils.rarity.Classification;
import zcylas.totality.api.core.rpgutils.rarity.ClassificationTypes;
import zcylas.totality.api.core.rpgutils.rarity.ClassificationsComponent;
import zcylas.totality.api.core.rpgutils.rarity.ItemClassificationResolver;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarityResolver;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.api.core.rpgutils.rarity.RarityComponent;
import zcylas.totality.api.core.rpgutils.rarity.RarityCoverage;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.TooltipRouting;
import zcylas.totality.client.tooltip.TotalityTooltipRenderer;
import zcylas.totality.client.tooltip.TotalityTooltipScrollHandler;
import zcylas.totality.api.industrial.energy.UEItem;
import zcylas.totality.client.tooltip.contributor.ExternalContentContributor;
import zcylas.totality.client.tooltip.contributor.TooltipContributor;
import zcylas.totality.client.tooltip.contributor.TooltipContributorRegistry;
import zcylas.totality.client.tooltip.presentation.TooltipIdentityLines;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Tooltip V2 Pass 1 — real registered-item tests. Runs inside a real client (runClientGameTest) with a fresh
 * singleplayer world, so item default components and tags are genuinely bound, Totality's own registrations are
 * the real ones, and SHIFT/CTRL are real simulated key holds. Screenshots hover real inventory slots, so the
 * tooltip goes through the genuine AbstractContainerScreen path with vanilla's own original lines.
 *
 * <p>All checks are collected (so one run reports every result) and the test fails at the end if any failed.
 */
public class TooltipV2Pass1ClientGameTest implements FabricClientGameTest {

    private final List<String> results = new ArrayList<>();
    private int failures;

    private void check(String name, boolean ok, Object detail) {
        String line = (ok ? "PASS " : "FAIL ") + name + " — " + detail;
        results.add(line);
        System.out.println("[TooltipV2Pass1] " + line);
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

            context.runOnClient(this::registryChecks);
            modifierStability(context);
            screenshots(context, world);
        }
        writeResults(context);
        if (failures > 0) throw new AssertionError(failures + " Tooltip V2 Pass 1 check(s) failed — see totality-pass1-results.txt");
    }

    // ── Real registered-item checks ────────────────────────────────────────────

    private static Item item(String id) {
        Identifier key = Identifier.parse(id);
        Item item = BuiltInRegistries.ITEM.getValue(key);
        if (item == null || !BuiltInRegistries.ITEM.getKey(item).equals(key)) throw new IllegalStateException("not registered: " + id);
        return item;
    }

    private static ItemStack decode(HolderLookup.Provider registries, String json) {
        return ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), JsonParser.parseString(json)).getOrThrow();
    }

    static final String FILLED_BUNDLE = "{\"id\":\"minecraft:bundle\",\"components\":{\"minecraft:bundle_contents\":"
            + "[{\"id\":\"minecraft:diamond\",\"count\":2},{\"id\":\"minecraft:apple\",\"count\":1}]}}";

    private static ItemStack enchantedSword(HolderLookup.Provider registries) {
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 3);
        return sword;
    }

    private static Classification pair(ItemType category, Identifier type) {
        return Classification.of(category, type);
    }

    private void registryChecks(Minecraft mc) {
        HolderLookup.Provider registries = mc.level.registryAccess();

        ItemStack diamond = new ItemStack(Items.DIAMOND);
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        ItemStack ironSword = new ItemStack(Items.IRON_SWORD);
        ItemStack ironPickaxe = new ItemStack(Items.IRON_PICKAXE);
        ItemStack bread = new ItemStack(Items.BREAD);
        ItemStack ironChestplate = new ItemStack(Items.IRON_CHESTPLATE);
        ItemStack stone = new ItemStack(Items.STONE);
        ItemStack oakPlanks = new ItemStack(Items.OAK_PLANKS);
        ItemStack wheatSeeds = new ItemStack(Items.WHEAT_SEEDS);
        ItemStack bow = new ItemStack(Items.BOW);
        ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
        ItemStack potion = new ItemStack(Items.POTION);
        ItemStack enchanted = enchantedSword(registries);
        ItemStack shuriken = new ItemStack(item("totality:netherite_shuriken"));
        ItemStack battery = new ItemStack(item("totality:copper_battery"));
        ItemStack pizza = new ItemStack(item("totality:pizza_margherita"));
        ItemStack soulGem = new ItemStack(item("totality:petty_soul_gem"));
        ItemStack coin = new ItemStack(item("totality:copper_coin"));
        ItemStack credits = new ItemStack(item("totality:credits"));
        ItemStack tinOre = new ItemStack(item("totality:tin_ore"));
        ItemStack energyCell = new ItemStack(item("totality:copper_energy_cell"));
        ItemStack bundle = decode(registries, FILLED_BUNDLE);
        ItemStack emptyBundle = new ItemStack(Items.BUNDLE);
        ItemStack external = new ItemStack(BuiltInRegistries.ITEM.getValue(GameTestExternalItems.SAMPLE_RELIC_ID));
        ItemStack hidden = new ItemStack(Items.DIAMOND);
        hidden.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new LinkedHashSet<>()));

        // 1. Routing: every ordinary vanilla and Totality item uses V2; functional components/hidden keep vanilla.
        List<ItemStack> ordinary = List.of(diamond, ironIngot, ironSword, ironPickaxe, bread, ironChestplate, stone,
                oakPlanks, wheatSeeds, bow, enchantedBook, potion, enchanted, shuriken, battery, pizza, soulGem, coin,
                credits, tinOre, energyCell);
        for (ItemStack s : ordinary) {
            check("routing.v2." + id(s), TooltipRouting.of(s) == TooltipRouting.TOTALITY && TotalityTooltipRenderer.isEligible(s),
                    TooltipRouting.of(s));
        }
        check("routing.empty", TooltipRouting.of(ItemStack.EMPTY) == TooltipRouting.NONE && !TotalityTooltipRenderer.isEligible(ItemStack.EMPTY),
                TooltipRouting.of(ItemStack.EMPTY));
        check("routing.bundle_filled_keeps_vanilla_component", bundle.getTooltipImage().isPresent()
                && TooltipRouting.of(bundle) == TooltipRouting.VANILLA_TOOLTIP_COMPONENT && !TotalityTooltipRenderer.isEligible(bundle),
                TooltipRouting.of(bundle));
        check("routing.bundle_empty_keeps_vanilla_component", TooltipRouting.of(emptyBundle) == TooltipRouting.VANILLA_TOOLTIP_COMPONENT,
                TooltipRouting.of(emptyBundle) + " image=" + emptyBundle.getTooltipImage().isPresent());
        check("routing.hidden_tooltip_stays_hidden", TooltipRouting.of(hidden) == TooltipRouting.VANILLA_HIDDEN, TooltipRouting.of(hidden));
        check("routing.external_namespace_keeps_original_tooltip", id(external).equals(GameTestExternalItems.SAMPLE_RELIC_ID.toString())
                && TooltipRouting.of(external) == TooltipRouting.EXTERNAL_ITEM && !TotalityTooltipRenderer.isEligible(external),
                id(external) + " route=" + TooltipRouting.of(external));
        check("rarity.external_not_rated", ItemRarityResolver.resolve(external).isEmpty(), ItemRarityResolver.resolve(external));
        List<String> externalLines = new ArrayList<>();
        external.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL).forEach(l -> externalLines.add(l.getString()));
        check("routing.external_original_lines_intact", externalLines.contains(GameTestExternalItems.LORE_LINE), externalLines);
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        results.add("INFO routing.filled_map (observed, not asserted) — " + TooltipRouting.of(map));

        // 2. Rarity.
        check("rarity.vanilla_fallback_common", !diamond.has(ItemComponents.RARITY)
                && ItemRarityResolver.resolve(diamond).equals(Optional.of(ItemRarity.COMMON)), ItemRarityResolver.resolve(diamond));
        check("rarity.vanilla_enchanted_not_raised", ItemRarityResolver.resolve(enchanted).equals(Optional.of(ItemRarity.COMMON)),
                ItemRarityResolver.resolve(enchanted) + " (vanilla's own rarity would be " + enchanted.getRarity() + ")");
        check("rarity.vanilla_exact_override_is_authored", stone.has(ItemComponents.RARITY)
                && ItemRarityResolver.resolve(stone).equals(Optional.of(ItemRarity.COMMON)), stone.get(ItemComponents.RARITY));
        ItemStack authoredDiamond = diamond.copy();
        authoredDiamond.set(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE));
        check("rarity.stack_authored_wins", ItemRarityResolver.resolve(authoredDiamond).equals(Optional.of(ItemRarity.RARE)),
                ItemRarityResolver.resolve(authoredDiamond));
        expectRarity(shuriken, ItemRarity.EPIC);
        expectRarity(battery, ItemRarity.CRUDE);
        // Canonical Industrial construction progression (Pass 1 final corrections) — batteries and gears.
        String[][] industrial = {{"copper", "CRUDE"}, {"iron", "CALIBRATED"}, {"gold", "PROTOTYPE"},
                {"diamond", "OVERCHARGED"}, {"netherite", "MASTERWORK"}};
        for (String[] tier : industrial) {
            expectRarity(new ItemStack(item("totality:" + tier[0] + "_battery")), ItemRarity.valueOf(tier[1]));
            expectRarity(new ItemStack(item("totality:" + tier[0] + "_gear")), ItemRarity.valueOf(tier[1]));
        }
        expectRarity(new ItemStack(item("totality:umbra_visor")), ItemRarity.CALIBRATED);
        // Presentation only: battery capacity and I/O are exactly the pre-task gameplay values.
        long[][] stats = {{48_000, 32, 32}, {320_000, 32, 32}, {128_000, 128, 128}, {1_000_000, 256, 256}, {5_000_000, 512, 512}};
        for (int i = 0; i < industrial.length; i++) {
            ItemStack b = new ItemStack(item("totality:" + industrial[i][0] + "_battery"));
            UEItem ue = (UEItem) b.getItem();
            long[] actual = {ue.getEnergyCapacity(b), ue.getEnergyMaxInput(b), ue.getEnergyMaxOutput(b)};
            check("gameplay.battery_stats_unchanged." + industrial[i][0], java.util.Arrays.equals(actual, stats[i]),
                    java.util.Arrays.toString(actual));
        }
        expectRarity(pizza, ItemRarity.COMMON);
        expectRarity(soulGem, ItemRarity.COMMON);
        expectRarity(coin, ItemRarity.COMMON);
        expectRarity(credits, ItemRarity.CRUDE);
        expectRarity(tinOre, ItemRarity.COMMON);
        expectRarity(energyCell, ItemRarity.CRUDE);
        List<Identifier> missing = RarityCoverage.missingAuthoredRarity();
        long totalityItems = BuiltInRegistries.ITEM.keySet().stream().filter(k -> k.getNamespace().equals("totality")).count();
        check("rarity.every_totality_item_authored", missing.isEmpty(), totalityItems + " Totality items, missing=" + missing);
        BuiltInRegistries.ITEM.keySet().stream().filter(k -> k.getNamespace().equals("totality")).sorted()
                .forEach(k -> results.add("RARITY " + k + " = " + ItemRarityResolver.resolve(new ItemStack(BuiltInRegistries.ITEM.getValue(k)))
                        .map(r -> r + " (" + r.family() + ")").orElse("NONE")));

        // 3. Classification.
        expectClass(diamond, List.of(pair(ItemType.MATERIAL, ClassificationTypes.GEM)));
        expectClass(ironIngot, List.of(pair(ItemType.MATERIAL, ClassificationTypes.INGOT)));
        expectClass(ironSword, List.of(pair(ItemType.WEAPON, ClassificationTypes.SWORD)));
        expectClass(ironPickaxe, List.of(pair(ItemType.TOOL, ClassificationTypes.PICKAXE)));
        expectClass(bread, List.of(Classification.of(ItemType.FOOD)));
        expectClass(ironChestplate, List.of(pair(ItemType.ARMOR, ClassificationTypes.CHESTPLATE)));
        expectClass(stone, List.of(Classification.of(ItemType.BLOCK)));
        expectClass(oakPlanks, List.of(Classification.of(ItemType.BLOCK)));
        expectClass(wheatSeeds, List.of());
        expectClass(bow, List.of(pair(ItemType.WEAPON, ClassificationTypes.BOW)));
        expectClass(enchantedBook, List.of());
        expectClass(potion, List.of(Classification.of(ItemType.POTION)));
        expectClass(coin, List.of());
        expectClass(tinOre, List.of(Classification.of(ItemType.BLOCK)));
        check("classification.authored_battery", ItemClassificationResolver.resolve(battery).equals(ItemComponents.classificationEntriesOf(battery))
                && !ItemClassificationResolver.resolve(battery).isEmpty(), ItemClassificationResolver.resolve(battery));
        check("classification.authored_shuriken", ItemClassificationResolver.resolve(shuriken).equals(ItemComponents.classificationEntriesOf(shuriken)),
                ItemClassificationResolver.resolve(shuriken));
        ItemStack classifiedDiamond = diamond.copy();
        classifiedDiamond.set(ItemComponents.getClassifications(), ClassificationsComponent.of(ItemType.WEAPON));
        check("classification.stack_authored_wins_no_merge", ItemClassificationResolver.resolve(classifiedDiamond)
                .equals(List.of(Classification.of(ItemType.WEAPON))), ItemClassificationResolver.resolve(classifiedDiamond));
        Function<Identifier, String> typeName = type -> Component.translatableWithFallback(ClassificationTypes.translationKey(type),
                ClassificationTypes.fallbackName(type)).getString();
        for (ItemStack s : ordinary) {
            List<String> lines = TooltipIdentityLines.classificationLines(ItemClassificationResolver.resolve(s), typeName);
            check("classification.no_duplicate_lines." + id(s), lines.size() == new LinkedHashSet<>(lines).size(), lines);
        }
        check("classification.localized_subtype", typeName.apply(ClassificationTypes.SWORD).equals("Sword"), typeName.apply(ClassificationTypes.SWORD));

        // 4. Nothing fabricated: plain items get only header content (no statistic rows). (Seeds are BlockItems, so
        //    the authoritative Block Breaking rows legitimately apply to them — they are not a "plain" item here.)
        ItemStack stick = new ItemStack(Items.STICK);
        for (ItemStack s : List.of(diamond, stick, coin, enchantedBook, ironSword)) {
            List<String> kinds = sectionKinds(mc, s, TooltipDisclosureLevel.DEFAULT, List.of());
            boolean noStats = kinds.stream().noneMatch(k -> k.equals("StatRow") || k.equals("IconStatRow")
                    || k.equals("PropertyBadges") || k.equals("GroupHeading"));
            check("no_fabricated_stats." + id(s), noStats, kinds);
        }

        // 5. Old persisted data still resolves.
        ItemStack legacy = decode(registries, "{\"id\":\"minecraft:diamond\",\"components\":{\"totality:rarity\":\"artifact\","
                + "\"totality:classifications\":[\"weapon\"],\"totality:item_type\":\"tool\"}}");
        check("legacy.artifact_rarity_reads_as_ancient", ItemRarityResolver.resolve(legacy).equals(Optional.of(ItemRarity.ANCIENT)),
                ItemRarityResolver.resolve(legacy));
        check("legacy.flat_classification_list", ItemClassificationResolver.resolve(legacy).equals(List.of(Classification.of(ItemType.WEAPON))),
                ItemClassificationResolver.resolve(legacy));
        ItemStack legacyType = decode(registries, "{\"id\":\"minecraft:stick\",\"components\":{\"totality:item_type\":\"tool\"}}");
        check("legacy.item_type_component", ItemClassificationResolver.resolve(legacyType).equals(List.of(Classification.of(ItemType.TOOL))),
                ItemClassificationResolver.resolve(legacyType));

        // 6. Vanilla lines survive: the enchanted sword's real tooltip lines reach V2 through ExternalContent — except the
        //    enchantment line itself, which the ENCHANTMENTS group now represents (Tooltip V2 Enchantments integration).
        List<Component> vanillaLines = enchanted.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
        TooltipContext ctx = TooltipContext.hover(enchanted, TooltipDisclosureLevel.DEFAULT, vanillaLines, Optional.empty(), mc.player, mc.level);
        List<String> preserved = new ArrayList<>();
        for (TooltipSection section : new ExternalContentContributor().contribute(ctx)) {
            if (section instanceof TooltipSection.ExternalContent ext) ext.lines().forEach(l -> preserved.add(l.getString()));
        }
        String sharpness = Component.translatable("enchantment.minecraft.sharpness").getString();
        String attackDamage = Component.translatable("attribute.name.attack_damage").getString();
        check("vanilla_lines.unrelated_preserved", preserved.stream().anyMatch(l -> l.contains(attackDamage)), preserved);
        List<String> enchantmentRows = new ArrayList<>();
        for (TooltipSection section : new zcylas.totality.client.tooltip.contributor.EnchantmentsContributor().contribute(ctx)) {
            if (section instanceof TooltipSection.StatRow row) enchantmentRows.add(row.label() + " " + row.value());
        }
        check("vanilla_lines.enchantment_represented_once", enchantmentRows.equals(List.of(sharpness + " III"))
                && preserved.stream().noneMatch(l -> l.contains(sharpness)), "rows=" + enchantmentRows + " external=" + preserved);
    }

    private void expectRarity(ItemStack stack, ItemRarity expected) {
        Optional<ItemRarity> actual = ItemRarityResolver.resolve(stack);
        check("rarity.authored." + id(stack), stack.has(ItemComponents.RARITY) && actual.equals(Optional.of(expected)), actual);
    }

    private void expectClass(ItemStack stack, List<Classification> expected) {
        List<Classification> actual = ItemClassificationResolver.resolve(stack);
        check("classification." + id(stack), actual.equals(expected), actual);
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static List<String> sectionKinds(Minecraft mc, ItemStack stack, TooltipDisclosureLevel level, List<Component> lines) {
        TooltipContext ctx = TooltipContext.hover(stack, level, lines, Optional.empty(), mc.player, mc.level);
        List<String> kinds = new ArrayList<>();
        for (TooltipContributor contributor : TooltipContributorRegistry.ordered()) {
            for (TooltipSection section : contributor.contribute(ctx)) kinds.add(section.getClass().getSimpleName());
        }
        return kinds;
    }

    // ── Renderer selection is stable under real SHIFT/CTRL holds ────────────────

    private record ModifierState(String name, boolean shift, boolean ctrl, TooltipDisclosureLevel expected) {}

    private void modifierStability(ClientGameTestContext context) {
        List<ModifierState> states = List.of(
                new ModifierState("DEFAULT", false, false, TooltipDisclosureLevel.DEFAULT),
                new ModifierState("SHIFT", true, false, TooltipDisclosureLevel.DETAILS),
                new ModifierState("CTRL", false, true, TooltipDisclosureLevel.TECHNICAL),
                new ModifierState("SHIFT+CTRL", true, true, TooltipDisclosureLevel.DETAILS_AND_TECHNICAL));
        for (ModifierState state : states) {
            if (state.shift()) context.getInput().holdShift();
            if (state.ctrl()) context.getInput().holdControl();
            context.waitTick();
            context.runOnClient(mc -> {
                TooltipDisclosureLevel resolved = TooltipDisclosureLevel.resolve();
                check("modifiers.disclosure_follows_real_keys." + state.name(), resolved == state.expected(), resolved);
                ItemStack diamond = new ItemStack(Items.DIAMOND);
                ItemStack bundle = decode(mc.level.registryAccess(), FILLED_BUNDLE);
                check("modifiers.diamond_stays_v2." + state.name(), TotalityTooltipRenderer.isEligible(diamond), TooltipRouting.of(diamond));
                check("modifiers.bundle_stays_vanilla." + state.name(), !TotalityTooltipRenderer.isEligible(bundle), TooltipRouting.of(bundle));
                ItemStack shuriken = new ItemStack(item("totality:netherite_shuriken"));
                check("modifiers.totality_item_stays_v2." + state.name(), TotalityTooltipRenderer.isEligible(shuriken), TooltipRouting.of(shuriken));
                ItemStack external = new ItemStack(BuiltInRegistries.ITEM.getValue(GameTestExternalItems.SAMPLE_RELIC_ID));
                check("modifiers.external_stays_original." + state.name(), TooltipRouting.of(external) == TooltipRouting.EXTERNAL_ITEM
                        && !TotalityTooltipRenderer.isEligible(external), TooltipRouting.of(external));
            });
            if (state.shift()) context.getInput().releaseShift();
            if (state.ctrl()) context.getInput().releaseControl();
            context.waitTick();
        }
    }

    // ── Screenshots through the genuine inventory hover path ────────────────────

    private record Hover(String shot, int inventorySlot) {}

    private static final String[] INVENTORY = {
            "minecraft:diamond", "minecraft:iron_sword", "minecraft:iron_pickaxe", "minecraft:bread", "minecraft:stone",
            "totality:netherite_shuriken", "totality:copper_battery", "BUNDLE", "ENCHANTED_SWORD", "totality:tin_ore",
            "totality:copper_coin", "minecraft:iron_chestplate", "totality:petty_soul_gem", "totality:netherite_battery",
            "totality:gold_battery", "totality:diamond_battery", "totality:basic_copper_phone", "gametest_external:sample_relic",
            "totality:netherite_gear"};

    private static final int DIAMOND = 9, PICKAXE = 11, STONE = 13, SHURIKEN = 14, BATTERY = 15, BUNDLE = 16,
            ENCHANTED = 17, SOUL_GEM = 21, NETHERITE_BATTERY = 22, GOLD_BATTERY = 23, DIAMOND_BATTERY = 24, PHONE = 25,
            EXTERNAL = 26, NETHERITE_GEAR = 27;

    private void screenshots(ClientGameTestContext context, TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> {
            var player = server.getPlayerList().getPlayers().get(0);
            HolderLookup.Provider registries = server.registryAccess();
            for (int i = 0; i < INVENTORY.length; i++) {
                String entry = INVENTORY[i];
                ItemStack stack = switch (entry) {
                    case "BUNDLE" -> decode(registries, FILLED_BUNDLE);
                    case "ENCHANTED_SWORD" -> enchantedSword(registries);
                    default -> new ItemStack(item(entry));
                };
                player.getInventory().setItem(9 + i, stack);
            }
        });
        context.waitTicks(5);
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitForScreen(InventoryScreen.class);

        guiScale(context, 2);
        shot(context, "gui2_01_plain_diamond_no_body", DIAMOND);
        shot(context, "gui2_02_iron_pickaxe_mining_first_group", PICKAXE);
        shot(context, "gui2_03_shuriken_multiple_groups", SHURIKEN);
        shot(context, "gui2_04_netherite_battery_MASTERWORK", NETHERITE_BATTERY);
        shot(context, "gui2_05_gold_battery_PROTOTYPE", GOLD_BATTERY);
        shot(context, "gui2_06_diamond_battery_OVERCHARGED", DIAMOND_BATTERY);
        shot(context, "gui2_07_netherite_gear_MASTERWORK", NETHERITE_GEAR);
        shot(context, "gui2_08_copper_phone_lore_footer_no_mechanical_body", PHONE);
        shot(context, "gui2_09_soul_gem_lore_only_body", SOUL_GEM);
        shot(context, "gui2_10_stone_properties_then_lore", STONE);
        shot(context, "gui2_11_bundle_vanilla_tooltip_component", BUNDLE);
        scrollNotCaptured(context, "bundle");
        shot(context, "gui2_12_external_item_original_tooltip", EXTERNAL);
        scrollNotCaptured(context, "external");
        shot(context, "gui2_13_enchanted_sword_vanilla_lines", ENCHANTED);

        context.getInput().holdShift();
        shot(context, "gui2_14_iron_pickaxe_SHIFT", PICKAXE);
        context.getInput().releaseShift();
        context.getInput().holdControl();
        shot(context, "gui2_15_iron_pickaxe_CTRL", PICKAXE);
        shot(context, "gui2_16_plain_diamond_CTRL_same_renderer", DIAMOND);
        shot(context, "gui2_17_external_item_CTRL_still_original", EXTERNAL);
        scrollNotCaptured(context, "external_CTRL");
        context.getInput().holdShift();
        shot(context, "gui2_18_battery_SHIFT_CTRL", BATTERY);
        shot(context, "gui2_19_external_item_SHIFT_CTRL_still_original", EXTERNAL);
        context.getInput().releaseShift();
        context.getInput().releaseControl();

        for (int scale : new int[]{1, 4}) {
            guiScale(context, scale);
            String p = "gui" + scale + "_";
            shot(context, p + "01_plain_diamond_no_body", DIAMOND);
            shot(context, p + "02_iron_pickaxe_mining_first_group", PICKAXE);
            shot(context, p + "03_shuriken_multiple_groups", SHURIKEN);
            shot(context, p + "04_netherite_battery_MASTERWORK", NETHERITE_BATTERY);
            shot(context, p + "05_copper_phone_lore_footer_no_mechanical_body", PHONE);
            shot(context, p + "06_bundle_vanilla_tooltip_component", BUNDLE);
            shot(context, p + "07_external_item_original_tooltip", EXTERNAL);
        }

        context.setScreen(() -> null);
    }

    /** With the item genuinely hovered, a wheel event must not be consumed by Totality's tooltip scrolling. */
    private void scrollNotCaptured(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> check("scroll.not_captured." + name, !TotalityTooltipScrollHandler.onMouseScroll(-1.0),
                "TotalityTooltipScrollHandler.onMouseScroll(-1.0) returned false"));
    }

    private static void guiScale(ClientGameTestContext context, int scale) {
        context.runOnClient(mc -> {
            mc.options.guiScale().set(scale);
            mc.resizeGui();
        });
        context.waitTicks(2);
    }

    /** Reads a protected AbstractContainerScreen field (dev runtime is unobfuscated); test-only reflection. */
    private static Object screenField(Object screen, String name) {
        try {
            Field f = AbstractContainerScreen.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Moves the real cursor over the inventory slot, verifies the screen itself reports it hovered, then captures. */
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
                    id(stack) + " route=" + TooltipRouting.of(stack) + " disclosure=" + TooltipDisclosureLevel.resolve()
                            + " gui=" + mc.options.guiScale().get());
        });
        Path file = context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
        results.add("SHOT " + name + " -> " + file.getFileName());
    }

    private void writeResults(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            try {
                Files.write(mc.gameDirectory.toPath().resolve("totality-pass1-results.txt"), results);
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}

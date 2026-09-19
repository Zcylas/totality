package zcylas.totality.init;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.Totality;
import zcylas.totality.init.blocks.AlchemyBlocks;
import zcylas.totality.init.blocks.EnergyBlocks;
import zcylas.totality.init.blocks.NaturalBlocks;
import zcylas.totality.init.blocks.OreBlocks;
import zcylas.totality.init.blocks.RitualBlocks;
import zcylas.totality.init.blocks.WhitestoneBlocks;
import zcylas.totality.init.items.BasicWeaponItems;
import zcylas.totality.init.items.BleachItems;
import zcylas.totality.init.items.CurrencyItems;
import zcylas.totality.init.items.DndPotionItems;
import zcylas.totality.init.items.EnergyItems;
import zcylas.totality.init.items.FoodItems;
import zcylas.totality.init.items.FuelItems;
import zcylas.totality.init.items.IngredientItems;
import zcylas.totality.init.items.MagicItems;
import zcylas.totality.init.items.PotionItems;
import zcylas.totality.init.items.ReligiousItems;
import zcylas.totality.init.items.RitualItems;
import zcylas.totality.init.items.RuneItems;
import zcylas.totality.init.items.SKIngredientItems;
import zcylas.totality.init.items.SpellComponentItems;
import zcylas.totality.init.items.ToolItems;

/**
 * Totality's creative-inventory tabs. Four broad, player-understandable groups chosen from the
 * actual current content distribution (Totality had zero custom creative tabs before this pass —
 * every item/block was only obtainable via commands): raw/building materials, wearable and
 * carried equipment, the copper/energy machine chain, and the magic/alchemy/ritual system (by far
 * the largest content family, so it gets its own tab rather than being folded into "equipment").
 *
 * <p>Purely a creative-browsing/testing aid — registers no gameplay behavior, changes no recipes,
 * loot, or item stats. Icons are existing Totality items chosen for recognizability, not new
 * assets; see this class's inline comments for why each was picked.
 *
 * <p>{@code RitualBlocks.CHALK} and {@code AlchemyBlocks.TRUE_WHEAT_CROP} are intentionally
 * excluded — both are registered with {@code withItem=false} (no BlockItem exists for either,
 * matching vanilla's own crop-block convention), so there is no {@code ItemStack} to add.
 */
public class ModGroups {

    private static ResourceKey<CreativeModeTab> key(String path) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(Totality.MOD_ID, path));
    }

    private static final ResourceKey<CreativeModeTab> MATERIALS_KEY = key("materials");
    private static final ResourceKey<CreativeModeTab> EQUIPMENT_KEY = key("equipment");
    private static final ResourceKey<CreativeModeTab> MACHINES_KEY = key("machines");
    private static final ResourceKey<CreativeModeTab> MAGIC_KEY = key("magic");
    private static final ResourceKey<CreativeModeTab> FOOD_KEY = key("food");

    public static CreativeModeTab MATERIALS;
    public static CreativeModeTab EQUIPMENT;
    public static CreativeModeTab MACHINES;
    public static CreativeModeTab MAGIC;
    public static CreativeModeTab FOOD;

    public static void register() {
        // Icon: a distinctive raw gem reads instantly as "materials," and it's a plain Item
        // (not a BlockItem), so referencing it here can't be affected by block/model timing.
        MATERIALS = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MATERIALS_KEY,
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.totality.materials"))
                        .icon(() -> new ItemStack(IngredientItems.ROUGH_RUBY))
                        .displayItems((parameters, output) -> {
                            // Ores
                            output.accept(OreBlocks.TIN_ORE);
                            output.accept(OreBlocks.DEEPSLATE_TIN_ORE);
                            output.accept(OreBlocks.GRAPHITE_ORE);
                            output.accept(OreBlocks.DEEPSLATE_GRAPHITE_ORE);
                            output.accept(OreBlocks.LEAD_ORE);
                            output.accept(OreBlocks.DEEPSLATE_LEAD_ORE);
                            output.accept(OreBlocks.SILVER_ORE);
                            output.accept(OreBlocks.DEEPSLATE_SILVER_ORE);
                            output.accept(OreBlocks.VIBRANIUM_ORE);
                            output.accept(OreBlocks.DEEPSLATE_VIBRANIUM_ORE);
                            output.accept(OreBlocks.RUBY_ORE);
                            output.accept(OreBlocks.DEEPSLATE_RUBY_ORE);
                            // Raw materials / gears
                            output.accept(IngredientItems.RAW_TIN);
                            output.accept(IngredientItems.GRAPHITE);
                            output.accept(IngredientItems.ROUGH_RUBY);
                            output.accept(IngredientItems.COPPER_GEAR);
                            output.accept(IngredientItems.IRON_GEAR);
                            output.accept(IngredientItems.GOLD_GEAR);
                            output.accept(IngredientItems.DIAMOND_GEAR);
                            output.accept(IngredientItems.NETHERITE_GEAR);
                            // Building materials
                            output.accept(NaturalBlocks.LIMESTONE);
                            output.accept(IngredientItems.LIMESTONE_CHUNK);
                            output.accept(WhitestoneBlocks.WHITESTONE);
                            output.accept(WhitestoneBlocks.FLECKED_WHITESTONE);
                            output.accept(WhitestoneBlocks.POLISHED_WHITESTONE);
                            output.accept(WhitestoneBlocks.POLISHED_WHITESTONE_BRICKS);
                            output.accept(IngredientItems.WHITESTONE_CHUNK);
                            output.accept(IngredientItems.RESIDUUM_FLECKED_CHUNK);
                            // Fuel
                            output.accept(FuelItems.TINY_COAL);
                            // Farming / cooking ingredients
                            output.accept(AlchemyBlocks.BLUE_MOUNTAIN_FLOWER_BUSH);
                            output.accept(AlchemyBlocks.PURPLE_MOUNTAIN_FLOWER_BUSH);
                            output.accept(AlchemyBlocks.RED_MOUNTAIN_FLOWER_BUSH);
                            output.accept(SKIngredientItems.BLUE_MOUNTAIN_FLOWER);
                            output.accept(SKIngredientItems.PURPLE_MOUNTAIN_FLOWER);
                            output.accept(SKIngredientItems.RED_MOUNTAIN_FLOWER);
                            output.accept(IngredientItems.TRUE_WHEAT_SEEDS);
                            output.accept(SKIngredientItems.TRUE_WHEAT);
                            output.accept(SKIngredientItems.SALMON_ROE);
                            output.accept(SKIngredientItems.ROCK_WARBLER_EGG);
                            output.accept(SKIngredientItems.GARLIC);
                        })
                        .build());

        // Icon: Zanpakutō is Totality's signature weapon, immediately recognizable as "equipment."
        EQUIPMENT = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, EQUIPMENT_KEY,
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.totality.equipment"))
                        .icon(() -> new ItemStack(BleachItems.ZANPAKUTO))
                        .displayItems((parameters, output) -> {
                            // Weapons
                            output.accept(BasicWeaponItems.COPPER_SHURIKEN);
                            output.accept(BasicWeaponItems.IRON_SHURIKEN);
                            output.accept(BasicWeaponItems.GOLD_SHURIKEN);
                            output.accept(BasicWeaponItems.DIAMOND_SHURIKEN);
                            output.accept(BasicWeaponItems.NETHERITE_SHURIKEN);
                            output.accept(BasicWeaponItems.IRON_SWORD);
                            output.accept(BasicWeaponItems.STEEL_SWORD);
                            output.accept(BleachItems.ZANPAKUTO);
                            // Armor
                            output.accept(BleachItems.SHINIGAMI_ROBE);
                            // Tools
                            output.accept(ToolItems.WRENCH);
                            // Personal gadgets
                            output.accept(EnergyItems.UMBRA_VISOR);
                            output.accept(EnergyItems.BASIC_COPPER_PHONE);
                            // Currency
                            output.accept(CurrencyItems.COPPER_COIN);
                            output.accept(CurrencyItems.SILVER_COIN);
                            output.accept(CurrencyItems.GOLD_COIN);
                            output.accept(CurrencyItems.PLATINUM_COIN);
                            output.accept(CurrencyItems.CREDITS);
                        })
                        .build());

        // Icon: the Generator is the root power source of the whole energy chain.
        MACHINES = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MACHINES_KEY,
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.totality.machines"))
                        .icon(() -> new ItemStack(EnergyBlocks.GENERATOR))
                        .displayItems((parameters, output) -> {
                            output.accept(EnergyBlocks.GENERATOR);
                            output.accept(EnergyBlocks.ELECTRIC_FURNACE);
                            output.accept(EnergyBlocks.COPPER_ENERGY_CELL);
                            output.accept(EnergyBlocks.COPPER_CABLE);
                            output.accept(ModBlocks.COPPER_TANK);
                            output.accept(EnergyItems.COPPER_BATTERY);
                            output.accept(EnergyItems.IRON_BATTERY);
                            output.accept(EnergyItems.GOLD_BATTERY);
                            output.accept(EnergyItems.DIAMOND_BATTERY);
                            output.accept(EnergyItems.NETHERITE_BATTERY);
                        })
                        .build());

        // Icon: the Archmage Grimoire is the mod's top-tier spellbook, an immediately readable
        // "this is the magic tab" icon.
        MAGIC = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MAGIC_KEY,
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.totality.magic"))
                        .icon(() -> new ItemStack(MagicItems.ARCHMAGE_GRIMOIRE))
                        .displayItems((parameters, output) -> {
                            // Grimoires / magic trinkets
                            output.accept(MagicItems.NOVICE_GRIMOIRE);
                            output.accept(MagicItems.APPRENTICE_GRIMOIRE);
                            output.accept(MagicItems.ARCHMAGE_GRIMOIRE);
                            output.accept(MagicItems.RING_OF_PROTECTION);
                            output.accept(MagicItems.ARCANE_ORB);
                            output.accept(MagicItems.BARD_GUITAR);
                            // Soul Gems
                            output.accept(MagicItems.PETTY_SOUL_GEM);
                            output.accept(MagicItems.COMMON_SOUL_GEM);
                            // Runes — Blanks
                            output.accept(RuneItems.BLANK_FORM);
                            output.accept(RuneItems.BLANK_EFFECT);
                            output.accept(RuneItems.BLANK_AUGMENT);
                            // Runes — Forms
                            output.accept(RuneItems.RUNE_TOUCH);
                            output.accept(RuneItems.RUNE_PROJECTILE);
                            output.accept(RuneItems.RUNE_SELF);
                            // Runes — Effects
                            output.accept(RuneItems.RUNE_BREAK);
                            output.accept(RuneItems.RUNE_PICKUP);
                            output.accept(RuneItems.RUNE_LAUNCH);
                            output.accept(RuneItems.RUNE_IGNITE);
                            output.accept(RuneItems.RUNE_EXPLOSION);
                            output.accept(RuneItems.RUNE_GLIDE);
                            output.accept(RuneItems.RUNE_SMELT);
                            output.accept(RuneItems.RUNE_ORBIT);
                            output.accept(RuneItems.RUNE_HARM);
                            output.accept(RuneItems.RUNE_HEAL);
                            output.accept(RuneItems.RUNE_HEX);
                            output.accept(RuneItems.RUNE_LIGHTNING);
                            output.accept(RuneItems.RUNE_CHAINING);
                            output.accept(RuneItems.RUNE_GROW);
                            output.accept(RuneItems.RUNE_LINGER);
                            output.accept(RuneItems.RUNE_HARVEST);
                            output.accept(RuneItems.RUNE_BURST);
                            output.accept(RuneItems.RUNE_SUMMON_UNDEAD);
                            // Runes — Augments
                            output.accept(RuneItems.RUNE_AMPLIFY);
                            output.accept(RuneItems.RUNE_AOE);
                            output.accept(RuneItems.RUNE_REDUCE_TIME);
                            output.accept(RuneItems.RUNE_EXTEND_TIME);
                            output.accept(RuneItems.RUNE_DAMPEN);
                            output.accept(RuneItems.RUNE_SENSITIVE);
                            output.accept(RuneItems.RUNE_PIERCE);
                            output.accept(RuneItems.RUNE_FORTUNE);
                            output.accept(RuneItems.RUNE_RANDOMIZE);
                            output.accept(RuneItems.RUNE_EXTRACT);
                            output.accept(RuneItems.RUNE_ACCELERATE);
                            output.accept(RuneItems.RUNE_DECELERATE);
                            output.accept(RuneItems.RUNE_SPLIT);
                            // Spell material components
                            output.accept(SpellComponentItems.BAT_GUANO);
                            output.accept(SpellComponentItems.SULPHUR_DUST);
                            output.accept(SpellComponentItems.FUR);
                            output.accept(SpellComponentItems.GLASS_ROD);
                            output.accept(SpellComponentItems.COMPONENT_POUCH);
                            output.accept(SpellComponentItems.ARCANE_FOCUS);
                            // Ritual items / blocks
                            output.accept(ReligiousItems.BLESSED_INCENSE);
                            output.accept(RitualItems.INCENSE);
                            output.accept(RitualItems.WHITE_CHALK);
                            output.accept(RitualItems.GOLD_CHALK);
                            output.accept(RitualItems.BLUE_CHALK);
                            output.accept(RitualItems.PURPLE_CHALK);
                            output.accept(RitualItems.RED_CHALK);
                            output.accept(RitualItems.RESIDUUM_CHALK);
                            output.accept(RitualBlocks.RITUAL_ALTAR);
                            output.accept(RitualBlocks.RITUAL_DAIS);
                            // Alchemy
                            output.accept(AlchemyBlocks.APOTHECARY_TABLE);
                            output.accept(PotionItems.BREWED_POTION);
                            output.accept(PotionItems.POTION_OF_MINOR_HEALING);
                            output.accept(PotionItems.POTION_OF_HEALING);
                            output.accept(PotionItems.POTION_OF_PLENTIFUL_HEALING);
                            output.accept(PotionItems.POTION_OF_VIGOROUS_HEALING);
                            output.accept(PotionItems.POTION_OF_EXTREME_HEALING);
                            output.accept(PotionItems.POTION_OF_ULTIMATE_HEALING);
                            output.accept(PotionItems.POTION_OF_MINOR_MANA);
                            output.accept(PotionItems.POTION_OF_MANA);
                            output.accept(PotionItems.POTION_OF_PLENTIFUL_MANA);
                            output.accept(PotionItems.POTION_OF_VIGOROUS_MANA);
                            output.accept(PotionItems.POTION_OF_EXTREME_MANA);
                            output.accept(PotionItems.POTION_OF_ULTIMATE_MANA);
                            output.accept(PotionItems.POTION_OF_MINOR_STAMINA);
                            output.accept(PotionItems.POTION_OF_STAMINA);
                            output.accept(PotionItems.POTION_OF_PLENTIFUL_STAMINA);
                            output.accept(PotionItems.POTION_OF_VIGOROUS_STAMINA);
                            output.accept(PotionItems.POTION_OF_EXTREME_STAMINA);
                            output.accept(PotionItems.POTION_OF_ULTIMATE_STAMINA);
                            output.accept(PotionItems.POTION_OF_WATERBREATHING);
                            output.accept(PotionItems.DRAUGHT_OF_WATERBREATHING);
                            output.accept(PotionItems.PHILTER_OF_WATERBREATHING);
                            output.accept(PotionItems.ELIXIR_OF_WATERBREATHING);
                            output.accept(PotionItems.POTION_OF_REGENERATION);
                            output.accept(PotionItems.DRAUGHT_OF_REGENERATION);
                            output.accept(PotionItems.SOLUTION_OF_REGENERATION);
                            output.accept(PotionItems.PHILTER_OF_REGENERATION);
                            output.accept(PotionItems.ELIXIR_OF_REGENERATION);
                            output.accept(PotionItems.POTION_OF_LASTING_POTENCY);
                            output.accept(PotionItems.DRAUGHT_OF_LASTING_POTENCY);
                            output.accept(PotionItems.SOLUTION_OF_LASTING_POTENCY);
                            output.accept(PotionItems.PHILTER_OF_LASTING_POTENCY);
                            output.accept(PotionItems.ELIXIR_OF_LASTING_POTENCY);
                            output.accept(PotionItems.POTION_OF_HEALTH);
                            output.accept(PotionItems.DRAUGHT_OF_HEALTH);
                            output.accept(PotionItems.SOLUTION_OF_HEALTH);
                            output.accept(PotionItems.PHILTER_OF_HEALTH);
                            output.accept(PotionItems.ELIXIR_OF_HEALTH);
                            output.accept(PotionItems.POTION_OF_EXTRA_MANA);
                            output.accept(PotionItems.DRAUGHT_OF_EXTRA_MANA);
                            output.accept(PotionItems.SOLUTION_OF_EXTRA_MANA);
                            output.accept(PotionItems.PHILTER_OF_EXTRA_MANA);
                            output.accept(PotionItems.ELIXIR_OF_EXTRA_MANA);
                            output.accept(DndPotionItems.POTION_OF_HEALING);
                        })
                        .build());

        // Icon: the whole Pizza is the mod's first real food item and instantly reads as "food."
        // One deliberately unsplit Food tab (2026-09-17) — see FoodItems' own class Javadoc; a
        // future Ingredients/Meals/Snacks/Drinks split is deferred until the catalog justifies it.
        FOOD = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, FOOD_KEY,
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.totality.food"))
                        .icon(() -> new ItemStack(FoodItems.PIZZA_MARGHERITA))
                        .displayItems((parameters, output) -> {
                            output.accept(FoodItems.PIZZA_MARGHERITA);
                            output.accept(FoodItems.PIZZA_MARGHERITA_SLICE);
                        })
                        .build());
    }

    private ModGroups() {}
}

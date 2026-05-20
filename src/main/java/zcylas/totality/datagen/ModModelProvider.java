package zcylas.totality.datagen;

import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.item.properties.conditional.HasComponent;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jspecify.annotations.NonNull;
import zcylas.totality.api.industrial.energy.UEComponents;
import zcylas.totality.api.ritual.ChalkColor;
import zcylas.totality.api.ritual.ChalkSigil;
import zcylas.totality.block.energy.CableBlock;
import zcylas.totality.block.energy.ElectricFurnaceBlock;
import zcylas.totality.block.energy.EnergyCellBlock;
import zcylas.totality.block.fluid.FluidTankBlock;
import zcylas.totality.block.ritual.ChalkBlock;
import zcylas.totality.client.color.PotionTintSource;
import zcylas.totality.client.renderer.fluid.FluidTankSpecialRenderer;
import zcylas.totality.init.ModBlocks;
import zcylas.totality.init.blocks.*;
import zcylas.totality.init.items.*;

import net.minecraft.client.data.models.blockstates.ConditionBuilder;

public class ModModelProvider extends FabricModelProvider {
    public ModModelProvider(FabricPackOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(@NonNull BlockModelGenerators generators) {
        registerFluidTank(generators, ModBlocks.COPPER_TANK);
        // Energy cell — uses furnace model as placeholder, no facing needed
        generators.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(EnergyBlocks.COPPER_ENERGY_CELL)
                        .with(PropertyDispatch.initial(EnergyCellBlock.FACING)
                                .select(Direction.NORTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/copper_energy_cell")))
                                .select(Direction.EAST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/copper_energy_cell")).with(BlockModelGenerators.Y_ROT_90))
                                .select(Direction.SOUTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/copper_energy_cell")).with(BlockModelGenerators.Y_ROT_180))
                                .select(Direction.WEST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/copper_energy_cell")).with(BlockModelGenerators.Y_ROT_270))
                        )
        );
        generators.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(EnergyBlocks.GENERATOR)
                        .with(PropertyDispatch.initial(BlockStateProperties.LIT, BlockStateProperties.HORIZONTAL_FACING)
                                .select(false, Direction.NORTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/generator")))
                                .select(false, Direction.EAST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/generator")).with(BlockModelGenerators.Y_ROT_90))
                                .select(false, Direction.SOUTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/generator")).with(BlockModelGenerators.Y_ROT_180))
                                .select(false, Direction.WEST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/generator")).with(BlockModelGenerators.Y_ROT_270))
                                .select(true, Direction.NORTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/generator_active")))
                                .select(true, Direction.EAST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/generator_active")).with(BlockModelGenerators.Y_ROT_90))
                                .select(true, Direction.SOUTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/generator_active")).with(BlockModelGenerators.Y_ROT_180))
                                .select(true, Direction.WEST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/generator_active")).with(BlockModelGenerators.Y_ROT_270))
                        )
        );
        generators.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(EnergyBlocks.ELECTRIC_FURNACE)
                        .with(PropertyDispatch.initial(ElectricFurnaceBlock.ACTIVE, ElectricFurnaceBlock.FACING)
                                .select(false, Direction.NORTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/electric_furnace")))
                                .select(false, Direction.EAST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/electric_furnace")).with(BlockModelGenerators.Y_ROT_90))
                                .select(false, Direction.SOUTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/electric_furnace")).with(BlockModelGenerators.Y_ROT_180))
                                .select(false, Direction.WEST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/electric_furnace")).with(BlockModelGenerators.Y_ROT_270))
                                .select(true, Direction.NORTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/electric_furnace_active")))
                                .select(true, Direction.EAST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/electric_furnace_active")).with(BlockModelGenerators.Y_ROT_90))
                                .select(true, Direction.SOUTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/electric_furnace_active")).with(BlockModelGenerators.Y_ROT_180))
                                .select(true, Direction.WEST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/electric_furnace_active")).with(BlockModelGenerators.Y_ROT_270))
                        )
        );
        // Ritual Altar
        generators.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(RitualBlocks.RITUAL_ALTAR)
                        .with(PropertyDispatch.initial(BlockStateProperties.LIT, BlockStateProperties.HORIZONTAL_FACING)
                                .select(false, Direction.NORTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_altar")))
                                .select(false, Direction.EAST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_altar")).with(BlockModelGenerators.Y_ROT_90))
                                .select(false, Direction.SOUTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_altar")).with(BlockModelGenerators.Y_ROT_180))
                                .select(false, Direction.WEST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_altar")).with(BlockModelGenerators.Y_ROT_270))
                                .select(true, Direction.NORTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_altar_active")))
                                .select(true, Direction.EAST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_altar_active")).with(BlockModelGenerators.Y_ROT_90))
                                .select(true, Direction.SOUTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_altar_active")).with(BlockModelGenerators.Y_ROT_180))
                                .select(true, Direction.WEST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_altar_active")).with(BlockModelGenerators.Y_ROT_270))
                        )
        );
        generators.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(RitualBlocks.RITUAL_DAIS)
                        .with(PropertyDispatch.initial(BlockStateProperties.LIT, BlockStateProperties.HORIZONTAL_FACING)
                                .select(false, Direction.NORTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_dais")))
                                .select(false, Direction.EAST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_dais")).with(BlockModelGenerators.Y_ROT_90))
                                .select(false, Direction.SOUTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_dais")).with(BlockModelGenerators.Y_ROT_180))
                                .select(false, Direction.WEST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_dais")).with(BlockModelGenerators.Y_ROT_270))
                                .select(true, Direction.NORTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_dais_active")))
                                .select(true, Direction.EAST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_dais_active")).with(BlockModelGenerators.Y_ROT_90))
                                .select(true, Direction.SOUTH, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_dais_active")).with(BlockModelGenerators.Y_ROT_180))
                                .select(true, Direction.WEST, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/ritual_dais_active")).with(BlockModelGenerators.Y_ROT_270))
                        )
        );

        Identifier cableCore = Identifier.fromNamespaceAndPath("totality", "block/copper_cable_core");
        Identifier cableSide = Identifier.fromNamespaceAndPath("totality", "block/copper_cable_side");

        generators.blockStateOutput.accept(
                MultiPartGenerator.multiPart(EnergyBlocks.COPPER_CABLE)
                        .with(BlockModelGenerators.plainVariant(cableCore))
                        .with(new ConditionBuilder().term(CableBlock.NORTH, true).build(),
                                BlockModelGenerators.plainVariant(cableSide))
                        .with(new ConditionBuilder().term(CableBlock.EAST, true).build(),
                                BlockModelGenerators.plainVariant(cableSide).with(BlockModelGenerators.Y_ROT_90))
                        .with(new ConditionBuilder().term(CableBlock.SOUTH, true).build(),
                                BlockModelGenerators.plainVariant(cableSide).with(BlockModelGenerators.Y_ROT_180))
                        .with(new ConditionBuilder().term(CableBlock.WEST, true).build(),
                                BlockModelGenerators.plainVariant(cableSide).with(BlockModelGenerators.Y_ROT_270))
                        .with(new ConditionBuilder().term(CableBlock.UP, true).build(),
                                BlockModelGenerators.plainVariant(cableSide).with(BlockModelGenerators.X_ROT_270))
                        .with(new ConditionBuilder().term(CableBlock.DOWN, true).build(),
                                BlockModelGenerators.plainVariant(cableSide).with(BlockModelGenerators.X_ROT_90))
        );
        //Whitestone
        generators.createTrivialCube(WhitestoneBlocks.WHITESTONE);
        generators.createTrivialCube(WhitestoneBlocks.FLECKED_WHITESTONE);
        generators.createTrivialCube(WhitestoneBlocks.POLISHED_WHITESTONE);
        generators.createTrivialCube(WhitestoneBlocks.POLISHED_WHITESTONE_BRICKS);
        //Natural Blocks
        generators.createTrivialCube(NaturalBlocks.LIMESTONE);
        //Ores
        generators.createTrivialCube(OreBlocks.TIN_ORE);
        generators.createTrivialCube(OreBlocks.DEEPSLATE_TIN_ORE);
        generators.createTrivialCube(OreBlocks.GRAPHITE_ORE);
        generators.createTrivialCube(OreBlocks.DEEPSLATE_GRAPHITE_ORE);
        generators.createTrivialCube(OreBlocks.LEAD_ORE);
        generators.createTrivialCube(OreBlocks.DEEPSLATE_LEAD_ORE);
        generators.createTrivialCube(OreBlocks.SILVER_ORE);
        generators.createTrivialCube(OreBlocks.DEEPSLATE_SILVER_ORE);
        generators.createTrivialCube(OreBlocks.VIBRANIUM_ORE);
        generators.createTrivialCube(OreBlocks.DEEPSLATE_VIBRANIUM_ORE);
        generators.createTrivialCube(OreBlocks.RUBY_ORE);
        generators.createTrivialCube(OreBlocks.DEEPSLATE_RUBY_ORE);
        //Alchemy Ingredients
            //Flowers
        generators.createCrossBlockWithDefaultItem(
                AlchemyBlocks.BLUE_MOUNTAIN_FLOWER,
                BlockModelGenerators.PlantType.NOT_TINTED
        );
        generators.createCrossBlockWithDefaultItem(
                AlchemyBlocks.PURPLE_MOUNTAIN_FLOWER,
                BlockModelGenerators.PlantType.NOT_TINTED
        );
        generators.createCrossBlockWithDefaultItem(
                AlchemyBlocks.RED_MOUNTAIN_FLOWER,
                BlockModelGenerators.PlantType.NOT_TINTED
        );
            //Crops
        generators.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(AlchemyBlocks.TRUE_WHEAT_CROP)
                        .with(PropertyDispatch.initial(CropBlock.AGE)
                                .select(0, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/true_wheat_stage_0")))
                                .select(1, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/true_wheat_stage_1")))
                                .select(2, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/true_wheat_stage_2")))
                                .select(3, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/true_wheat_stage_3")))
                                .select(4, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/true_wheat_stage_4")))
                                .select(5, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/true_wheat_stage_5")))
                                .select(6, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/true_wheat_stage_6")))
                                .select(7, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/true_wheat_stage_7")))
                        )

        );
        generators.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(RitualBlocks.CHALK)
                        .with(PropertyDispatch.initial(ChalkBlock.COLOR, ChalkBlock.SIGIL)
                                .select(ChalkColor.WHITE, ChalkSigil.FOCUS, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/focus_sigil")))
                                .select(ChalkColor.GOLD, ChalkSigil.FOCUS, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/focus_sigil")))
                                .select(ChalkColor.BLUE, ChalkSigil.FOCUS, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/focus_sigil")))
                                .select(ChalkColor.PURPLE, ChalkSigil.FOCUS, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/focus_sigil")))
                                .select(ChalkColor.RED, ChalkSigil.FOCUS, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/focus_sigil")))
                                .select(ChalkColor.RESIDUUM, ChalkSigil.FOCUS, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/focus_sigil")))
                                .select(ChalkColor.WHITE, ChalkSigil.TRANSFORMATION, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/transformation_sigil")))
                                .select(ChalkColor.GOLD, ChalkSigil.TRANSFORMATION, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/transformation_sigil")))
                                .select(ChalkColor.BLUE, ChalkSigil.TRANSFORMATION, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/transformation_sigil")))
                                .select(ChalkColor.PURPLE, ChalkSigil.TRANSFORMATION, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/transformation_sigil")))
                                .select(ChalkColor.RED, ChalkSigil.TRANSFORMATION, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/transformation_sigil")))
                                .select(ChalkColor.RESIDUUM, ChalkSigil.TRANSFORMATION, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/transformation_sigil")))
                                .select(ChalkColor.WHITE, ChalkSigil.INQUISITION, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/inquisition_sigil")))
                                .select(ChalkColor.GOLD, ChalkSigil.INQUISITION, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/inquisition_sigil")))
                                .select(ChalkColor.BLUE, ChalkSigil.INQUISITION, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/inquisition_sigil")))
                                .select(ChalkColor.PURPLE, ChalkSigil.INQUISITION, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/inquisition_sigil")))
                                .select(ChalkColor.RED, ChalkSigil.INQUISITION, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/inquisition_sigil")))
                                .select(ChalkColor.RESIDUUM, ChalkSigil.INQUISITION, BlockModelGenerators.plainVariant(
                                        Identifier.fromNamespaceAndPath("totality", "block/sigils/inquisition_sigil")))

                        )
        );
        //Functional Blocks
            //Skill Blocks
        generators.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(
                        AlchemyBlocks.APOTHECARY_TABLE,
                        BlockModelGenerators.plainVariant(Identifier.fromNamespaceAndPath("totality", "block/apothecary_table"))
                )
        );

    }

    @Override
    public void generateItemModels(@NonNull ItemModelGenerators generators) {
        Identifier modelLocation = Identifier.fromNamespaceAndPath(
                "totality", "block/copper_tank");
        generators.itemModelOutput.accept(
                EnergyBlocks.GENERATOR.asItem(),
                ItemModelUtils.plainModel(
                        Identifier.fromNamespaceAndPath("totality", "block/generator")));
        generators.itemModelOutput.accept(
                AlchemyBlocks.APOTHECARY_TABLE.asItem(),
                ItemModelUtils.plainModel(
                        Identifier.fromNamespaceAndPath("totality", "item/apothecary_table")));
        generators.itemModelOutput.accept(
                EnergyBlocks.ELECTRIC_FURNACE.asItem(),
                ItemModelUtils.plainModel(
                        Identifier.fromNamespaceAndPath("totality", "block/electric_furnace")));
        generators.itemModelOutput.accept(
                ModBlocks.COPPER_TANK.asItem(),
                ItemModelUtils.specialModel(
                        modelLocation,
                        new FluidTankSpecialRenderer.Unbaked()));
        generators.generateFlatItem(
                EnergyBlocks.COPPER_CABLE.asItem(), ModelTemplates.FLAT_ITEM
        );
        // Other tanks use same model for now

        generators.itemModelOutput.accept(
                EnergyItems.COPPER_BATTERY,
                ItemModelUtils.conditional(
                        new HasComponent(UEComponents.BATTERY_ACTIVE, false),
                        ItemModelUtils.plainModel(
                                Identifier.fromNamespaceAndPath("totality", "item/copper_battery_active")),
                        ItemModelUtils.plainModel(
                                Identifier.fromNamespaceAndPath("totality", "item/copper_battery"))
                )
        );
        generators.itemModelOutput.accept(
                EnergyItems.IRON_BATTERY,
                ItemModelUtils.conditional(
                        new HasComponent(UEComponents.BATTERY_ACTIVE, false),
                        ItemModelUtils.plainModel(
                                Identifier.fromNamespaceAndPath("totality", "item/iron_battery_active")),
                        ItemModelUtils.plainModel(
                                Identifier.fromNamespaceAndPath("totality", "item/iron_battery"))
                )
        );
        generators.itemModelOutput.accept(
                EnergyItems.GOLD_BATTERY,
                ItemModelUtils.conditional(
                        new HasComponent(UEComponents.BATTERY_ACTIVE, false),
                        ItemModelUtils.plainModel(
                                Identifier.fromNamespaceAndPath("totality", "item/gold_battery_active")),
                        ItemModelUtils.plainModel(
                                Identifier.fromNamespaceAndPath("totality", "item/gold_battery"))
                )
        );
        generators.itemModelOutput.accept(
                EnergyItems.DIAMOND_BATTERY,
                ItemModelUtils.conditional(
                        new HasComponent(UEComponents.BATTERY_ACTIVE, false),
                        ItemModelUtils.plainModel(
                                Identifier.fromNamespaceAndPath("totality", "item/diamond_battery_active")),
                        ItemModelUtils.plainModel(
                                Identifier.fromNamespaceAndPath("totality", "item/diamond_battery"))
                )
        );
        generators.itemModelOutput.accept(
                EnergyItems.NETHERITE_BATTERY,
                ItemModelUtils.conditional(
                        new HasComponent(UEComponents.BATTERY_ACTIVE, false),
                        ItemModelUtils.plainModel(
                                Identifier.fromNamespaceAndPath("totality", "item/netherite_battery_active")),
                        ItemModelUtils.plainModel(
                                Identifier.fromNamespaceAndPath("totality", "item/netherite_battery"))
                )
        );
        generators.itemModelOutput.accept(
                EnergyItems.UMBRA_VISOR, ItemModelUtils.plainModel(
                        Identifier.fromNamespaceAndPath("totality", "item/umbra_visor")
                )
        );
        generators.itemModelOutput.accept(
                RitualBlocks.RITUAL_ALTAR.asItem(),
                ItemModelUtils.plainModel(
                        Identifier.fromNamespaceAndPath("totality", "block/ritual_altar"))
        );
        generators.itemModelOutput.accept(
                RitualBlocks.RITUAL_DAIS.asItem(),
                ItemModelUtils.plainModel(
                        Identifier.fromNamespaceAndPath("totality", "block/ritual_dais"))
        );
        generators.generateFlatItem(MagicItems.NOVICE_GRIMOIRE, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(MagicItems.APPRENTICE_GRIMOIRE, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(MagicItems.ARCHMAGE_GRIMOIRE, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ToolItems.WRENCH, ModelTemplates.FLAT_ITEM);
        //Basic Weapons
            //Shuriken
        generators.generateFlatItem(BasicWeaponItems.COPPER_SHURIKEN, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(BasicWeaponItems.IRON_SHURIKEN, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(BasicWeaponItems.GOLD_SHURIKEN, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(BasicWeaponItems.DIAMOND_SHURIKEN, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(BasicWeaponItems.NETHERITE_SHURIKEN, ModelTemplates.FLAT_ITEM);
        //Ingredients
            //Gears
        generators.generateFlatItem(IngredientItems.COPPER_GEAR, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(IngredientItems.IRON_GEAR, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(IngredientItems.GOLD_GEAR, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(IngredientItems.DIAMOND_GEAR, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(IngredientItems.NETHERITE_GEAR, ModelTemplates.FLAT_ITEM);
            //Raw Ores
        generators.generateFlatItem(IngredientItems.RAW_TIN, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(IngredientItems.GRAPHITE, ModelTemplates.FLAT_ITEM);
            //Whitestone
        generators.generateFlatItem(IngredientItems.WHITESTONE_CHUNK, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(IngredientItems.RESIDUUM_FLECKED_CHUNK, ModelTemplates.FLAT_ITEM);
            //Limestone
        generators.generateFlatItem(IngredientItems.LIMESTONE_CHUNK, ModelTemplates.FLAT_ITEM);
            //Rough Gemstones
        generators.generateFlatItem(IngredientItems.ROUGH_RUBY, ModelTemplates.FLAT_ITEM);
            //Seeds
        generators.generateFlatItem(IngredientItems.TRUE_WHEAT_SEEDS, ModelTemplates.FLAT_ITEM);
        //Tools
            //Coins
        generators.generateFlatItem(CurrencyItems.COPPER_COIN, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(CurrencyItems.SILVER_COIN, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(CurrencyItems.GOLD_COIN, ModelTemplates.FLAT_ITEM);
        //Alchemy Ingredients
        generators.generateFlatItem(SKIngredientItems.SALMON_ROE, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(SKIngredientItems.ROCK_WARBLER_EGG, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(SKIngredientItems.TRUE_WHEAT, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(SKIngredientItems.GARLIC, ModelTemplates.FLAT_ITEM);
        //Fuels
        generators.generateFlatItem(FuelItems.TINY_COAL, ModelTemplates.FLAT_ITEM);
        //Ritual Items
        generators.generateFlatItem(RitualItems.WHITE_CHALK, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(RitualItems.GOLD_CHALK, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(RitualItems.BLUE_CHALK, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(RitualItems.PURPLE_CHALK, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(RitualItems.RED_CHALK, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(RitualItems.RESIDUUM_CHALK, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(RitualItems.INCENSE, ModelTemplates.FLAT_ITEM);
        generators.generateFlatItem(ReligiousItems.BLESSED_INCENSE, ModelTemplates.FLAT_ITEM);
        // Rune Items — Forms
        generateRuneItem(generators, RuneItems.RUNE_TOUCH, "touch");
        generateRuneItem(generators, RuneItems.RUNE_PROJECTILE, "projectile");
        generateRuneItem(generators, RuneItems.RUNE_SELF, "self");
        // Rune Items — Effects
        generateRuneItem(generators, RuneItems.RUNE_BREAK, "break");
        generateRuneItem(generators, RuneItems.RUNE_PICKUP, "pickup");
        generateRuneItem(generators, RuneItems.RUNE_LAUNCH, "launch");
        generateRuneItem(generators, RuneItems.RUNE_IGNITE, "ignite");
        generateRuneItem(generators, RuneItems.RUNE_EXPLOSION, "explosion");
        generateRuneItem(generators, RuneItems.RUNE_GLIDE, "glide");
        generateRuneItem(generators, RuneItems.RUNE_SMELT, "smelt");
        generateRuneItem(generators, RuneItems.RUNE_ORBIT, "orbit");
        generateRuneItem(generators, RuneItems.RUNE_HARM, "harm");
        generateRuneItem(generators, RuneItems.RUNE_HEAL, "heal");
        generateRuneItem(generators, RuneItems.RUNE_HEX, "hex");
        generateRuneItem(generators, RuneItems.RUNE_LIGHTNING, "lightning");
        generateRuneItem(generators, RuneItems.RUNE_CHAINING, "chaining");
        generateRuneItem(generators, RuneItems.RUNE_GROW, "grow");
        generateRuneItem(generators, RuneItems.RUNE_LINGER, "linger");
        generateRuneItem(generators, RuneItems.RUNE_HARVEST, "harvest");
        generateRuneItem(generators, RuneItems.RUNE_BURST, "burst");
        generateRuneItem(generators, RuneItems.RUNE_SUMMON_UNDEAD, "summon_undead");
        // Rune Items — Augments
        generateRuneItem(generators, RuneItems.RUNE_AMPLIFY, "amplify");
        generateRuneItem(generators, RuneItems.RUNE_AOE, "aoe");
        generateRuneItem(generators, RuneItems.RUNE_REDUCE_TIME, "reduce_time");
        generateRuneItem(generators, RuneItems.RUNE_EXTEND_TIME, "extend_time");
        generateRuneItem(generators, RuneItems.RUNE_DAMPEN, "dampen");
        generateRuneItem(generators, RuneItems.RUNE_SENSITIVE, "sensitive");
        generateRuneItem(generators, RuneItems.RUNE_PIERCE, "pierce");
        generateRuneItem(generators, RuneItems.RUNE_FORTUNE, "fortune");
        generateRuneItem(generators, RuneItems.RUNE_RANDOMIZE, "randomize");
        generateRuneItem(generators, RuneItems.RUNE_EXTRACT, "extract");
        generateRuneItem(generators, RuneItems.RUNE_ACCELERATE, "accelerate");
        generateRuneItem(generators, RuneItems.RUNE_DECELERATE, "decelerate");
        generateRuneItem(generators, RuneItems.RUNE_SPLIT, "split");

        //Defaults
            //Default Potion
        Identifier minorPotionModel = Identifier.fromNamespaceAndPath("totality", "item/minor_potion");
        Identifier standardPotionModel = Identifier.fromNamespaceAndPath("totality", "item/standard_potion");
        Identifier plentifulPotionModel = Identifier.fromNamespaceAndPath("totality", "item/plentiful_potion");
        Identifier vigorousPotionModel = Identifier.fromNamespaceAndPath("totality", "item/vigorous_potion");
        Identifier extremePotionModel = Identifier.fromNamespaceAndPath("totality", "item/extreme_potion");
        Identifier ultimatePotionModel = Identifier.fromNamespaceAndPath("totality", "item/ultimate_potion");
        generators.itemModelOutput.accept(
                PotionItems.BREWED_POTION,
                ItemModelUtils.tintedModel(minorPotionModel, PotionTintSource.INSTANCE)
        );
            //Minor Potions
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_MINOR_HEALING,
                ItemModelUtils.tintedModel(minorPotionModel, PotionTintSource.INSTANCE)
        );
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_MINOR_MANA,
                ItemModelUtils.tintedModel(minorPotionModel, PotionTintSource.INSTANCE)
        );
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_MINOR_STAMINA,
                ItemModelUtils.tintedModel(minorPotionModel, PotionTintSource.INSTANCE)
        );

        //Standard Potions
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_HEALING,
                ItemModelUtils.tintedModel(standardPotionModel, PotionTintSource.INSTANCE)
        );
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_MANA,
                ItemModelUtils.tintedModel(standardPotionModel, PotionTintSource.INSTANCE)
        );
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_STAMINA,
                ItemModelUtils.tintedModel(standardPotionModel, PotionTintSource.INSTANCE)
        );
        //Plentiful Potions
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_PLENTIFUL_HEALING,
                ItemModelUtils.tintedModel(plentifulPotionModel, PotionTintSource.INSTANCE)
        );
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_PLENTIFUL_MANA,
                ItemModelUtils.tintedModel(plentifulPotionModel, PotionTintSource.INSTANCE)
        );
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_PLENTIFUL_STAMINA,
                ItemModelUtils.tintedModel(plentifulPotionModel, PotionTintSource.INSTANCE)
        );
        //Vigorous Potions
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_VIGOROUS_HEALING,
                ItemModelUtils.tintedModel(vigorousPotionModel, PotionTintSource.INSTANCE)
        );
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_VIGOROUS_MANA,
                ItemModelUtils.tintedModel(vigorousPotionModel, PotionTintSource.INSTANCE)
        );
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_VIGOROUS_STAMINA,
                ItemModelUtils.tintedModel(vigorousPotionModel, PotionTintSource.INSTANCE)
        );
        //Extreme Potions
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_EXTREME_HEALING,
                ItemModelUtils.tintedModel(extremePotionModel, PotionTintSource.INSTANCE)
        );
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_EXTREME_MANA,
                ItemModelUtils.tintedModel(extremePotionModel, PotionTintSource.INSTANCE)
        );
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_EXTREME_STAMINA,
                ItemModelUtils.tintedModel(extremePotionModel, PotionTintSource.INSTANCE)
        );
        //Ultimate Potions
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_ULTIMATE_HEALING,
                ItemModelUtils.tintedModel(ultimatePotionModel, PotionTintSource.INSTANCE)
        );
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_ULTIMATE_MANA,
                ItemModelUtils.tintedModel(ultimatePotionModel, PotionTintSource.INSTANCE)
        );
        generators.itemModelOutput.accept(
                PotionItems.POTION_OF_ULTIMATE_STAMINA,
                ItemModelUtils.tintedModel(ultimatePotionModel, PotionTintSource.INSTANCE)
        );
    }
    //Helper Classes
    private void generateRuneItem(ItemModelGenerators generators, net.minecraft.world.item.Item item, String runeId) {
        Identifier modelId = Identifier.fromNamespaceAndPath("totality", "item/rune_" + runeId);
        Identifier textureId = Identifier.fromNamespaceAndPath("totality", "gui/sprites/rune/" + runeId);
        ModelTemplates.FLAT_ITEM.create(modelId, TextureMapping.layer0(
                        new net.minecraft.client.resources.model.sprite.Material(textureId)),
                generators.modelOutput);
        generators.itemModelOutput.accept(item, ItemModelUtils.plainModel(modelId));
    }
    private void registerFluidTank(BlockModelGenerators generators, FluidTankBlock block) {
        // Points to your hand-made JSON model, generates blockstate JSON
        generators.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(
                        block,
                        BlockModelGenerators.plainVariant(
                                Identifier.fromNamespaceAndPath("totality", "block/copper_tank"))));

        // Generates item model JSON pointing to the block model

    }
}

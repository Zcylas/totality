package zcylas.totality.init;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import zcylas.totality.Totality;
import zcylas.totality.block.energy.EnergyCellBlock;
import zcylas.totality.block.fluid.FluidTankBlock;
import zcylas.totality.blockentity.energy.CableBlockEntity;
import zcylas.totality.blockentity.energy.ElectricFurnaceBlockEntity;
import zcylas.totality.blockentity.energy.EnergyCellBlockEntity;
import zcylas.totality.blockentity.fluid.FluidTankBlockEntity;
import zcylas.totality.blockentity.generator.GeneratorBlockEntity;
import zcylas.totality.blockentity.ritual.RitualAltarBlockEntity;
import zcylas.totality.blockentity.ritual.RitualDaisBlockEntity;
import zcylas.totality.init.blocks.EnergyBlocks;
import zcylas.totality.init.blocks.RitualBlocks;

public class ModBlockEntities {

    public static final BlockEntityType<FluidTankBlockEntity> FLUID_TANK = register(
            "fluid_tank",
            FabricBlockEntityTypeBuilder.create(
                    (pos, state) -> new FluidTankBlockEntity(
                            pos, state, ((FluidTankBlock) state.getBlock()).getCapacityMb()),
                    ModBlocks.COPPER_TANK
            ).build()
    );

    public static final BlockEntityType<GeneratorBlockEntity> GENERATOR = register(
            "generator",
            FabricBlockEntityTypeBuilder.create(GeneratorBlockEntity::new,
                    EnergyBlocks.GENERATOR).build()
    );

    public static final BlockEntityType<CableBlockEntity> CABLE = register(
            "cable",
            FabricBlockEntityTypeBuilder.create(
                    CableBlockEntity::new,
                    EnergyBlocks.COPPER_CABLE
                    // add more cables here as you register them:
                    // ModBlocks.GOLD_CABLE, ModBlocks.IRON_CABLE
            ).build()
    );

    public static final BlockEntityType<EnergyCellBlockEntity> ENERGY_CELL = register(
            "energy_cell",
            FabricBlockEntityTypeBuilder.<EnergyCellBlockEntity>create(
                    (pos, state) -> new EnergyCellBlockEntity(
                            pos, state,
                            ((EnergyCellBlock) state.getBlock()).getCapacity(),
                            ((EnergyCellBlock) state.getBlock()).getMaxInput(),
                            ((EnergyCellBlock) state.getBlock()).getMaxOutput())
            ).addBlock(EnergyBlocks.COPPER_ENERGY_CELL).build()
    );

    public static final BlockEntityType<ElectricFurnaceBlockEntity> ELECTRIC_FURNACE = register(
            "electric_furnace",
            FabricBlockEntityTypeBuilder.create(
                    ElectricFurnaceBlockEntity::new,
                    EnergyBlocks.ELECTRIC_FURNACE
            ).build()
    );
    public static final BlockEntityType<RitualAltarBlockEntity> RITUAL_ALTAR = register(
            "ritual_altar",
            FabricBlockEntityTypeBuilder.create(
                    RitualAltarBlockEntity::new,
                    RitualBlocks.RITUAL_ALTAR  // we'll add this shortly
            ).build()
    );
    public static final BlockEntityType<RitualDaisBlockEntity> RITUAL_DAIS = register(
            "ritual_dais",
            FabricBlockEntityTypeBuilder.create(
                    RitualDaisBlockEntity::new,
                    RitualBlocks.RITUAL_DAIS  // we'll add this to RitualBlocks next
            ).build()
    );

    public static void register() {}

    public static <T extends net.minecraft.world.level.block.entity.BlockEntity>
    BlockEntityType<T> register(String name, BlockEntityType<T> type) {
        return Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, name),
                type
        );
    }



    private ModBlockEntities() {}
}
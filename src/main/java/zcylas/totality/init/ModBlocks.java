package zcylas.totality.init;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import zcylas.totality.api.fluid.FluidTier;
import zcylas.totality.api.fluid.TotalityFluidStorage;
import zcylas.totality.block.energy.CableBlock;
import zcylas.totality.block.energy.EnergyCellBlock;
import zcylas.totality.block.fluid.FluidTankBlock;
import zcylas.totality.block.generator.GeneratorBlock;

public class ModBlocks {

    public static final FluidTankBlock COPPER_TANK = TotalityRegistry.registerFluidTank("copper_tank",8_000);
    public static final GeneratorBlock GENERATOR = TotalityRegistry.registerBlock(
            "generator",
            GeneratorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5f, 3.5f)
                    .requiresCorrectToolForDrops(),
            true
    );
    public static final EnergyCellBlock COPPER_ENERGY_CELL = TotalityRegistry.registerEnergyCell(
            "copper_energy_cell", 500_000, 64, 64);
    public static final CableBlock COPPER_CABLE = TotalityRegistry.registerCable("copper_cable", 32L);

    public static void register(){

    }

    private ModBlocks(){}
}

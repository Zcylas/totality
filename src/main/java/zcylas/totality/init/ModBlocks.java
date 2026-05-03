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
import zcylas.totality.init.blocks.AlchemyBlocks;
import zcylas.totality.init.blocks.EnergyBlocks;
import zcylas.totality.init.blocks.OreBlocks;

public class ModBlocks {

    public static final FluidTankBlock COPPER_TANK = TotalityRegistry.registerFluidTank("copper_tank",8_000);

    public static void register(){
        OreBlocks.register();
        EnergyBlocks.register();
        AlchemyBlocks.register();
    }

    private ModBlocks(){}
}

package zcylas.totality.init;

import zcylas.totality.block.fluid.FluidTankBlock;
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

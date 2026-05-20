package zcylas.totality.init;

import zcylas.totality.block.fluid.FluidTankBlock;
import zcylas.totality.init.blocks.*;

public class ModBlocks {

    public static final FluidTankBlock COPPER_TANK = TotalityRegistry.registerFluidTank("copper_tank",8_000);

    public static void register(){
        OreBlocks.register();
        EnergyBlocks.register();
        AlchemyBlocks.register();
        RitualBlocks.register();
        WhitestoneBlocks.register();
        NaturalBlocks.register();
    }

    private ModBlocks(){}
}

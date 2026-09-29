package zcylas.totality.init;

import net.minecraft.world.level.block.Block;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.block.fluid.FluidTankBlock;
import zcylas.totality.init.blocks.*;

public class ModBlocks {

    public static final FluidTankBlock COPPER_TANK = TotalityRegistry.registerFluidTank("copper_tank",8_000, ItemRarity.CRUDE);

    public static void register(){
        OreBlocks.register();
        EnergyBlocks.register();
        AlchemyBlocks.register();
        RitualBlocks.register();
        WhitestoneBlocks.register();
        NaturalBlocks.register();
        CookingBlocks.register();
    }

    private ModBlocks(){}
}

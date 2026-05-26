package zcylas.totality.init;

import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import zcylas.totality.block.ritual.ChalkBlock;
import zcylas.totality.init.blocks.RitualBlocks;

import java.util.List;
import java.util.Set;

public final class TotalityBlockColors {

    public static void register() {
        registerChalkColors();
    }

    private static void registerChalkColors() {
        BlockTintSource chalkTint = new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return state.getValue(ChalkBlock.COLOR).getTint();
            }

            @Override
            public Set<Property<?>> relevantProperties() {
                return Set.of(ChalkBlock.COLOR);
            }
        };

        BlockColorRegistry.register(List.of(chalkTint), RitualBlocks.CHALK);
    }

    private TotalityBlockColors() {}
}
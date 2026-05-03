package zcylas.totality.block.alchemy;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class BlueMountainFlowerBlock extends FlowerBlock {

    public BlueMountainFlowerBlock(Holder<MobEffect> stewEffect, int duration, BlockBehaviour.Properties properties) {
        super(stewEffect, duration, properties);
    }
}
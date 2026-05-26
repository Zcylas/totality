package zcylas.totality.api.ability.harvest.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.api.ability.harvest.HarvestHandler;
import zcylas.totality.api.core.util.MountainFlowerBushBlock;
import zcylas.totality.api.rpg.skills.core.MasteriesComponents;

public class MountainFlowerHarvestHandler implements HarvestHandler {

    @Override
    public boolean canHarvest(BlockState state) {
        return state.getBlock() instanceof MountainFlowerBushBlock
                && !state.getValue(MountainFlowerBushBlock.HARVESTED);
    }

    @Override
    public void harvest(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        MountainFlowerBushBlock flowerBush = (MountainFlowerBushBlock) state.getBlock();
        ItemStack drop = new ItemStack(flowerBush.getFlowerItem());

        boolean greenThumb = MasteriesComponents.get(player)
                .getMasteries().getUnlockedRank("green_thumb") > 0;
        if (greenThumb) drop.grow(drop.getCount());

        Block.popResource(level, pos, drop);
        level.setBlock(pos, state.setValue(MountainFlowerBushBlock.HARVESTED, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS,
                1.0f, 0.8f + level.getRandom().nextFloat() * 0.4f);
    }
}
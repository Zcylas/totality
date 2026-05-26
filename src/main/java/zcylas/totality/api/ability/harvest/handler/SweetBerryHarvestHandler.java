package zcylas.totality.api.ability.harvest.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.api.ability.harvest.HarvestHandler;

import java.util.List;

public class SweetBerryHarvestHandler implements HarvestHandler {

    @Override
    public boolean canHarvest(BlockState state) {
        return state.getBlock() instanceof SweetBerryBushBlock
                && state.getValue(SweetBerryBushBlock.AGE) >= 2;
    }

    @Override
    public void harvest(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY);
        level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS,
                1.0f, 0.8f + level.getRandom().nextFloat() * 0.4f);
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            if (!player.getInventory().add(drop)) player.drop(drop, false);
        }
    }
}
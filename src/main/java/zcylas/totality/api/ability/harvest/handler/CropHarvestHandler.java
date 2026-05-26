package zcylas.totality.api.ability.harvest.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.api.ability.harvest.HarvestHandler;

import java.util.List;

public class CropHarvestHandler implements HarvestHandler {

    @Override
    public boolean canHarvest(BlockState state) {
        if (!(state.getBlock() instanceof CropBlock crop)) return false;
        return crop.isMaxAge(state);
    }

    @Override
    public void harvest(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY);
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        removeSeed(drops, block, level, pos);
        playSound(level, pos);
        giveToPlayer(player, drops);
    }

    private void removeSeed(List<ItemStack> drops, Block block, ServerLevel level, BlockPos pos) {
        List<ItemStack> seedDrops = Block.getDrops(block.defaultBlockState(), level, pos, null);
        if (seedDrops.isEmpty()) return;
        ItemStack seed = seedDrops.get(0);
        if (seed.isEmpty()) return;
        for (ItemStack stack : drops) {
            if (stack.getItem() == seed.getItem()) {
                stack.shrink(1);
                return;
            }
        }
    }

    private void giveToPlayer(ServerPlayer player, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            if (!player.getInventory().add(drop)) player.drop(drop, false);
        }
    }

    private void playSound(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS,
                1.0f, 0.8f + level.getRandom().nextFloat() * 0.4f);
    }
}
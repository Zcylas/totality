package zcylas.totality.api.ability.harvest.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.api.ability.harvest.HarvestHandler;
import zcylas.totality.api.rpg.skills.core.MasteriesComponents;
import zcylas.totality.init.ModTags;

public class GenericTaggedHarvestHandler implements HarvestHandler {

    @Override
    public boolean canHarvest(BlockState state) {
        return state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SMALL_FLOWERS)
                || state.is(ModTags.HARVESTABLE);
    }

    @Override
    public void harvest(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        boolean greenThumb = MasteriesComponents.get(player)
                .getMasteries().getUnlockedRank("green_thumb") > 0;
        boolean isAlchemyIngredient = state.is(ModTags.HARVESTABLE);

        java.util.List<ItemStack> drops = Block.getDrops(
                state, level, pos, null, player, ItemStack.EMPTY);
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS,
                1.0f, 0.8f + level.getRandom().nextFloat() * 0.4f);

        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            if (greenThumb && isAlchemyIngredient) drop.grow(drop.getCount());
            if (!player.getInventory().add(drop)) player.drop(drop, false);
        }
    }
}
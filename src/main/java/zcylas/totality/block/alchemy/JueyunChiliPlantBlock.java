package zcylas.totality.block.alchemy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import zcylas.totality.api.core.util.MountainFlowerBushBlock;
import zcylas.totality.init.items.SKIngredientItems;

/**
 * Jueyun Chili Plant — a wild, regrowing, harvestable plant using the Mountain Flower Bush pattern unchanged:
 * {@code harvested=false} is fruiting (right-click pops exactly one Jueyun Chili and the plant turns picked),
 * {@code harvested=true} is picked (regrows on a 1-in-50 random tick, or bone meal). The Harvest ability reaches it
 * through the existing {@code MountainFlowerHarvestHandler}. Not a crop: no farmland, seeds or age stages.
 *
 * <p>Additions over the bush: vanilla vegetation support ({@code #minecraft:supports_vegetation}, popping off like
 * a flower when its ground goes), a plant-sized outline, and the harvest handler's pick sound on right-click.
 */
public class JueyunChiliPlantBlock extends MountainFlowerBushBlock {

    private static final VoxelShape SHAPE = Block.column(12.0, 0.0, 15.0);

    public JueyunChiliPlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public Item getFlowerItem() {
        return SKIngredientItems.JUEYUN_CHILI;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        boolean fruiting = !state.getValue(HARVESTED);
        InteractionResult result = super.useWithoutItem(state, level, pos, player, hit);
        if (fruiting && !level.isClientSide()) {
            level.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS,
                    1.0f, 0.8f + level.getRandom().nextFloat() * 0.4f);
        }
        return result;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(BlockTags.SUPPORTS_VEGETATION);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        return !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }
}

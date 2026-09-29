package zcylas.totality.block.cooking;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import zcylas.totality.blockentity.cooking.CuttingBoardBlockEntity;

/**
 * Cutting Board (Creative Test F): a low wooden board, 14 x 8 px and 2 px thick, placed on any sturdy top face. Its
 * {@link CuttingBoardBlockEntity} holds one ingredient stack; right-click adds one ingredient, an empty hand takes
 * the stack back, and a sword (temporary cutting tool until a Knife exists) chops one ingredient per use.
 *
 * <p>{@link #FACING} is the direction the placing player looked; the handle points to their right.
 */
public class CuttingBoardBlock extends BaseEntityBlock {

    public static final MapCodec<CuttingBoardBlock> CODEC = simpleCodec(CuttingBoardBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** The board's footprint: 14 px along the handle axis, 8 px across, 2 px high. */
    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(1, 0, 4, 15, 2, 12);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(4, 0, 1, 12, 2, 15);

    public CuttingBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        return direction == Direction.DOWN && !state.canSurvive(level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NORTH_SOUTH : SHAPE_EAST_WEST;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CuttingBoardBlockEntity(pos, state);
    }

    /**
     * Server-authoritative: the client only reports SUCCESS for interactions the board will handle (so the hand
     * swings and the use is not passed on to the item), and every change happens once, in the server's call.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CuttingBoardBlockEntity board)) return InteractionResult.PASS;
        if (stack.is(ItemTags.SWORDS)) {
            if (!CuttingBoardBlockEntity.canChop(board.getIngredient())) return InteractionResult.PASS;
            if (!level.isClientSide()) board.chop(player, hand);
            return InteractionResult.SUCCESS;
        }
        if (CuttingBoardBlockEntity.canChop(stack)) {
            if (!board.canAdd(stack)) return InteractionResult.FAIL;
            if (!level.isClientSide()) board.addOne(stack, player);
            return InteractionResult.SUCCESS;
        }
        if (stack.isEmpty() && !board.getIngredient().isEmpty()) {
            if (!level.isClientSide()) board.takeAll(player);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}

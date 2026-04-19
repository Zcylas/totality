package zcylas.totality.block.energy;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Util;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;
import zcylas.totality.blockentity.energy.CableBlockEntity;
import zcylas.totality.init.ModBlockEntities;

import java.util.HashMap;
import java.util.Map;

public class CableBlock extends BaseEntityBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST  = BlockStateProperties.EAST;
    public static final BooleanProperty WEST  = BlockStateProperties.WEST;
    public static final BooleanProperty UP    = BlockStateProperties.UP;
    public static final BooleanProperty DOWN  = BlockStateProperties.DOWN;

    public static final Map<Direction, BooleanProperty> PROPERTY_MAP = Util.make(new HashMap<>(), map -> {
        map.put(Direction.NORTH, NORTH);
        map.put(Direction.SOUTH, SOUTH);
        map.put(Direction.EAST,  EAST);
        map.put(Direction.WEST,  WEST);
        map.put(Direction.UP,    UP);
        map.put(Direction.DOWN,  DOWN);
    });

    // Cable thickness as fraction of block (0.375 = 6/16 centered)
    public static final double THICKNESS = 0.375;

    private final long transferPerTick;

    public CableBlock(BlockBehaviour.Properties properties, long transferPerTick) {
        super(properties);
        this.transferPerTick = transferPerTick;
        registerDefaultState(defaultBlockState()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST,  false).setValue(WEST,  false)
                .setValue(UP,    false).setValue(DOWN,  false));
    }

    public long getTransferPerTick() { return transferPerTick; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        throw new IllegalStateException("CableBlock does not support codec");
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState();
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader world,
                                  ScheduledTickAccess tickView, BlockPos pos,
                                  Direction direction, BlockPos neighborPos,
                                  BlockState neighborState, RandomSource random) {
        return state;
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos,
                                Block block, net.minecraft.world.level.redstone.Orientation orientation,
                                boolean notify) {
        if (world.getBlockEntity(pos) instanceof CableBlockEntity cable) {
            cable.onNeighborChanged();
        }
        super.neighborChanged(state, world, pos, block, orientation, notify);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext ctx) {
        return CableShapeUtil.getShape(state);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.CABLE,
                (world, pos, blockState, be) -> be.tick());
    }
}
package zcylas.totality.block.energy;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.Containers;
import org.jspecify.annotations.Nullable;
import zcylas.totality.blockentity.energy.ElectricFurnaceBlockEntity;
import zcylas.totality.init.ModBlockEntities;

public class ElectricFurnaceBlock extends BaseEntityBlock {

    public static final MapCodec<ElectricFurnaceBlock> CODEC =
            simpleCodec(ElectricFurnaceBlock::new);

    public static final Property<Direction> FACING =
            BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ACTIVE =
            BooleanProperty.create("active");

    public ElectricFurnaceBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, net.minecraft.core.Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    // ── Block entity ──────────────────────────────────────────────────────────
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricFurnaceBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.ELECTRIC_FURNACE,
                ElectricFurnaceBlockEntity::tick);
    }

    // ── Interaction ───────────────────────────────────────────────────────────
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ElectricFurnaceBlockEntity furnaceBE) {
                serverPlayer.openMenu(furnaceBE);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ── Drop inventory on break (any cause — explosion, piston, player) ───────
    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ElectricFurnaceBlockEntity furnaceBE) {
            Containers.dropContents((Level) level, pos, furnaceBE.getItems());
        }
        super.destroy(level, pos, state);
    }

    // ── Placement facing ──────────────────────────────────────────────────────
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    // ── Blockstate ────────────────────────────────────────────────────────────
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
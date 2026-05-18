package zcylas.totality.block.ritual;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.blockentity.ritual.RitualAltarBlockEntity;
import zcylas.totality.init.ModBlockEntities;

public class RitualAltarBlock extends BaseEntityBlock {
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE =
            net.minecraft.world.phys.shapes.Shapes.box(0, 0, 0, 1, 15/16.0, 1);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public RitualAltarBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(RitualAltarBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING,
                context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RitualAltarBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.RITUAL_ALTAR,
                RitualAltarBlockEntity::tick);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          BlockHitResult hit) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RitualAltarBlockEntity altar) {
            // Empty hand — retrieve item
            if (stack.isEmpty()) {
                if (!level.isClientSide() && !altar.isEmpty()) {
                    if (player.isShiftKeyDown()) {
                        // Sneak + right-click → activate ritual
                        altar.tryActivate(level, pos, state, player);
                    } else {
                        // Right-click → retrieve
                        ItemStack toReturn = altar.getHeldItem();
                        boolean added = false;
                        int selected = player.getInventory().getSelectedSlot();
                        if (player.getInventory().getItem(selected).isEmpty()) {
                            player.getInventory().setItem(selected, toReturn.copy());
                            added = true;
                        }
                        if (!added) added = player.getInventory().add(toReturn);
                        if (!added) {
                            net.minecraft.world.Containers.dropItemStack(level,
                                    player.getX(), player.getY(), player.getZ(), toReturn);
                        }
                        altar.setHeldItem(ItemStack.EMPTY);
                        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                                net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                    }
                }
                return InteractionResult.SUCCESS;
            }
            // Item in hand — place it
            if (!altar.isEmpty()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide()) {
                altar.setHeldItem(stack.copyWithCount(1));
                stack.shrink(1);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
                                                                  net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                                                  net.minecraft.world.phys.shapes.CollisionContext context) {
        return SHAPE;
    }
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof RitualAltarBlockEntity altar && !altar.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(
                    level, pos.getX(), pos.getY(), pos.getZ(), altar.getHeldItem());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }


}
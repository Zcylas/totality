package zcylas.totality.block.fluid;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import zcylas.totality.blockentity.fluid.FluidTankBlockEntity;
import zcylas.totality.init.ModBlockEntities;

public class FluidTankBlock extends BaseEntityBlock {

    public static final MapCodec<FluidTankBlock> CODEC = simpleCodec(
            properties -> new FluidTankBlock(properties, 8_000)
    );

    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 14, 12);
    private final long capacityMb;

    public FluidTankBlock(BlockBehaviour.Properties properties, long capacityMb) {
        super(properties);
        this.capacityMb = capacityMb;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public long getCapacityMb() {
        return capacityMb;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level,
                                  BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FluidTankBlockEntity(pos, state, capacityMb);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(
                type,
                ModBlockEntities.FLUID_TANK,
                (world, pos, blockState, blockEntity) -> blockEntity.tick()
        );
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FluidTankBlockEntity tankEntity)) {
            return InteractionResult.PASS;
        }

        ItemStack heldStack = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (heldStack.getItem() instanceof BucketItem bucketItem) {
            return handleBucketInteraction(level, player, tankEntity, heldStack, bucketItem);
        }

        return InteractionResult.PASS;
    }

    private InteractionResult handleBucketInteraction(
            Level level, Player player,
            FluidTankBlockEntity tankEntity,
            ItemStack heldStack, BucketItem bucketItem) {

        Storage<FluidVariant> tankStorage = tankEntity.getFluidStorage(null);

        // Empty bucket — drain from tank into bucket
        if (heldStack.is(Items.BUCKET)) {
            if (tankEntity.getFluidStorage().isEmpty()) {
                return InteractionResult.FAIL;
            }

            FluidVariant storedVariant = tankEntity.getFluidStorage().variant;

            try (Transaction transaction = Transaction.openOuter()) {
                long extracted = tankStorage.extract(
                        storedVariant, FluidConstants.BUCKET, transaction);

                if (extracted == FluidConstants.BUCKET) {
                    ItemStack filledBucket = storedVariant.getFluid()
                            .getBucket().getDefaultInstance();

                    if (!player.isCreative()) {
                        heldStack.shrink(1);
                        if (heldStack.isEmpty()) {
                            player.setItemInHand(InteractionHand.MAIN_HAND, filledBucket);
                        } else {
                            player.getInventory().add(filledBucket);
                        }
                    }

                    transaction.commit();
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.FAIL;
        }

        // Filled bucket — fill tank from bucket
        FluidVariant bucketFluid = getBucketFluid(bucketItem);
        if (bucketFluid == null || bucketFluid.isBlank()) {
            return InteractionResult.PASS;
        }

        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = tankStorage.insert(
                    bucketFluid, FluidConstants.BUCKET, transaction);

            if (inserted == FluidConstants.BUCKET) {
                if (!player.isCreative()) {
                    heldStack.shrink(1);
                    if (heldStack.isEmpty()) {
                        player.setItemInHand(
                                InteractionHand.MAIN_HAND,
                                Items.BUCKET.getDefaultInstance());
                    } else {
                        player.getInventory().add(Items.BUCKET.getDefaultInstance());
                    }
                }

                transaction.commit();
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.FAIL;
    }

    @Nullable
    private FluidVariant getBucketFluid(BucketItem bucket) {
        Storage<FluidVariant> bucketStorage = FluidStorage.ITEM.find(
                bucket.getDefaultInstance(),
                ContainerItemContext.withConstant(bucket.getDefaultInstance())
        );

        if (bucketStorage == null) return null;

        for (var view : bucketStorage) {
            if (!view.isResourceBlank() && view.getAmount() > 0) {
                return view.getResource();
            }
        }

        return null;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos,
                                        BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FluidTankBlockEntity tankEntity) {
                ItemStack drop = new ItemStack(this);
                if (!tankEntity.getFluidStorage().isEmpty()) {
                    drop.applyComponents(tankEntity.collectComponents());
                }
                Block.popResource(level, pos, drop);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
package zcylas.totality.fluid.base;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.NonNull;

import java.util.function.Supplier;

public abstract class TotalityFluid extends FlowingFluid {

    private final boolean still;
    private final Supplier<? extends FlowingFluid> stillFluid;
    private final Supplier<? extends FlowingFluid> flowingFluid;
    private final Supplier<Block> block;
    private final Supplier<Item> bucket;

    protected TotalityFluid(
            boolean still,
            Supplier<? extends FlowingFluid> stillFluid,
            Supplier<? extends FlowingFluid> flowingFluid,
            Supplier<Block> block,
            Supplier<Item> bucket
    ) {
        this.still = still;
        this.stillFluid = stillFluid;
        this.flowingFluid = flowingFluid;
        this.block = block;
        this.bucket = bucket;
    }

    @Override
    public @NonNull FlowingFluid getSource() {
        return stillFluid.get();
    }

    @Override
    public @NonNull FlowingFluid getFlowing() {
        return flowingFluid.get();
    }

    @Override
    public @NonNull Item getBucket() {
        return bucket.get();
    }

    @Override
    public boolean isSource(@NonNull FluidState state) {
        return still;
    }

    @Override
    public int getAmount(@NonNull FluidState state) {
        return still ? 8 : state.getValue(LEVEL);
    }

    @Override
    protected boolean canConvertToSource(@NonNull ServerLevel level) {
        return false;
    }

    @Override
    protected void beforeDestroyingBlock(@NonNull LevelAccessor level, @NonNull BlockPos pos, BlockState state) {
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        Block.dropResources(state, level, pos, blockEntity);
    }

    @Override
    protected int getSlopeFindDistance(LevelReader level) {
        return 4; // vanilla-like
    }

    @Override
    protected int getDropOff(LevelReader level) {
        return 1; // vanilla-like
    }

    @Override
    protected boolean canBeReplacedWith(@NonNull FluidState state, @NonNull BlockGetter level, @NonNull BlockPos pos, @NonNull Fluid other, @NonNull Direction direction) {
        return direction == Direction.DOWN && !this.isSame(other);
    }

    @Override
    public int getTickDelay(@NonNull LevelReader world) {
        return 10;
    }

    @Override
    protected float getExplosionResistance() {
        return 100F;
    }

    @Override
    protected @NonNull BlockState createLegacyBlock(@NonNull FluidState fluidState) {
        return block.get()
                .defaultBlockState()
                .setValue(LiquidBlock.LEVEL, getLegacyLevel(fluidState));
    }
}
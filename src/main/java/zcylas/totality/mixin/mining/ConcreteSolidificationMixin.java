package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import zcylas.totality.api.mining.BlockDamageStorage;

/**
 * Block Breaking V2 Pass 4B: an EXISTING Concrete Powder block solidifying in place runs inside
 * {@link BlockDamageStorage#transaction}. In 26.2 that happens only through {@code ConcretePowderBlock.updateShape}
 * (water now touches it), whose result every in-place shape update applies with the static
 * {@code Block.updateOrDestroy}; the wrapper runs only when the block being updated is Concrete Powder (one
 * {@code instanceof} on the common path) and then runs vanilla unchanged. Placement next to water
 * ({@code getStateForPlacement}) and a falling powder landing in water ({@code onLand}, at the landing position, after
 * the original block was removed) are fresh blocks and are deliberately NOT transformations.
 */
@Mixin(Block.class)
public abstract class ConcreteSolidificationMixin {

    @WrapMethod(method = "updateOrDestroy(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;II)V")
    private static void totality$solidifyInPlace(BlockState blockState, BlockState newState, LevelAccessor level, BlockPos pos,
                                                 int updateFlags, int updateLimit, Operation<Void> original) {
        if (!(blockState.getBlock() instanceof ConcretePowderBlock) || !(level instanceof ServerLevel serverLevel)) {
            original.call(blockState, newState, level, pos, updateFlags, updateLimit);
            return;
        }
        BlockDamageStorage.transaction(serverLevel, pos, () -> original.call(blockState, newState, level, pos, updateFlags, updateLimit));
    }
}

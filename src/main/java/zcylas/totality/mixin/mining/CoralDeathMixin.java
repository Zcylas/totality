package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CoralBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import zcylas.totality.api.mining.BlockDamageStorage;

/**
 * Block Breaking V2 Pass 4B: a living full Coral Block dying in place runs inside {@link BlockDamageStorage#transaction}.
 * {@code CoralBlock.tick} (scheduled by vanilla's own survival check) sets the dead block when no water is adjacent;
 * the tick is wrapped whole and runs unchanged. Small Coral and fans are other classes, stay SPECIAL and are untouched.
 */
@Mixin(CoralBlock.class)
public abstract class CoralDeathMixin {

    @WrapMethod(method = "tick")
    private void totality$dieInPlace(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
        BlockDamageStorage.transaction(level, pos, () -> original.call(state, level, pos, random));
    }
}

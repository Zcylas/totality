package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import zcylas.totality.api.mining.BlockDamageStorage;

/**
 * Block Breaking V2 Pass 4B: a Water / Powder Snow Cauldron losing its last level to become an empty Cauldron runs inside
 * {@link BlockDamageStorage#transaction}. Every such emptying (a burning entity extinguished inside, a bottle taken,
 * ...) goes through the static {@code LayeredCauldronBlock.lowerFillLevel}, wrapped whole; lower levels are same-id.
 */
@Mixin(LayeredCauldronBlock.class)
public abstract class LayeredCauldronEmptyingMixin {

    @WrapMethod(method = "lowerFillLevel")
    private static void totality$emptyInPlace(BlockState state, Level level, BlockPos pos, Operation<Void> original) {
        if (!(level instanceof ServerLevel serverLevel)) {
            original.call(state, level, pos);
            return;
        }
        BlockDamageStorage.transaction(serverLevel, pos, () -> original.call(state, level, pos));
    }
}

package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.SpreadingSnowyBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zcylas.totality.api.mining.BlockDamageStorage;

/**
 * Block Breaking V2 Pass 4B: Grass Block / Mycelium decay and spread ({@code SpreadingSnowyBlock.randomTick}). The decay
 * call turns THIS block into Dirt; each spread call turns a SEPARATE Dirt block into Grass/Mycelium. Each
 * {@code setBlockAndUpdate} is wrapped in its own transaction at the position it changes, so the Dirt target keeps its
 * own Integrity and nothing is ever carried from the spreading source to the target.
 */
@Mixin(SpreadingSnowyBlock.class)
public abstract class GrassSpreadTransformationMixin {

    @WrapOperation(method = "randomTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean totality$spreadOrDecayInPlace(ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        return BlockDamageStorage.transaction(level, pos, () -> original.call(level, pos, state));
    }
}

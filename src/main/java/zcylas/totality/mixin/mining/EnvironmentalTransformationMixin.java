package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zcylas.totality.api.mining.BlockDamageStorage;

/**
 * Block Breaking V2 Pass 4B: lightning deoxidation. {@code LightningBolt.clearCopperOnLightningStrike} resets the struck
 * copper block ({@code WeatheringCopper.getFirst}), then random walks clean nearby copper one step each
 * ({@code lambda$randomStepCleaningCopper$0}). Each {@code setBlockAndUpdate} call is wrapped in its OWN transaction at
 * its OWN position: every block keeps its own Integrity; nothing is carried between positions.
 */
@Mixin(LightningBolt.class)
public abstract class EnvironmentalTransformationMixin {

    @WrapOperation(method = {"clearCopperOnLightningStrike", "lambda$randomStepCleaningCopper$0"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private static boolean totality$deoxidizeInPlace(Level level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        if (!(level instanceof ServerLevel serverLevel)) return original.call(level, pos, state);
        return BlockDamageStorage.transaction(serverLevel, pos, () -> original.call(level, pos, state));
    }
}

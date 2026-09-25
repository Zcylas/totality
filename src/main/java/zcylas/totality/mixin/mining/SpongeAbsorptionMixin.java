package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SpongeBlock;
import org.spongepowered.asm.mixin.Mixin;
import zcylas.totality.api.mining.BlockDamageStorage;

/**
 * Block Breaking V2 Pass 4B: a Sponge absorbing water runs inside {@link BlockDamageStorage#transaction}.
 * {@code SpongeBlock.tryAbsorbWater} (reached from {@code neighborChanged} for an existing Sponge, and from
 * {@code onPlace} for a freshly placed one, which has no record) removes the water elsewhere and then
 * {@code setBlock(pos, WET_SPONGE, 2)}; the whole method is wrapped at the Sponge's position only, so the water
 * removals elsewhere are untouched. Wet -> dry has no in-place path for an existing block in 26.2
 * ({@code WetSpongeBlock.onPlace} only dries a Wet Sponge as it is placed in an ultrawarm dimension).
 */
@Mixin(SpongeBlock.class)
public abstract class SpongeAbsorptionMixin {

    @WrapMethod(method = "tryAbsorbWater")
    private void totality$absorbInPlace(Level level, BlockPos pos, Operation<Void> original) {
        if (!(level instanceof ServerLevel serverLevel)) {
            original.call(level, pos);
            return;
        }
        BlockDamageStorage.transaction(serverLevel, pos, () -> original.call(level, pos));
    }
}

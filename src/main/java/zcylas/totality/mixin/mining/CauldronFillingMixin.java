package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import zcylas.totality.api.mining.BlockDamageStorage;

/**
 * Block Breaking V2 Pass 4B: an EMPTY Cauldron filled by the environment runs inside
 * {@link BlockDamageStorage#transaction}: precipitation ({@code handlePrecipitation} -> Water / Powder Snow Cauldron)
 * and a dripstone drip ({@code receiveStalactiteDrip} -> Water / Lava Cauldron). Both methods only change this
 * Cauldron and are wrapped whole. Filled cauldrons gaining a level are same-id changes and need nothing.
 */
@Mixin(CauldronBlock.class)
public abstract class CauldronFillingMixin {

    @WrapMethod(method = "handlePrecipitation")
    private void totality$precipitationInPlace(BlockState state, Level level, BlockPos pos, Biome.Precipitation precipitation, Operation<Void> original) {
        if (!(level instanceof ServerLevel serverLevel)) {
            original.call(state, level, pos, precipitation);
            return;
        }
        BlockDamageStorage.transaction(serverLevel, pos, () -> original.call(state, level, pos, precipitation));
    }

    @WrapMethod(method = "receiveStalactiteDrip")
    private void totality$dripInPlace(BlockState state, Level level, BlockPos pos, Fluid fluid, Operation<Void> original) {
        if (!(level instanceof ServerLevel serverLevel)) {
            original.call(state, level, pos, fluid);
            return;
        }
        BlockDamageStorage.transaction(serverLevel, pos, () -> original.call(state, level, pos, fluid));
    }
}

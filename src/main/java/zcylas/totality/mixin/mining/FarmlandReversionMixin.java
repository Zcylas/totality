package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import zcylas.totality.api.mining.BlockDamageStorage;

/**
 * Block Breaking V2 Pass 4A: Farmland and Dirt Path reversion to Dirt runs inside {@link BlockDamageStorage#transaction}.
 * Every reversion (trampling, drying out, a block placed above Farmland or a Path) goes through the static
 * {@code FarmlandBlock.turnToDirt}; it is wrapped whole and runs unchanged (entities pushed up, same update flags).
 */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandReversionMixin {

    @WrapMethod(method = "turnToDirt")
    private static void totality$revertInPlace(@Nullable Entity source, BlockState state, Level level, BlockPos pos, Operation<Void> original) {
        if (!(level instanceof ServerLevel serverLevel)) {
            original.call(source, state, level, pos);
            return;
        }
        BlockDamageStorage.transaction(serverLevel, pos, () -> original.call(source, state, level, pos));
    }
}

package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import zcylas.totality.api.mining.BlockDamageStorage;

import java.util.Optional;

/**
 * Block Breaking V2 Pass 4B: Anvil USE degradation runs inside {@link BlockDamageStorage#transaction}. In 26.2 a placed
 * Anvil degrades in place only in {@code AnvilMenu.onTake} (12% chance per use: {@code AnvilBlock.damage} ->
 * {@code setBlock(pos, next, 2)}, or {@code removeBlock} when a Damaged Anvil breaks — terminal, so the record is
 * deleted). The whole vanilla method is wrapped at the menu's own block position. A FALLING Anvil degrades the
 * entity's carried state and lands elsewhere: the original block was removed when it started falling, so that is not
 * an in-place transformation and is deliberately not wrapped.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilDegradationMixin {

    @WrapMethod(method = "onTake")
    private void totality$degradeInPlace(Player player, ItemStack carried, Operation<Void> original) {
        Optional<Object[]> target = ((ItemCombinerMenuAccessor) this).totality$getAccess()
                .evaluate((level, pos) -> level instanceof ServerLevel serverLevel ? new Object[]{serverLevel, pos} : null);
        if (target.isEmpty()) {
            original.call(player, carried);
            return;
        }
        BlockDamageStorage.transaction((ServerLevel) target.get()[0], (BlockPos) target.get()[1], () -> original.call(player, carried));
    }
}

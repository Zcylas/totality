package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import zcylas.totality.api.mining.BlockDamageStorage;

/**
 * Block Breaking V2 Pass 4A: a Dispenser waxing copper with Honeycomb runs inside {@link BlockDamageStorage#transaction}.
 * In 26.2 this is the anonymous {@code OptionalDispenseItemBehavior} registered for {@code Items.HONEYCOMB} in
 * {@code DispenseItemBehavior.bootStrap} ({@code DispenseItemBehavior$12}, the only one referencing HoneycombItem); its
 * {@code execute} mutates only the block in front of the dispenser and is wrapped whole, unchanged.
 */
@Mixin(targets = "net.minecraft.core.dispenser.DispenseItemBehavior$12")
public abstract class DispenserHoneycombTransformationMixin {

    @WrapMethod(method = "execute")
    private ItemStack totality$waxInPlace(BlockSource source, ItemStack dispensed, Operation<ItemStack> original) {
        ServerLevel level = source.level();
        return BlockDamageStorage.transaction(level, source.pos().relative(source.state().getValue(DispenserBlock.FACING)),
                () -> original.call(source, dispensed));
    }
}

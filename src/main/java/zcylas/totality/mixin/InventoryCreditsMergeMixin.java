package zcylas.totality.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zcylas.totality.item.tools.CreditsItem;

/**
 * totality:credits stacks don't stack normally (stacksTo(1), amount lives in a
 * component) — instead their amounts merge into an existing Credits stack on pickup,
 * capped at {@link CreditsItem#MAX_PER_STACK}. Mutates {@code stack} in place and lets
 * vanilla's add() continue for any leftover, so there's no risk of re-entrant recursion.
 */
@Mixin(Inventory.class)
public abstract class InventoryCreditsMergeMixin {

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void totality$mergeCreditsOnPickup(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || !(stack.getItem() instanceof CreditsItem)) return;

        Inventory self = (Inventory) (Object) this;
        long incoming = CreditsItem.getAmount(stack);
        if (incoming <= 0) return;

        for (int i = 0; i < self.getContainerSize() && incoming > 0; i++) {
            ItemStack existing = self.getItem(i);
            if (existing.isEmpty() || existing == stack || !(existing.getItem() instanceof CreditsItem)) continue;

            long existingAmount = CreditsItem.getAmount(existing);
            long room = CreditsItem.MAX_PER_STACK - existingAmount;
            if (room <= 0) continue;

            long transfer = Math.min(room, incoming);
            CreditsItem.setAmount(existing, existingAmount + transfer);
            incoming -= transfer;
        }

        if (incoming <= 0) {
            stack.setCount(0);
            cir.setReturnValue(true);
            return;
        }

        CreditsItem.setAmount(stack, incoming);
        // Not cancelled: vanilla's add() proceeds with the (possibly reduced) remainder.
    }
}

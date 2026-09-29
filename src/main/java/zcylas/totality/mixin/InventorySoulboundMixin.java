package zcylas.totality.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.api.item.Soulbound;

import java.util.HashMap;
import java.util.Map;

/**
 * Soulbound stacks in the vanilla inventory (main, armor and offhand) are not dropped by the death drop: they are set
 * aside for the drop and put back in the same slots of the dead player's inventory, from where
 * {@link Soulbound#register() Soulbound's respawn handler} moves them to the respawned player. Vanishing items were
 * already destroyed by vanilla before this runs.
 */
@Mixin(Inventory.class)
public abstract class InventorySoulboundMixin {

    @Unique
    private final Map<Integer, ItemStack> totality$soulbound = new HashMap<>();

    @Inject(method = "dropAll", at = @At("HEAD"))
    private void totality$setAsideSoulbound(CallbackInfo ci) {
        Inventory inventory = (Inventory) (Object) this;
        totality$soulbound.clear();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (Soulbound.isSoulbound(stack)) {
                totality$soulbound.put(i, stack);
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    @Inject(method = "dropAll", at = @At("TAIL"))
    private void totality$restoreSoulbound(CallbackInfo ci) {
        Inventory inventory = (Inventory) (Object) this;
        totality$soulbound.forEach(inventory::setItem);
        totality$soulbound.clear();
    }
}

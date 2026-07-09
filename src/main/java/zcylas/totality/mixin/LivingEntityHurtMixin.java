package zcylas.totality.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {

    // Sword parry uses ItemUseAnimation.BLOCK for the arm pose, which makes vanilla's
    // isBlocking() return true. That causes LivingEntity.hurtServer() to call
    // hurtCurrentlyUsedShield() + stopUsingItem() on every hit, resetting the animation.
    // Returning false here for non-shield items prevents that interference.
    // Arm pose visuals use getUseAnimation(), not isBlocking(), so nothing breaks visually.
    @Inject(method = "isBlocking", at = @At("RETURN"), cancellable = true)
    private void totality$swordParryNotBlocking(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (!((Object)this instanceof Player player)) return;
        ItemStack used = player.getUseItem();
        if (used.isEmpty() || used.getItem() instanceof ShieldItem) return;
        cir.setReturnValue(false);
    }
}

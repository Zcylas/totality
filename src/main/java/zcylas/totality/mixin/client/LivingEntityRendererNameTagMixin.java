package zcylas.totality.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zcylas.totality.client.hologram.TargetNameplate;

/**
 * One nameplate per entity: while Totality's holographic plate is on an entity, its vanilla nametag is not drawn.
 * Every other case (other entities, all players, sneaking, team visibility) falls through to vanilla unchanged.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererNameTagMixin {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void totality$customPlateReplacesVanillaName(LivingEntity entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        if (TargetNameplate.replacesVanillaName(entity)) cir.setReturnValue(false);
    }
}

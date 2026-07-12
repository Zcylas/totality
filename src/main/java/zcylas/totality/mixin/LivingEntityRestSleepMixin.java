package zcylas.totality.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zcylas.totality.api.rpg.rest.RestSessionManager;

@Mixin(LivingEntity.class)
public abstract class LivingEntityRestSleepMixin {

    // LivingEntity#tick() runs its own separate sleep-validity check, independent of Player#tick()'s
    // day/night one (see PlayerRestSleepMixin): if canInteractWithLevel() && !checkBedExists(), it
    // force-wakes via stopSleeping() — i.e. the block at the sleeping position must still actually
    // BE a bed. Our outdoor Nap/Long Rest anchor is just the player's own feet position, not a real
    // bed, so this fired on literally the first tick every time. Suppressed the same way as the
    // day/night check: only while our own Rest session is deliberately keeping this lying-down.
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;stopSleeping()V"))
    private void totality$suppressBedValidityWakeDuringRest(LivingEntity entity) {
        if (entity instanceof Player player && RestSessionManager.isSuppressingAutoWake(player.getUUID())) return;
        entity.stopSleeping();
    }
}

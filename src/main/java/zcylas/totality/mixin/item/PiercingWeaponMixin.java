package zcylas.totality.mixin.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.PiercingWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.api.stamina.PlayerStaminaManager;
import zcylas.totality.networking.stamina.StaminaServerTick;

@Mixin(PiercingWeapon.class)
public class PiercingWeaponMixin {

    /**
     * Injects right before makeHitSound, which only runs when hitSomething == true.
     * This means stamina is only drained when the jab actually connects,
     * consistent with swords/axes via AttackEntityCallback.
     */
    @Inject(
            method = "attack",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/component/PiercingWeapon;makeHitSound(Lnet/minecraft/world/entity/Entity;)V"
            )
    )
    private void onSpearJab(LivingEntity attacker, EquipmentSlot hand, CallbackInfo ci) {
        if (attacker instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative()) {
            PlayerStaminaManager.removeStamina(serverPlayer, 10);
            StaminaServerTick.syncStamina(serverPlayer);
        }
    }
}
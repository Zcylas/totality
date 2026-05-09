package zcylas.totality.mixin.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.KineticWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.networking.stamina.StaminaServerTick;

@Mixin(KineticWeapon.class)
public class KineticWeaponMixin {

    /**
     * Injects after a successful spear hit (when affected == true).
     * Fires for both jab and charge attacks since both go through damageEntities().
     * Sprint drain already adds extra cost to charge attacks naturally.
     */
    @Inject(
            method = "damageEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;broadcastEntityEvent(Lnet/minecraft/world/entity/Entity;B)V"
            )
    )
    private void onSpearHit(ItemStack stack, int ticksRemaining, LivingEntity livingEntity,
                            EquipmentSlot equipmentSlot, CallbackInfo ci) {
        if (livingEntity instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative()) {
            PlayerStaminaManager.removeStamina(serverPlayer, 20);
            StaminaServerTick.syncStamina(serverPlayer);
        }
    }
}
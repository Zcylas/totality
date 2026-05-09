package zcylas.totality.mixin.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.networking.stamina.StaminaServerTick;

@Mixin(TridentItem.class)
public class TridentItemMixin {

    /**
     * Injects right before the trident projectile is spawned.
     * Only fires when a real throw occurs (not riptide, not too soon, not breaking).
     * Drains 15 stamina — tridents are heavy throwing weapons.
     */
    @Inject(
            method = "releaseUsing",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileFromRotation(Lnet/minecraft/world/entity/projectile/Projectile$ProjectileFactory;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;FFF)Lnet/minecraft/world/entity/projectile/Projectile;"
            ),
            cancellable = false
    )
    private void onTridentThrow(ItemStack itemStack, Level level, LivingEntity entity,
                                int remainingTime, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative()) {
            PlayerStaminaManager.removeStamina(serverPlayer, 15);
            StaminaServerTick.syncStamina(serverPlayer);
        }
    }


}
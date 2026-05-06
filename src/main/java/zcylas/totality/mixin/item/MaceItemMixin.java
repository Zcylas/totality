package zcylas.totality.mixin.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.api.stamina.PlayerStaminaManager;
import zcylas.totality.networking.stamina.StaminaServerTick;

@Mixin(MaceItem.class)
public class MaceItemMixin {

    /**
     * Injects at the HEAD of hurtEnemy, but only drains stamina when
     * canSmashAttack is true — i.e. the player is falling (fall distance > 1.5).
     * Normal mace attacks are handled by AttackEntityCallback at 10 stamina.
     * Smash attack costs 20 stamina — equivalent to a power attack.
     */
    @Inject(
            method = "hurtEnemy",
            at = @At("HEAD")
    )
    private void onMaceSmash(ItemStack itemStack, LivingEntity mob,
                             LivingEntity attacker, CallbackInfo ci) {
        if (attacker instanceof ServerPlayer serverPlayer
                && !serverPlayer.isCreative()
                && MaceItem.canSmashAttack(attacker)) {
            PlayerStaminaManager.removeStamina(serverPlayer, 20);
            StaminaServerTick.syncStamina(serverPlayer);
        }
    }
}
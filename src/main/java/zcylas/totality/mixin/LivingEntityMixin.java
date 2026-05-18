package zcylas.totality.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zcylas.totality.api.rpg.combat.CombatStateManager;
import zcylas.totality.init.ModEffects;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    @ModifyVariable(
            method = "hurtServer",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private float totality$modifyDamageForHex(float amount,
                                              ServerLevel level, DamageSource source) {
        LivingEntity entity = (LivingEntity)(Object)this;
        if (entity.hasEffect(ModEffects.HEX)) {
            int amplifier = entity.getEffect(ModEffects.HEX).getAmplifier();
            return amount * (1.0f + 0.25f * (amplifier + 1));
        }
        return amount;
    }

    @Inject(method = "hurtServer", at = @At("HEAD"))
    private void totality$onHurtServer(ServerLevel level, DamageSource source, float amount,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (!((LivingEntity)(Object)this instanceof ServerPlayer player)) return;
        if (amount <= 0) return;
        CombatStateManager.onDamage(player);
    }
}
package zcylas.totality.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
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
}
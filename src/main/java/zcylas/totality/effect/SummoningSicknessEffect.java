package zcylas.totality.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class SummoningSicknessEffect extends MobEffect {
    public static final SummoningSicknessEffect INSTACE = new SummoningSicknessEffect();

    public SummoningSicknessEffect() {
        super(MobEffectCategory.HARMFUL, 0x33cc33);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        return true;
    }

    @Override
    public void onEffectAdded(LivingEntity entity, int amplifier) {
        // Apply nausea for 2 seconds on application
        entity.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 2 * 20, 0));
    }
}
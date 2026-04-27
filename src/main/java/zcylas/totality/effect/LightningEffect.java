package zcylas.totality.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class LightningEffect extends MobEffect {

    public static final LightningEffect INSTANCE = new LightningEffect();

    private LightningEffect() {
        super(MobEffectCategory.HARMFUL, 0x1E90FF); // electric blue
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false; // no per-tick damage, just a debuff marker
    }
}

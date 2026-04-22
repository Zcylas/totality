package zcylas.totality.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class GlideEffect extends MobEffect {
    public static final GlideEffect INSTANCE = new GlideEffect();

    private GlideEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x88CCFF);
    }
}
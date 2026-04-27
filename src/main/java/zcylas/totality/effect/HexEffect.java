package zcylas.totality.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;


public class HexEffect extends MobEffect {
    public static final HexEffect INSTANCE = new HexEffect();

    protected HexEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B008B);
    }
}

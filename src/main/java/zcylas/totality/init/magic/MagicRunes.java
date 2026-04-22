package zcylas.totality.init.magic;

import zcylas.totality.item.magic.rune.effect.*;
import zcylas.totality.item.magic.rune.effect.augment.*;
import zcylas.totality.item.magic.rune.form.ProjectileForm;
import zcylas.totality.item.magic.rune.form.SelfForm;
import zcylas.totality.item.magic.rune.form.TouchForm;

public class MagicRunes {

    public static void register() {
        RuneRegistry.register(TouchForm.INSTANCE);
        RuneRegistry.register(BreakEffect.INSTANCE);
        RuneRegistry.register(AmplifyAugment.INSTANCE);
        RuneRegistry.register(ProjectileForm.INSTANCE);
        RuneRegistry.register(SelfForm.INSTANCE);
        RuneRegistry.register(AoeAugment.INSTANCE);
        RuneRegistry.register(PickupEffect.INSTANCE);
        RuneRegistry.register(LaunchEffect.INSTANCE);
        RuneRegistry.register(IgniteEffect.INSTANCE);
        RuneRegistry.register(ExplosionEffect.INSTANCE);
        RuneRegistry.register(GlideEffect.INSTANCE);
        RuneRegistry.register(ReduceTimeAugment.INSTANCE);
        RuneRegistry.register(ExtendTimeAugment.INSTANCE);
        RuneRegistry.register(DampenAugment.INSTANCE);
        RuneRegistry.register(SensitiveAugment.INSTANCE);
        RuneRegistry.register(PierceAugment.INSTANCE);
        RuneRegistry.register(SmeltEffect.INSTANCE);
        RuneRegistry.register(OrbitEffect.INSTANCE);
    }

    private MagicRunes() {}
}
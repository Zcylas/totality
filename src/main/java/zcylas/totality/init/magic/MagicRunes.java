package zcylas.totality.init.magic;

import zcylas.totality.item.magic.rune.effect.BreakEffect;
import zcylas.totality.item.magic.rune.effect.augment.AmplifyAugment;
import zcylas.totality.item.magic.rune.form.ProjectileForm;
import zcylas.totality.item.magic.rune.form.TouchForm;

public class MagicRunes {

    public static void register() {
        RuneRegistry.register(TouchForm.INSTANCE);
        RuneRegistry.register(BreakEffect.INSTANCE);
        RuneRegistry.register(AmplifyAugment.INSTANCE);
        RuneRegistry.register(ProjectileForm.INSTANCE);
    }

    private MagicRunes() {}
}
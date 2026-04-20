package zcylas.totality.item.magic.rune.effect.augment;

import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.rune.AbstractRune;

public class AmplifyAugment extends AbstractAugmentRune {

    public static final AmplifyAugment INSTANCE = new AmplifyAugment();

    private AmplifyAugment() {
        super("amplify", "Amplify");
    }

    @Override
    public int getManaCost() {
        return 20;
    }

    @Override
    public FormulaStats.Builder applyModifiers(FormulaStats.Builder builder,
                                               AbstractRune targetRune) {
        builder.addAmplification(1);
        return builder;
    }
}
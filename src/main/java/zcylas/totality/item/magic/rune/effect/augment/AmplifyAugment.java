package zcylas.totality.item.magic.rune.effect.augment;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.grimoire.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

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

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_AMPLIFY; }

    @Override
    public int getTier() { return 1; }

    public String getDescription() { return "Increases the power of most spells."; }
}
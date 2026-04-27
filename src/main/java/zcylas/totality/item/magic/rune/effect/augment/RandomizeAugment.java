package zcylas.totality.item.magic.rune.effect.augment;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

public class RandomizeAugment extends AbstractAugmentRune {

    public static final RandomizeAugment INSTANCE = new RandomizeAugment();

    private RandomizeAugment() { super("randomize", "Randomize"); }

    @Override public int getManaCost() { return 0; }
    @Override public int getTier()     { return 1; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_RANDOMIZE; }

    @Override
    public FormulaStats.Builder applyModifiers(FormulaStats.Builder builder, AbstractRune targetRune) {
        builder.setRandomized();
        return builder;
    }

    @Override
    public String getDescription() {
        return "Randomizes the behavior of some effects. Harm will deal a random amount of damage.";
    }
}
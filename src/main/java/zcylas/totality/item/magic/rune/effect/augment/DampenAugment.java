package zcylas.totality.item.magic.rune.effect.augment;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

public class DampenAugment extends AbstractAugmentRune {

    public static final DampenAugment INSTANCE = new DampenAugment();

    private DampenAugment() {
        super("dampen", "Dampen");
    }

    @Override
    public int getManaCost() { return 0; }

    @Override
    public int getTier() { return 2; }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_DAMPEN; }

    @Override
    public FormulaStats.Builder applyModifiers(FormulaStats.Builder builder, AbstractRune targetRune) {
        builder.addAmplification(-1);
        return builder;
    }

    public String getDescription() { return "Decreases the power of most spells."; }
}
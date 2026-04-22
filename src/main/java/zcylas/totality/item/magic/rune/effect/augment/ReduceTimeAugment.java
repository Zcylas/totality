package zcylas.totality.item.magic.rune.effect.augment;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

public class ReduceTimeAugment extends AbstractAugmentRune {

    public static final ReduceTimeAugment INSTANCE = new ReduceTimeAugment();

    private ReduceTimeAugment() {
        super("reduce_time", "Reduce Time");
    }

    @Override
    public int getManaCost() { return 15; }

    @Override
    public int getTier() { return 2; }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_REDUCE_TIME; }

    @Override
    public FormulaStats.Builder applyModifiers(FormulaStats.Builder builder, AbstractRune targetRune) {
        builder.addDurationModifier(-1.0);
        return builder;
    }

    public String getDescription() { return "Decreases the duration of spells like potion effects."; }
}
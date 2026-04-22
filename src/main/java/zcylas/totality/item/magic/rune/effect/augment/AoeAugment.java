package zcylas.totality.item.magic.rune.effect.augment;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

public class AoeAugment extends AbstractAugmentRune {

    public static final AoeAugment INSTANCE = new AoeAugment();

    private AoeAugment() {
        super("aoe", "AOE");
    }

    @Override
    public int getManaCost() { return 35; }

    @Override
    public int getTier() { return 2; }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_AOE; }

    @Override
    public FormulaStats.Builder applyModifiers(FormulaStats.Builder builder,
                                               AbstractRune targetRune) {
        builder.addAoe(1.0);
        return builder;
    }

    public String getDescription() { return "Increases the area of effect of spells."; }
}
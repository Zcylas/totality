package zcylas.totality.item.magic.rune.effect.augment;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

public class FortuneAugment extends AbstractAugmentRune {

    public static final FortuneAugment INSTANCE = new FortuneAugment();

    private FortuneAugment() { super("fortune", "Luck"); }

    @Override public int getManaCost() { return 80; }
    @Override public int getTier()     { return 2; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_FORTUNE; }

    @Override
    public FormulaStats.Builder applyModifiers(FormulaStats.Builder builder, AbstractRune targetRune) {
        builder.addFortune(1);
        return builder;
    }

    @Override
    public String getDescription() {
        return "Increases drop chance from mobs killed by Harm and blocks broken by Break.";
    }
}
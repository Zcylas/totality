package zcylas.totality.item.magic.rune.effect.augment;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

public class SensitiveAugment extends AbstractAugmentRune {

    public static final SensitiveAugment INSTANCE = new SensitiveAugment();

    private SensitiveAugment() {
        super("sensitive", "Sensitive");
    }

    @Override
    public int getManaCost() { return 10; }

    @Override
    public int getTier() { return 1; }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_SENSITIVE; }

    @Override
    public FormulaStats.Builder applyModifiers(FormulaStats.Builder builder, AbstractRune targetRune) {
        builder.setSensitive();
        return builder;
    }

    @Override
    public String getDescription() {
        return "Changes targeting rules of certain effects. Smelt will only target items, not blocks.";
    }
}
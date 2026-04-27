package zcylas.totality.item.magic.rune.effect.augment;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.Set;

public class ExtractAugment extends AbstractAugmentRune {

    public static final ExtractAugment INSTANCE = new ExtractAugment();

    private ExtractAugment() { super("extract", "Extract"); }

    @Override public int getManaCost() { return 30; }
    @Override public int getTier()     { return 2; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_EXTRACT; }

    @Override
    public String getDescription() {
        return "Applies a silk-touch effect to Break. Cannot be combined with Fortune.";
    }

    @Override
    public Set<String> getCompatibleAugments() { return Set.of(); }

    @Override
    public FormulaStats.Builder applyModifiers(FormulaStats.Builder builder, AbstractRune targetRune) {
        return builder.setSilkTouch();
    }
}
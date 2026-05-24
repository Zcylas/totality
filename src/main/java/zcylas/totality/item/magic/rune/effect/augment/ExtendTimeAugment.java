package zcylas.totality.item.magic.rune.effect.augment;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.grimoire.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

public class ExtendTimeAugment extends AbstractAugmentRune {

    public static final ExtendTimeAugment INSTANCE = new ExtendTimeAugment();

    private ExtendTimeAugment() {
        super("extend_time", "Extend Time");
    }

    @Override
    public int getManaCost() { return 10; }

    @Override
    public int getTier() { return 1; }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_EXTEND_TIME; }

    @Override
    public FormulaStats.Builder applyModifiers(FormulaStats.Builder builder, AbstractRune targetRune) {
        builder.addDurationModifier(1.0);
        return builder;
    }

    public String getDescription() { return "Increases the duration of spells like potion effects."; }
}
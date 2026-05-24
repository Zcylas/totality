package zcylas.totality.item.magic.rune.effect.augment;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.grimoire.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.Set;

public class DecelerateAugment extends AbstractAugmentRune {

    public static final DecelerateAugment INSTANCE = new DecelerateAugment();

    private DecelerateAugment() { super("decelerate", "Decelerate"); }

    @Override public int getManaCost() { return 5; }
    @Override public int getTier()     { return 2; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_DECELERATE; }

    @Override
    public String getDescription() {
        return "Decreases the speed of projectile spells.";
    }

    @Override
    public Set<String> getCompatibleAugments() { return Set.of(); }

    @Override
    public FormulaStats.Builder applyModifiers(FormulaStats.Builder builder, AbstractRune targetRune) {
        return builder.addAcceleration(-0.5f);
    }
}
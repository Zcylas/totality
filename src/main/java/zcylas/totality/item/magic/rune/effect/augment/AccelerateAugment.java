package zcylas.totality.item.magic.rune.effect.augment;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.grimoire.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.Set;

public class AccelerateAugment extends AbstractAugmentRune {

    public static final AccelerateAugment INSTANCE = new AccelerateAugment();

    private AccelerateAugment() { super("accelerate", "Accelerate"); }

    @Override public int getManaCost() { return 10; }
    @Override public int getTier()     { return 2; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_ACCELERATE; }

    @Override
    public String getDescription() {
        return "Increases the speed of projectile spells.";
    }

    @Override
    public Set<String> getCompatibleAugments() { return Set.of(); }

    @Override
    public FormulaStats.Builder applyModifiers(FormulaStats.Builder builder, AbstractRune targetRune) {
        return builder.addAcceleration(1.0f);
    }
}
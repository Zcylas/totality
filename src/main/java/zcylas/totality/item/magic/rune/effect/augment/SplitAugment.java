package zcylas.totality.item.magic.rune.effect.augment;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.Set;

public class SplitAugment extends AbstractAugmentRune {

    public static final SplitAugment INSTANCE = new SplitAugment();

    private SplitAugment() { super("split", "Split"); }

    @Override public int getManaCost() { return 20; }
    @Override public int getTier()     { return 3; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_SPLIT; }

    @Override
    public String getDescription() {
        return "Increases the number of projectiles or orbiting entities spawned.";
    }

    @Override
    public Set<String> getCompatibleAugments() { return Set.of(); }

    @Override
    public FormulaStats.Builder applyModifiers(FormulaStats.Builder builder, AbstractRune targetRune) {
        return builder.addSplit(1);
    }
}
package zcylas.totality.item.magic.rune.effect.augment;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractAugmentRune;
import zcylas.totality.api.magic.rune.AbstractRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

public class PierceAugment extends AbstractAugmentRune {

    public static final PierceAugment INSTANCE = new PierceAugment();

    private PierceAugment() {
        super("pierce", "Pierce");
    }

    @Override
    public int getManaCost() { return 40; }

    @Override
    public int getTier() { return 2; }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_PIERCE; }

    @Override
    public FormulaStats.Builder applyModifiers(FormulaStats.Builder builder, AbstractRune targetRune) {
        builder.addPierce(1);
        return builder;
    }

    @Override
    public String getDescription() {
        return "Projectiles pierce through targets an additional time. Causes effects to target blocks behind the hit. Combines with AOE for depth.";
    }
}
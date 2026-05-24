package zcylas.totality.api.magic.grimoire.rune;

import zcylas.totality.api.magic.grimoire.formula.FormulaStats;

/**
 * An Augment rune — modifies the stats of the effect it follows.
 * Examples: Amplify
 */
public abstract class AbstractAugmentRune extends AbstractRune {

    public AbstractAugmentRune(String id, String name) {
        super(id, name);
    }

    @Override
    public final int getTypeIndex() {
        return 3;
    }

    /**
     * Apply this augment's modifiers to the stats builder.
     */
    public abstract FormulaStats.Builder applyModifiers(FormulaStats.Builder builder,
                                                        AbstractRune targetRune);
}
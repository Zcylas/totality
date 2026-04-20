package zcylas.totality.api.magic.formula;

import zcylas.totality.api.magic.rune.AbstractAugmentRune;

import java.util.List;

/**
 * Holds the computed stats for a formula at cast time.
 * Built from the augments that follow a Form or Effect rune.
 */
public class FormulaStats {

    private final int ampCount;

    private FormulaStats(Builder builder) {
        this.ampCount = builder.ampCount;
    }

    /**
     * How many Amplify augments are applied.
     * Used by BreakEffect to determine harvest tier.
     */
    public int getAmpCount() {
        return ampCount;
    }

    public static class Builder {
        private int ampCount = 0;

        public Builder addAmplification(int amount) {
            this.ampCount += amount;
            return this;
        }

        public Builder setAugments(List<AbstractAugmentRune> augments,
                                   zcylas.totality.api.magic.rune.AbstractRune targetRune) {
            for (AbstractAugmentRune augment : augments) {
                augment.applyModifiers(this, targetRune);
            }
            return this;
        }

        public FormulaStats build() {
            return new FormulaStats(this);
        }
    }
}
package zcylas.totality.api.magic.formula;

import zcylas.totality.api.magic.rune.AbstractAugmentRune;

import java.util.List;

/**
 * Holds the computed stats for a formula at cast time.
 * Built from the augments that follow a Form or Effect rune.
 */
public class FormulaStats {

    private final int ampCount;
    private final double aoeRadius;
    private final double durationModifier;
    private final boolean sensitive;
    private final int pierceCount;

    private FormulaStats(Builder builder) {
        this.ampCount = builder.ampCount;
        this.aoeRadius = builder.aoeRadius;
        this.durationModifier = builder.durationModifier;
        this.sensitive = builder.sensitive;
        this.pierceCount = builder.pierceCount;
    }

    public double getAoeRadius() {
        return aoeRadius;
    }

    public double getDurationModifier(){ return  durationModifier; }

    public int getPierceCount() { return pierceCount; }

    public int getAmpCount() {
        return ampCount;
    }
    public boolean isSensitive() { return sensitive; };

    public static class Builder {
        private int ampCount = 0;
        private double aoeRadius = 0;
        private double durationModifier = 0;
        private boolean sensitive = false;
        private int pierceCount = 0;

        public Builder addPierce(int amount) {
            this.pierceCount += amount;
            return this;
        }

        public Builder addAmplification(int amount) {
            this.ampCount += amount;
            return this;
        }

        public Builder addAoe(double amount) {
            this.aoeRadius += amount;
            return this;
        }

        public Builder addDurationModifier(double amount) {
            this.durationModifier += amount;
            return this;
        }

        public Builder setSensitive(){
            this.sensitive = true;
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
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
    private final int fortuneLevel;
    private final boolean randomized;
    private boolean silkTouch = false;
    private final float accelerationModifier;
    private final int splitCount;

    private FormulaStats(Builder builder) {
        this.ampCount = builder.ampCount;
        this.aoeRadius = builder.aoeRadius;
        this.durationModifier = builder.durationModifier;
        this.sensitive = builder.sensitive;
        this.pierceCount = builder.pierceCount;
        this.fortuneLevel = builder.fortuneLevel;
        this.randomized = builder.randomized;
        this.silkTouch = builder.silkTouch;
        this.accelerationModifier = builder.accelerationModifier;
        this.splitCount = builder.splitCount;
    }
    public int getSplitCount() { return splitCount; }

    public float getAccelerationModifier() { return accelerationModifier; }

    public boolean isRandomized() { return randomized; }

    public double getAoeRadius() {
        return aoeRadius;
    }

    public double getDurationModifier(){ return  durationModifier; }

    public int getPierceCount() { return pierceCount; }

    public int getAmpCount() {
        return ampCount;
    }

    public boolean isSensitive() { return sensitive; };

    public int getFortuneLevel() { return fortuneLevel; }

    public boolean isSilkTouch() { return silkTouch; }

    public static class Builder {
        private int ampCount = 0;
        private double aoeRadius = 0;
        private double durationModifier = 0;
        private boolean sensitive = false;
        private int pierceCount = 0;
        private int fortuneLevel = 0;
        private boolean randomized = false;
        private boolean silkTouch = false;
        private float accelerationModifier = 0f;
        private int splitCount = 0;

        public Builder addSplit(int amount) {
            this.splitCount += amount; return this;
        }

        public Builder addAcceleration(float amount) {
            this.accelerationModifier += amount;
            return this;
        }
        public Builder setRandomized() {
            this.randomized = true;
            return this;
        }

        public Builder setSilkTouch() { this.silkTouch = true; return this; }

        public Builder addFortune(int amount) {
            this.fortuneLevel += amount;
            return this;
        }

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
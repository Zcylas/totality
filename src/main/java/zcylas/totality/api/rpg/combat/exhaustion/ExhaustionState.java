package zcylas.totality.api.rpg.combat.exhaustion;

/**
 * Represents the current exhaustion state of a player based on their stamina level.
 *
 * NORMAL    → above 30% stamina, no penalties
 * WARNING   → below 30% stamina, out of breath sound plays once on transition
 * EXHAUSTED → at 0% stamina, all penalties active and regen halved
 */
public enum ExhaustionState {
    NORMAL,
    WARNING,
    EXHAUSTED;

    /**
     * Determines the exhaustion state from current and max stamina values.
     */
    public static ExhaustionState fromStamina(int stamina, int maxStamina) {
        if (maxStamina <= 0) return NORMAL;
        if (stamina <= 0) return EXHAUSTED;
        float pct = (float) stamina / maxStamina;
        if (pct <= 0.30f) return WARNING;
        return NORMAL;
    }
}
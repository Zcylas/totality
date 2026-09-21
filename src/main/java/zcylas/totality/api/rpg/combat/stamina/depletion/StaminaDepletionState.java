package zcylas.totality.api.rpg.combat.stamina.depletion;

/**
 * Immediate, Stamina-derived exertion/depletion tier — NOT Totality's future D&D-inspired
 * persistent, multi-source Exhaustion condition. This state is recomputed from current/max
 * Stamina alone and carries no lifecycle of its own beyond that single tick's snapshot.
 *
 * NORMAL   → above 30% Stamina, no penalties
 * WINDED   → at or below 30% Stamina but above zero — breathing feedback, reduced regen
 * DEPLETED → zero Stamina — movement/attack penalties, further-reduced regen
 */
public enum StaminaDepletionState {
    NORMAL,
    WINDED,
    DEPLETED;

    /** Inclusive boundary: Stamina at exactly 30% of max counts as WINDED, not NORMAL. */
    public static final float WINDED_THRESHOLD = 0.30f;

    /**
     * Determines the depletion state from current and max Stamina values.
     */
    public static StaminaDepletionState fromStamina(int stamina, int maxStamina) {
        if (maxStamina <= 0) return NORMAL;
        if (stamina <= 0) return DEPLETED;
        float pct = (float) stamina / maxStamina;
        if (pct <= WINDED_THRESHOLD) return WINDED;
        return NORMAL;
    }
}

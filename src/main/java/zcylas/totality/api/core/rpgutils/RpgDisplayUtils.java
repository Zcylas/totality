package zcylas.totality.api.core.rpgutils;

/**
 * Utility class for converting between vanilla internal values and
 * Totality's display values.
 *
 * Convention: all Totality UI displays HP as vanillaHp * 5.
 * Internally Minecraft always uses the 0-20 scale — this is purely cosmetic.
 *
 * Used by:
 *   - Player HUD (health bar)
 *   - Mob health bars
 *   - Boss bars
 *   - Damage numbers (when added)
 *   - Potion tooltips
 */
public final class RpgDisplayUtils {

    /** Multiplier between vanilla HP and display HP. */
    public static final int HP_DISPLAY_MULTIPLIER = 5;

    private RpgDisplayUtils() {}

    // ── HP conversion ─────────────────────────────────────────────────────────

    /**
     * Converts vanilla HP to display HP.
     * e.g. 20 vanilla → 100 display, 10 vanilla → 50 display.
     */
    public static int toDisplayHp(float vanillaHp) {
        return Math.round(vanillaHp * HP_DISPLAY_MULTIPLIER);
    }

    /**
     * Converts display HP back to vanilla HP.
     * e.g. 100 display → 20 vanilla, 50 display → 10 vanilla.
     */
    public static float toVanillaHp(int displayHp) {
        return (float) displayHp / HP_DISPLAY_MULTIPLIER;
    }

    /**
     * Converts a CON modifier point to vanilla MAX_HEALTH units.
     * Each CON modifier point = +10 display HP = +2 vanilla HP.
     * e.g. CON modifier +1 → +2 vanilla HP → displayed as +10 HP.
     */
    public static double conModifierToVanillaHp(int conModifier) {
        return conModifier * 2.0;
    }

    // ── Stamina / Mana — already on 0-100 scale, no conversion needed ─────────

    /**
     * Returns the max stamina bonus from END modifier.
     * Each END modifier point = +5 stamina.
     * At END 10 (modifier 0) → bonus 0 → total = 100 (base).
     */
    public static int endModifierToStaminaBonus(int endModifier) {
        return endModifier * 5;
    }

    /**
     * Returns the max mana bonus from INT modifier.
     * Each INT modifier point = +5 mana.
     * At INT 10 (modifier 0) → bonus 0 → total = 100 (base).
     */
    public static int intModifierToManaBonus(int intModifier) {
        return intModifier * 5;
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    /**
     * Formats HP for display — e.g. "85 / 100".
     */
    public static String formatHp(float currentVanilla, float maxVanilla) {
        return toDisplayHp(currentVanilla) + " / " + toDisplayHp(maxVanilla);
    }

    /**
     * Formats a stat value with its modifier — e.g. "12 (+1)".
     */
    public static String formatScoreWithModifier(int score) {
        int mod = zcylas.totality.api.rpg.stats.AbilityScore.getModifier(score);
        String modStr = mod >= 0 ? "+" + mod : String.valueOf(mod);
        return score + " (" + modStr + ")";
    }
}
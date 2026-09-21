package zcylas.totality.api.core.rpgutils;

import zcylas.totality.api.rpg.resources.external.HealthResourceAdapter;
import zcylas.totality.api.rpg.resources.presentation.ResourceDisplayConversion;

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
 *
 * {@link #toDisplayHp}/{@link #toVanillaHp} now delegate to the shared, registered
 * {@code totality:health} {@link ResourceDisplayConversion} (see
 * {@code Context/Audit/TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §19.2) instead of hand-rolling
 * their own multiplication, so there remains exactly one authoritative ×5 Health conversion. Kept
 * as public static methods (not deleted) because callers throughout the codebase still depend on
 * them (see the readiness audit's migration matrix).
 */
public final class RpgDisplayUtils {

    /**
     * Multiplier between vanilla HP and display HP. Correction pass: derived from
     * {@link ResourceDisplayConversion#HEALTH_FOOD} at class-init time rather than maintained as
     * an independent literal — {@code HEALTH_FOOD} is the single authoritative declaration of
     * Health's {@code 5/1} ratio. This field assumes {@code HEALTH_FOOD} is a whole-number
     * multiplier (denominator {@code 1}); {@link #deriveMultiplier()} fails loudly rather than
     * silently if that ever stops being true, instead of this field quietly drifting from it.
     */
    public static final int HP_DISPLAY_MULTIPLIER = deriveMultiplier();

    private static int deriveMultiplier() {
        ResourceDisplayConversion conversion = ResourceDisplayConversion.HEALTH_FOOD;
        if (conversion.denominator() != 1) {
            throw new IllegalStateException(
                    "HP_DISPLAY_MULTIPLIER assumes a whole-number multiplier (denominator=1), but "
                            + "ResourceDisplayConversion.HEALTH_FOOD is "
                            + conversion.numerator() + "/" + conversion.denominator());
        }
        return Math.toIntExact(conversion.numerator());
    }

    private RpgDisplayUtils() {}

    // ── HP conversion ─────────────────────────────────────────────────────────

    /**
     * Converts vanilla HP to display HP.
     * e.g. 20 vanilla → 100 display, 10 vanilla → 50 display.
     */
    public static int toDisplayHp(float vanillaHp) {
        long units = HealthResourceAdapter.toUnits(vanillaHp, HealthResourceAdapter.UNIT_SCALE);
        long display = ResourceDisplayConversion.HEALTH_FOOD.convertUnitsToDisplay(units, HealthResourceAdapter.UNIT_SCALE);
        return Math.toIntExact(display);
    }

    /**
     * Converts display HP back to vanilla HP.
     * e.g. 100 display → 20 vanilla, 50 display → 10 vanilla.
     * Derived from {@link ResourceDisplayConversion#HEALTH_FOOD}'s own tested inverse
     * ({@link ResourceDisplayConversion#invertToMechanical(double)}), not a second division by
     * {@link #HP_DISPLAY_MULTIPLIER} — one authoritative conversion object backs both directions.
     */
    public static float toVanillaHp(int displayHp) {
        return (float) ResourceDisplayConversion.HEALTH_FOOD.invertToMechanical(displayHp);
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
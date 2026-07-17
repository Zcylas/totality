package zcylas.totality.api.core.rpgutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the current, already-correct HP display behavior ahead of any Resource API migration
 * (readiness audit §2.1/§3.8). Also pins two dead/contradictory bonus formulas that must NOT be
 * treated as live during a future migration — see {@code PlayerStatsCharacterizationTest} for the
 * formulas that actually are live.
 */
class RpgDisplayUtilsCharacterizationTest {

    @Test
    void hpDisplayMultiplierIsExactlyFive() {
        assertEquals(5, RpgDisplayUtils.HP_DISPLAY_MULTIPLIER);
    }

    @Test
    void toDisplayHpAppliesExactlyFiveX() {
        assertEquals(100, RpgDisplayUtils.toDisplayHp(20f));
        assertEquals(5, RpgDisplayUtils.toDisplayHp(1f));
        assertEquals(0, RpgDisplayUtils.toDisplayHp(0f));
    }

    @Test
    void toVanillaHpIsTheExactInverse() {
        assertEquals(20f, RpgDisplayUtils.toVanillaHp(100));
        assertEquals(1f, RpgDisplayUtils.toVanillaHp(5));
    }

    @Test
    void conModifierToVanillaHpIsExactlyTimesTwo() {
        // Live path: StatAttributeApplier.applyConHp adds this as a vanilla MAX_HEALTH modifier.
        assertEquals(2.0, RpgDisplayUtils.conModifierToVanillaHp(1));
        assertEquals(-2.0, RpgDisplayUtils.conModifierToVanillaHp(-1));
        assertEquals(0.0, RpgDisplayUtils.conModifierToVanillaHp(0));
    }

    @Test
    void formatHpUsesTheDisplayMultiplierOnBothValues() {
        assertEquals("100 / 100", RpgDisplayUtils.formatHp(20f, 20f));
        assertEquals("50 / 100", RpgDisplayUtils.formatHp(10f, 20f));
    }

    /**
     * {@code endModifierToStaminaBonus} and {@code intModifierToManaBonus} are confirmed dead
     * (no caller anywhere in production code) AND contradict the live formulas in
     * {@code PlayerStats.getMaxStaminaBonus}/{@code getMaxManaBonus}, which use *10, not *5. This
     * test pins their current (wrong, unused) values so migration work doesn't accidentally treat
     * them as authoritative — see readiness audit §5 "Duplicated/contradictory formulas".
     */
    @Test
    void deadStaminaAndManaBonusFormulasUseTimesFiveNotTheLiveTimesTen() {
        assertEquals(5, RpgDisplayUtils.endModifierToStaminaBonus(1));
        assertEquals(5, RpgDisplayUtils.intModifierToManaBonus(1));
        // NOTE: "dead" here means confirmed-by-grep zero call sites anywhere in production source
        // at the time of writing (readiness audit §5) — not something this reflection-only test
        // can itself verify. Re-run that grep before migrating Stamina/Mana in a later phase.
    }
}

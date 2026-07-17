package zcylas.totality.api.rpg.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the live derived-stat formulas actually consumed by {@code PlayerStaminaManager}/
 * {@code PlayerManaManager} today (readiness audit §2.2/§2.3), ahead of a future Resource API
 * migration that must preserve them exactly (canonical §24.1: "behavior-neutral" first phase).
 */
class PlayerStatsCharacterizationTest {

    @Test
    void baseScoreIsTenAndModifierIsZeroByDefault() {
        PlayerStats stats = new PlayerStats();
        assertEquals(10, stats.getScore(AbilityScore.END));
        assertEquals(0, stats.getModifier(AbilityScore.END));
        assertEquals(0, stats.getMaxStaminaBonus());
        assertEquals(0, stats.getMaxManaBonus());
    }

    @Test
    void modifierFormulaIsFloorDivScoreMinusTenOverTwo() {
        assertEquals(0, AbilityScore.getModifier(10));
        assertEquals(0, AbilityScore.getModifier(11));
        assertEquals(1, AbilityScore.getModifier(12));
        assertEquals(-1, AbilityScore.getModifier(8));
        assertEquals(-1, AbilityScore.getModifier(9));
    }

    @Test
    void maxStaminaBonusIsEndModifierTimesTen() {
        PlayerStats stats = new PlayerStats();
        stats.setOriginBonus(new AbilityScoreBonus(0, 0, 0, 4, 0, 0, 0, 0)); // END 10 -> 14, modifier +2

        assertEquals(2, stats.getModifier(AbilityScore.END));
        assertEquals(20, stats.getMaxStaminaBonus(), "live formula is END modifier * 10, not * 5");
    }

    @Test
    void maxManaBonusIsIntModifierTimesTen() {
        PlayerStats stats = new PlayerStats();
        stats.setOriginBonus(new AbilityScoreBonus(0, 0, 0, 0, 4, 0, 0, 0)); // INT 10 -> 14, modifier +2

        assertEquals(2, stats.getModifier(AbilityScore.INT));
        assertEquals(20, stats.getMaxManaBonus(), "live formula is INT modifier * 10, not * 5");
    }

    /**
     * {@code getMaxHpBonus()} is confirmed unused anywhere in production source (readiness audit
     * addendum): {@code StatAttributeApplier.applyConHp} independently computes
     * {@code RpgDisplayUtils.conModifierToVanillaHp(conModifier) = conModifier * 2.0} vanilla HP
     * units and applies that directly as a vanilla attribute modifier — it never calls this
     * method. This test pins the dead method's own (differently-scaled) value so it is not
     * mistaken for the live HP path during a future migration.
     */
    @Test
    void maxHpBonusFormulaExistsButIsNotTheLiveHpPath() {
        PlayerStats stats = new PlayerStats();
        stats.setOriginBonus(new AbilityScoreBonus(0, 0, 4, 0, 0, 0, 0, 0)); // CON 10 -> 14, modifier +2

        assertEquals(2, stats.getModifier(AbilityScore.CON));
        assertEquals(10, stats.getMaxHpBonus(),
                "getMaxHpBonus() returns CON modifier * 5 — a different, unused formula from the "
                        + "live StatAttributeApplier path (CON modifier * 2.0 vanilla HP units)");
    }

    @Test
    void spendingAttributePointsAffectsFinalScoreAndDerivedBonuses() {
        PlayerStats stats = new PlayerStats();
        stats.setUnspentAttributePointsDirectly(2);

        assertTrue(stats.spendAttributePoint(AbilityScore.END));
        assertTrue(stats.spendAttributePoint(AbilityScore.END));
        assertFalse(stats.spendAttributePoint(AbilityScore.END), "no points left to spend");

        assertEquals(12, stats.getScore(AbilityScore.END));
        assertEquals(1, stats.getModifier(AbilityScore.END));
        assertEquals(10, stats.getMaxStaminaBonus());
    }
}

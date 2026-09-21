package zcylas.totality.api.core.rpgutils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the current, already-correct HP display behavior ahead of any Resource API migration
 * (readiness audit §2.1/§3.8). Also pins two dead/contradictory bonus formulas that must NOT be
 * treated as live during a future migration — see {@code PlayerStatsCharacterizationTest} for the
 * formulas that actually are live.
 *
 * Phase 2A refactored {@link RpgDisplayUtils#toDisplayHp} to delegate to the shared, registered
 * {@code totality:health} {@code ResourceDisplayConversion} (fixed-point, unit scale 1000) instead
 * of the old raw {@code Math.round(vanillaHp * 5)}.
 *
 * <p><b>Correction pass — corrected compatibility claim.</b> An earlier version of this class
 * contained a test named {@code toDisplayHpMatchesTheOldRawFormulaExhaustively}, sampling at a
 * coarse 0.1 granularity and asserting universal equivalence with the old formula. That name and
 * claim were both wrong: exhaustively checking every finite {@code float} was never actually
 * performed, and — more importantly — a real, mathematically-inherent "double rounding" divergence
 * was found once finer granularity was actually checked (0.0001 steps, 2,000,001 samples): the new
 * two-stage conversion (round to the nearest fixed-point unit, <em>then</em> round the display
 * value) can differ by exactly {@code 1} from the old single-stage formula (round the display value
 * directly) for {@code float} inputs that fall within roughly {@code 0.0005} of a value ending in
 * {@code .0}, {@code .2}, {@code .4}, {@code .6}, or {@code .8} from below (e.g. {@code 0.0995}
 * through {@code 0.0999}, one boundary of many) — about {@code 0.22%} of the sampled range.
 * {@link #twoStageFixedPointRoundingCanDivergeFromTheOldSingleStageFormulaNearBoundaryValues()}
 * documents this honestly with a concrete example instead of hiding it behind a false
 * "exhaustive"/"universal" claim. This is an accepted, inherent consequence of the deliberate
 * switch to fixed-point authoritative Health storage (the Phase 2A task's own explicit
 * requirement) — not a defect to "fix" by abandoning the fixed-point architecture.
 * {@link #toDisplayHpMatchesTheOldRawFormulaAtOrdinaryGameplayGranularity()} demonstrates ordinary
 * gameplay-range compatibility (coarse 0.1 steps, matching every value this game actually produces
 * via whole/half Health changes) — a real but deliberately narrower claim than "exhaustive".
 */
class RpgDisplayUtilsCharacterizationTest {

    @Test
    void toDisplayHpMatchesTheOldRawFormulaAtOrdinaryGameplayGranularity() {
        // Ordinary gameplay-range compatibility, NOT a claim of universal/exhaustive equivalence —
        // see the class Javadoc's "Correction pass" note. Old (pre-Phase-2A) formula, reproduced
        // verbatim here as the behavior baseline, not imported from production code (which no
        // longer contains it). Each sample is computed fresh via division (tenths / 10f), not by
        // repeatedly accumulating `+= 0.1f` — the latter drifts (e.g. lands on 2.4999998 instead
        // of 2.5 by the 25th addition) and would then be asserting equivalence at a float value no
        // real Health change ever actually produces.
        for (int tenths = 0; tenths <= 2000; tenths++) {
            float sample = tenths / 10f;
            int oldResult = Math.round(sample * RpgDisplayUtils.HP_DISPLAY_MULTIPLIER);
            int newResult = RpgDisplayUtils.toDisplayHp(sample);
            assertEquals(oldResult, newResult,
                    () -> "toDisplayHp(" + sample + ") diverged from the pre-refactor formula");
        }
    }

    @Test
    void twoStageFixedPointRoundingCanDivergeFromTheOldSingleStageFormulaNearBoundaryValues() {
        // Concrete, verified example (found via a 2,000,001-sample sweep at 0.0001 granularity,
        // not asserted from theory alone): hp=0.0996 is just below the 0.1 boundary.
        // Old single-stage formula: Math.round(0.0996 * 5) = Math.round(0.498) = 0.
        // New two-stage formula: round-to-unit first -> round(0.0996 * 1000) = 100 units exactly
        // (0.0996 rounds UP to the nearest whole unit at scale 1000), then round(100 * 5 / 1000)
        // = round(0.5) = 1. The intermediate rounding step crossing a unit boundary is what
        // produces the display-level divergence; this is "double rounding", a normal and accepted
        // property of any two-stage fixed-point pipeline, not a bug in either stage individually.
        float boundaryValue = 0.0996f;
        int oldFormula = Math.round(boundaryValue * RpgDisplayUtils.HP_DISPLAY_MULTIPLIER);
        int newFormula = RpgDisplayUtils.toDisplayHp(boundaryValue);

        assertEquals(0, oldFormula, "sanity-check on the old formula's own behavior at this value");
        assertEquals(1, newFormula, "the new fixed-point formula's correct, documented behavior");
        assertNotEquals(oldFormula, newFormula,
                "this test exists specifically to document that these two values legitimately differ");
    }

    @Test
    void auditedCurrentHealthSamplesDoNotFallInADivergenceZones() {
        // Correction-pass note: this test previously claimed "no currently-used gameplay Health
        // value falls in a divergence zone" — an overclaim. What is actually established here is
        // narrower: the specific values below (every Health delta this codebase's source was
        // audited as producing today — readiness audit §2.1/§3.8 — whole numbers, half-points, and
        // ConditionServerTick's heal(0.2f)) do not land in a known double-rounding divergence zone.
        // This is evidence about these particular audited samples, not a proof about every float a
        // future or unaudited code path might produce. An unusual resulting float landing within
        // ~0.0005 of a boundary from below (see twoStageFixedPointRoundingCanDivergeFromTheOldSingleStageFormulaNearBoundaryValues())
        // may legitimately display one point differently — that is accepted, documented behavior,
        // not a regression; authoritative mechanical Health itself is never affected, and the
        // accepted fixed-point scale remains 1000.
        float[] auditedCurrentHealthSamples = {0f, 0.2f, 0.5f, 1f, 1.5f, 2f, 10f, 10.5f, 13.7f, 20f, 24f, 40f};
        for (float hp : auditedCurrentHealthSamples) {
            int oldFormula = Math.round(hp * RpgDisplayUtils.HP_DISPLAY_MULTIPLIER);
            int newFormula = RpgDisplayUtils.toDisplayHp(hp);
            assertEquals(oldFormula, newFormula,
                    () -> "an audited current-gameplay Health sample must not hit a double-rounding divergence: hp=" + hp);
        }
    }

    @Test
    void toDisplayHpHandlesFractionalHealthPrecisely() {
        // 0.2 vanilla Health -> displayed 1 (Phase 2A task's explicit fixed-point requirement).
        assertEquals(1, RpgDisplayUtils.toDisplayHp(0.2f));
        // Half-point Health round-trips deterministically.
        assertEquals(53, RpgDisplayUtils.toDisplayHp(10.5f));
        assertEquals(50, RpgDisplayUtils.toDisplayHp(10f));
    }

    @Test
    void hpDisplayMultiplierIsExactlyFive() {
        assertEquals(5, RpgDisplayUtils.HP_DISPLAY_MULTIPLIER);
    }

    @Test
    void hpDisplayMultiplierCannotDriftFromHealthFood() {
        // Correction pass: HP_DISPLAY_MULTIPLIER is now derived from
        // ResourceDisplayConversion.HEALTH_FOOD at class-init time rather than an independently
        // maintained literal — this test proves the two can never disagree, by construction.
        assertEquals(zcylas.totality.api.rpg.resources.presentation.ResourceDisplayConversion.HEALTH_FOOD.numerator(),
                RpgDisplayUtils.HP_DISPLAY_MULTIPLIER);
        assertEquals(1, zcylas.totality.api.rpg.resources.presentation.ResourceDisplayConversion.HEALTH_FOOD.denominator());
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
        assertEquals(10f, RpgDisplayUtils.toVanillaHp(50));
        assertEquals(1f, RpgDisplayUtils.toVanillaHp(5));
    }

    @Test
    void toVanillaHpCannotDriftFromHealthFoodsInverse() {
        // Correction pass: toVanillaHp now delegates to ResourceDisplayConversion.HEALTH_FOOD's
        // own tested invertToMechanical, not a second, independently maintained division.
        for (int displayHp = 0; displayHp <= 200; displayHp += 5) {
            float expected = (float) zcylas.totality.api.rpg.resources.presentation.ResourceDisplayConversion.HEALTH_FOOD
                    .invertToMechanical(displayHp);
            assertEquals(expected, RpgDisplayUtils.toVanillaHp(displayHp));
        }
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

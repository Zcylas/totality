package zcylas.totality.client.renderer.hud;

import org.junit.jupiter.api.Test;

import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real, executing tests for {@link HudValueFormatter} (the contained HUD cleanup pass's embedded
 * current/max value formatter — see {@code TOTALITY_SMALL_HUD_CLEANUP_IMPLEMENTATION_REPORT.md}).
 * Pure — no Minecraft/{@code Font} dependency, since width measurement is injected via a
 * {@code ToIntFunction<String>} rather than requiring a live {@code Font}.
 */
class HudValueFormatterTest {

    /** A simple, deterministic synthetic width function — 6 "pixels" per character — used
     *  wherever a test only needs *some* predictable measuring function, not vanilla's real
     *  font metrics. */
    private static final ToIntFunction<String> SIX_PX_PER_CHAR = s -> s.length() * 6;

    // ── 14-16: spaced form for values that comfortably fit ───────────────────────────────────

    // 14. 100 / 100 fits and uses spaced form.
    @Test
    void oneHundredOverOneHundredFitsAndUsesSpacedForm() {
        assertEquals("100 / 100", HudValueFormatter.display(100, 100, 84, SIX_PX_PER_CHAR));
    }

    // 15. 110 / 110 fits and uses spaced form.
    @Test
    void oneHundredTenOverOneHundredTenFitsAndUsesSpacedForm() {
        assertEquals("110 / 110", HudValueFormatter.display(110, 110, 84, SIX_PX_PER_CHAR));
    }

    // 16. 999 / 999 behaves correctly.
    @Test
    void nineHundredNinetyNineOverNineHundredNinetyNineBehavesCorrectly() {
        assertEquals("999 / 999", HudValueFormatter.display(999, 999, 84, SIX_PX_PER_CHAR));
    }

    // ── 17-19: k-scale abbreviation ──────────────────────────────────────────────────────────

    // 17. 1000 formats as 1k.
    @Test
    void oneThousandFormatsAsOneK() {
        assertEquals("1k", HudValueFormatter.abbreviate(1000));
    }

    // 18. 1500 formats as 1.5k.
    @Test
    void fifteenHundredFormatsAsOnePointFiveK() {
        assertEquals("1.5k", HudValueFormatter.abbreviate(1500));
    }

    // 19. 10000 formats as 10k.
    @Test
    void tenThousandFormatsAsTenK() {
        assertEquals("10k", HudValueFormatter.abbreviate(10000));
    }

    // ── 20/21: M/B-scale abbreviation ────────────────────────────────────────────────────────

    // 20. Million-scale values format safely.
    @Test
    void millionScaleValuesFormatSafely() {
        assertEquals("1.2M", HudValueFormatter.abbreviate(1_200_000));
        assertEquals("3M", HudValueFormatter.abbreviate(3_000_000));
    }

    // 21. Billion-scale values format safely.
    @Test
    void billionScaleValuesFormatSafely() {
        assertEquals("2B", HudValueFormatter.abbreviate(2_000_000_000L));
        assertEquals("4B", HudValueFormatter.abbreviate(4_000_000_000L));
    }

    // ── Review-correction pass: rollover promotion at unit boundaries ───────────────────────
    // A value whose one-decimal rounding at a given tier reaches 1000 must promote to the next
    // tier instead of ever displaying a "1000k"/"1000M"-shaped string.

    @Test
    void aValueThatWouldRoundToOneThousandKPromotesToOneMInstead() {
        // 999_950 / 1000.0 = 999.95, which rounds to 1000.0 at the "k" tier.
        assertEquals("1M", HudValueFormatter.abbreviate(999_950L));
        assertFalse(HudValueFormatter.abbreviate(999_950L).contains("1000"),
                "must never emit a 1000k-shaped string");
    }

    @Test
    void aValueThatWouldRoundToOneThousandMPromotesToOneBInstead() {
        // 999_950_000 / 1_000_000.0 = 999.95, which rounds to 1000.0 at the "M" tier.
        assertEquals("1B", HudValueFormatter.abbreviate(999_950_000L));
        assertFalse(HudValueFormatter.abbreviate(999_950_000L).contains("1000"),
                "must never emit a 1000M-shaped string");
    }

    @Test
    void aValueJustBelowTheRolloverThresholdStaysAtItsOwnTier() {
        // 999_499 / 1000.0 = 999.499, rounds to 999.5 — legitimately still "k", not a rollover case.
        assertEquals("999.5k", HudValueFormatter.abbreviate(999_499L));
        // 999_499_000 / 1_000_000.0 = 999.499, rounds to 999.5 — legitimately still "M".
        assertEquals("999.5M", HudValueFormatter.abbreviate(999_499_000L));
    }

    // ── 22: compact fallback only when measured width requires it ───────────────────────────

    // 22. Compact slash form is used only when measured width requires it.
    @Test
    void compactSlashFormIsUsedOnlyWhenMeasuredWidthRequiresIt() {
        // "100 / 100" measures 9*6=54 under SIX_PX_PER_CHAR — fits within a generous 60px lane.
        assertEquals("100 / 100", HudValueFormatter.display(100, 100, 60, SIX_PX_PER_CHAR));
        // The same value does not fit a lane narrower than the spaced form's measured width —
        // falls back to the compact "100/100" (7*6=42) form instead.
        assertEquals("100/100", HudValueFormatter.display(100, 100, 50, SIX_PX_PER_CHAR));
    }

    // ── 23: long values are not narrowed to int ─────────────────────────────────────────────

    // 23. Long values are not narrowed to int.
    @Test
    void longValuesAreNotNarrowedToInt() {
        // 3_000_000_000 exceeds Integer.MAX_VALUE (2_147_483_647) — an accidental int narrowing
        // anywhere in the formatting path would silently wrap to a negative/garbage value.
        long overIntRange = 3_000_000_000L;
        assertTrue(overIntRange > Integer.MAX_VALUE);
        String result = HudValueFormatter.abbreviate(overIntRange);
        assertEquals("3B", result);
        assertFalse(result.contains("-"), "a narrowed/overflowed value would produce a negative sign");
    }

    // ── 24: text bounds remain within the approved lane for tested values ───────────────────

    // 24. Text bounds remain within the approved lane for tested values.
    @Test
    void textBoundsRemainWithinTheApprovedFillLaneForTestedValues() {
        ToIntFunction<String> realisticWidth = s -> s.length() * 6; // conservative per-char estimate
        long[][] cases = {
                {100, 100}, {110, 110}, {999, 999}, {1500, 2000}, {78, 110}
        };
        for (long[] c : cases) {
            String text = HudValueFormatter.display(c[0], c[1], HudBarLayout.INTERIOR_WIDTH, realisticWidth);
            assertTrue(realisticWidth.applyAsInt(text) <= HudBarLayout.INTERIOR_WIDTH,
                    "expected \"" + text + "\" (for " + c[0] + "/" + c[1] + ") to fit within the " +
                            HudBarLayout.INTERIOR_WIDTH + "px fill lane");
        }
    }

    // ── Contract: non-negative only ──────────────────────────────────────────────────────────

    @Test
    void abbreviateRejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> HudValueFormatter.abbreviate(-1));
    }

    @Test
    void abbreviateLeavesSubThousandValuesAsPlainDigits() {
        assertEquals("0", HudValueFormatter.abbreviate(0));
        assertEquals("78", HudValueFormatter.abbreviate(78));
        assertEquals("999", HudValueFormatter.abbreviate(999));
    }
}

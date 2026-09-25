package zcylas.totality.client.tooltip;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for the renderer's extracted layout-policy arithmetic
 * ({@code effectiveContentWidth}, {@code availableBodyHeight}, {@code fitsOnOneLine},
 * {@code footerHintFlags}) — package-private methods on {@link TotalityTooltipRenderer},
 * called directly since this test lives in the same package. These test the wrap/no-wrap and
 * sizing *decisions*, not actual glyph measurement or rendered pixel output: {@code Font} cannot
 * be constructed under plain JUnit in this repository, so real text width and
 * {@code Font.split} wrapping are not exercised here — that is covered by manual visual
 * validation instead (see the correction report).
 */
class TotalityTooltipRendererLayoutPolicyTest {

    // ── effectiveContentWidth (Finding 1: compact shrink-to-content width policy) ───────────
    // naturalContentW inputs below are representative approximations of each named item's
    // measured content (measureNaturalContentWidth itself needs a real Font and is therefore
    // not directly testable here — see the source-regression sentinel for its per-section-kind
    // inclusion/exclusion rules); these tests prove the clamping *policy* effectiveContentWidth
    // applies to whatever natural width it's given.

    @Test
    void whitestoneStyleSimpleItemFloorsAtTheCompactMinimum() {
        // Short title, small COMMON/BLOCK badges, no stat rows — natural width sits well under
        // the compact floor, so the panel floors at MIN_WIDTH rather than stretching wider.
        int w = TotalityTooltipRenderer.effectiveContentWidth(1920, 70);
        assertEquals(110, w, "a simple item's tooltip must floor at the compact MIN_WIDTH");
    }

    @Test
    void potionOfHealingStyleContentStaysCompact() {
        // "Restores: 2d4 + 2 HP" / "Use Time: 1.6 seconds" are short rows.
        int w = TotalityTooltipRenderer.effectiveContentWidth(1920, 95);
        assertEquals(110, w);
    }

    @Test
    void apprenticeGrimoireStyleContentUsesItsOwnMeasuredWidth() {
        // "Tier: II" / "Spell: No spell active" plus "EPIC MAGICAL" badges land between the
        // floor and the ceiling — the panel uses exactly the measured width, no more, no less.
        int w = TotalityTooltipRenderer.effectiveContentWidth(1920, 130);
        assertEquals(130, w);
    }

    @Test
    void netheriteShurikenStyleDetailedWeaponMayReachTheModerateCeiling() {
        // "Damage: 2d8 Piercing (STR or DEX)" is the widest single row — near the compact
        // preferred ceiling, moderately wider than a simple item, never near the old 260px max.
        int w = TotalityTooltipRenderer.effectiveContentWidth(1920, 190);
        assertEquals(190, w);
        assertTrue(w <= 200, "even a fairly wide natural row must not exceed the compact PREFERRED_MAX_WIDTH");
    }

    @Test
    void longCustomItemNameDoesNotForceThePanelPastThePreferredMaximum() {
        int w = TotalityTooltipRenderer.effectiveContentWidth(1920, 900);
        assertEquals(200, w, "a very long title must cap at PREFERRED_MAX_WIDTH and wrap vertically instead");
    }

    @Test
    void longTechnicalRegistryIdentifierWidthIsAlsoCappedIfItEverReachedThisPolicy() {
        // TechnicalInfo lines are excluded from measureNaturalContentWidth entirely (see the
        // source-regression sentinel) — this test instead proves effectiveContentWidth's own
        // policy caps even a huge input, which is the safety net for that exclusion.
        int w = TotalityTooltipRenderer.effectiveContentWidth(1920, 5000);
        assertEquals(200, w);
    }

    @Test
    void neverExceedsApproximatelyThirtyEightPercentOfScreenWidthUnderOrdinaryConditions() {
        for (int screenW : new int[]{300, 400, 500, 640, 800, 1024, 1920}) {
            int contentW = TotalityTooltipRenderer.effectiveContentWidth(screenW, 5000);
            int panelW = contentW + 16; // PADDING * 2
            assertTrue(panelW <= screenW * 0.38 + 1,
                    "panel width " + panelW + " exceeds ~38% of screen width " + screenW);
        }
    }

    @Test
    void smallScreenShrinksTheEffectiveWidthBelowThePreferredMaximum() {
        int narrow = TotalityTooltipRenderer.effectiveContentWidth(220, 200);
        assertTrue(narrow < 200, "a narrow screen must not use the full preferred maximum width");
        assertTrue(narrow > 0, "effective width must stay positive");
    }

    @Test
    void extremelyNarrowScreenNeverProducesAZeroOrNegativeWidth() {
        assertTrue(TotalityTooltipRenderer.effectiveContentWidth(1, 200) > 0);
        assertTrue(TotalityTooltipRenderer.effectiveContentWidth(0, 200) > 0);
        assertTrue(TotalityTooltipRenderer.effectiveContentWidth(-500, 200) > 0);
    }

    @Test
    void effectiveWidthNeverExceedsWhatTheScreenCanOffer() {
        // For a very small screen, the effective width must not claim more room than exists.
        int screenW = 100;
        int w = TotalityTooltipRenderer.effectiveContentWidth(screenW, 200);
        assertTrue(w <= screenW, "effective content width must not exceed the screen width itself");
    }

    // ── availableBodyHeight ──────────────────────────────────────────────────

    @Test
    void bodyHeightIsScreenMinusChromeWhenThereIsRoom() {
        int available = TotalityTooltipRenderer.availableBodyHeight(1000, 40, 30);
        assertEquals(1000 - (40 + 30), available, "chrome is header + footer only — no header divider band");
    }

    @Test
    void bodyHeightNeverGoesNegativeWhenChromeExceedsTheScreen() {
        int available = TotalityTooltipRenderer.availableBodyHeight(50, 40, 30);
        assertEquals(0, available, "chrome alone (70) exceeds the tiny viewport (50) — must fail safely to zero, not negative");
    }

    @Test
    void bodyHeightIsZeroOnAZeroHeightScreen() {
        assertEquals(0, TotalityTooltipRenderer.availableBodyHeight(0, 10, 10));
    }

    // ── fitsOnOneLine ─────────────────────────────────────────────────────────

    @Test
    void shortLabelAndValueFitOnOneLine() {
        assertTrue(TotalityTooltipRenderer.fitsOnOneLine(0, 40, 30, 4, 200));
    }

    @Test
    void longValueDoesNotFitAlongsideItsLabel() {
        // Simulates e.g. Netherite Battery's exact-figure Details value against a narrow panel.
        assertFalse(TotalityTooltipRenderer.fitsOnOneLine(19, 60, 220, 4, 240));
    }

    @Test
    void exactBoundaryFitsInclusively() {
        assertTrue(TotalityTooltipRenderer.fitsOnOneLine(0, 50, 50, 0, 100));
    }

    @Test
    void oneOverTheBoundaryDoesNotFit() {
        assertFalse(TotalityTooltipRenderer.fitsOnOneLine(0, 50, 51, 0, 100));
    }

    // ── visualForScale / logicalForScale (tooltip-layout-cleanup pass, 2026-09-22) ───────────
    // Pure arithmetic, unlike measureNaturalContentWidth/layout (which need a real Font and are
    // therefore only source-regression-testable) — directly unit-tested here.

    @Test
    void visualForScaleShrinksALogicalMeasurementByTheGivenFraction() {
        assertEquals(14, TotalityTooltipRenderer.visualForScale(16, 0.875f),
                "16 logical px at 0.875 scale must visually render as 14px (round(16*0.875)=14)");
    }

    @Test
    void visualForScaleAtFullScaleIsIdentity() {
        assertEquals(123, TotalityTooltipRenderer.visualForScale(123, 1.0f));
    }

    @Test
    void logicalForScaleGrowsAVisualBudgetSoWrappedTextHasRoomToBeSmaller() {
        // A scaled row needs MORE logical-unit room than its visual budget, since the text will
        // render smaller than the logical measurement implies.
        int logical = TotalityTooltipRenderer.logicalForScale(140, 0.875f);
        assertTrue(logical > 140, "the logical wrap width for scaled text must exceed the visual budget");
        assertEquals(160, logical, "round(140/0.875)=160");
    }

    @Test
    void logicalForScaleAtFullScaleIsIdentity() {
        assertEquals(200, TotalityTooltipRenderer.logicalForScale(200, 1.0f));
    }

    @Test
    void logicalForScaleNeverProducesAZeroOrNegativeWidth() {
        assertTrue(TotalityTooltipRenderer.logicalForScale(0, 0.875f) > 0);
        assertTrue(TotalityTooltipRenderer.logicalForScale(-50, 0.875f) > 0);
    }

    @Test
    void visualAndLogicalForScaleAreApproximateInversesOfEachOther() {
        // Converting visual -> logical -> visual should land back within 1px of the original
        // (rounding at each step means it is not always bit-exact, but it must never drift by
        // more than a pixel).
        for (int visual : new int[]{20, 50, 75, 100, 140, 200}) {
            int logical = TotalityTooltipRenderer.logicalForScale(visual, 0.875f);
            int roundTripped = TotalityTooltipRenderer.visualForScale(logical, 0.875f);
            assertTrue(Math.abs(roundTripped - visual) <= 1,
                    "visual=" + visual + " logical=" + logical + " roundTripped=" + roundTripped);
        }
    }
}

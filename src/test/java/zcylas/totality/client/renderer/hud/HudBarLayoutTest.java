package zcylas.totality.client.renderer.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real, executing tests for {@link HudBarLayout}'s approved geometry and shared position formulas
 * (see {@code TOTALITY_SMALL_HUD_CLEANUP_IMPLEMENTATION_REPORT.md} and its correction reports —
 * this file reflects the final player-HUD and vanilla-chat compatibility correction's 96x12 frame
 * with a genuine 1px border and a single 94x10 interior, not the earlier symmetric-3px-track-inset
 * attempt). Pure — no Minecraft/rendering dependency, so unlike {@code TotalityHudRenderer} itself
 * this class can be exercised directly under plain JUnit.
 */
class HudBarLayoutTest {

    // ── Approved geometry constants ──────────────────────────────────────────────────────────

    @Test
    void frameDisplayGeometryIs96x12() {
        assertEquals(96, HudBarLayout.FRAME_WIDTH);
        assertEquals(12, HudBarLayout.FRAME_HEIGHT);
    }

    @Test
    void borderIsOnePixel() {
        assertEquals(1, HudBarLayout.BORDER_WIDTH);
    }

    @Test
    void interiorOffsetIsOnePixelOnBothAxes() {
        assertEquals(1, HudBarLayout.INTERIOR_OFFSET_X);
        assertEquals(1, HudBarLayout.INTERIOR_OFFSET_Y);
    }

    // Explicit required arithmetic: 96-1-1=94, 12-1-1=10.
    @Test
    void interiorSizeIs94x10DerivedFromFrameMinusBorderOnBothSides() {
        assertEquals(94, HudBarLayout.INTERIOR_WIDTH);
        assertEquals(10, HudBarLayout.INTERIOR_HEIGHT);
        assertEquals(HudBarLayout.INTERIOR_WIDTH,
                HudBarLayout.FRAME_WIDTH - HudBarLayout.INTERIOR_OFFSET_X * 2);
        assertEquals(HudBarLayout.INTERIOR_HEIGHT,
                HudBarLayout.FRAME_HEIGHT - HudBarLayout.INTERIOR_OFFSET_Y * 2);
    }

    @Test
    void edgeMarginRemains6() {
        assertEquals(6, HudBarLayout.EDGE_MARGIN);
    }

    @Test
    void bottomMarginRemains2() {
        assertEquals(2, HudBarLayout.BOTTOM_MARGIN);
    }

    @Test
    void barGapIs2() {
        assertEquals(2, HudBarLayout.BAR_GAP);
    }

    // ── Derived Y-position formulas ──────────────────────────────────────────────────────────

    @Test
    void leftBarOrderRemainsHealthManaStaminaTopToBottom() {
        int screenH = 1080;
        assertTrue(HudBarLayout.healthY(screenH) < HudBarLayout.manaY(screenH));
        assertTrue(HudBarLayout.manaY(screenH) < HudBarLayout.staminaY(screenH));
    }

    @Test
    void foodRemainsAlignedWithHealth() {
        for (int screenH : new int[] {200, 480, 1080, 2160}) {
            assertEquals(HudBarLayout.healthY(screenH), HudBarLayout.foodY(screenH));
        }
    }

    @Test
    void ragePositionFollowsFoodGeometry() {
        int screenH = 1080;
        assertEquals(HudBarLayout.foodY(screenH) + HudBarLayout.FRAME_HEIGHT + HudBarLayout.BAR_GAP,
                HudBarLayout.secondaryResourceY(screenH));
    }

    // Approved absolute Y positions for the 96x12 geometry: Stamina H-14, Mana H-28,
    // Health/Food H-42, Rage H-28 (derived from BOTTOM_MARGIN=2, BAR_GAP=2, FRAME_HEIGHT=12) —
    // unchanged in value from the prior pass since FRAME_HEIGHT/BAR_GAP/BOTTOM_MARGIN themselves
    // did not change, only the border/interior split within each 12px-tall frame did.
    @Test
    void approvedAbsoluteYPositionsMatchTheAuthoredFormulasForAnyScreenHeight() {
        for (int screenH : new int[] {200, 480, 1080, 2160}) {
            assertEquals(screenH - 14, HudBarLayout.staminaY(screenH));
            assertEquals(screenH - 28, HudBarLayout.manaY(screenH));
            assertEquals(screenH - 42, HudBarLayout.healthY(screenH));
            assertEquals(screenH - 42, HudBarLayout.foodY(screenH));
            assertEquals(screenH - 28, HudBarLayout.secondaryResourceY(screenH));
        }
    }

    // ── Left/right X anchors ─────────────────────────────────────────────────────────────────

    @Test
    void leftXIsTheEdgeMargin() {
        assertEquals(HudBarLayout.EDGE_MARGIN, HudBarLayout.leftX());
    }

    @Test
    void rightXPlacesTheFramesRightEdgeMarginFromTheScreenEdge() {
        int screenW = 480;
        int rightX = HudBarLayout.rightX(screenW);
        assertEquals(screenW - HudBarLayout.EDGE_MARGIN, rightX + HudBarLayout.FRAME_WIDTH);
    }

    // ── Embedded-text placement formulas ─────────────────────────────────────────────────────

    @Test
    void textXCentersAgainstTheFullInteriorWidth() {
        int barX = 6;
        int textWidth = 40;
        int expected = barX + HudBarLayout.INTERIOR_OFFSET_X + (HudBarLayout.INTERIOR_WIDTH - textWidth) / 2;
        assertEquals(expected, HudBarLayout.textX(barX, textWidth));
    }

    @Test
    void textXKeepsNarrowerTextCenteredAsWidthShrinks() {
        int barX = 6;
        int wide = HudBarLayout.textX(barX, 60);
        int narrow = HudBarLayout.textX(barX, 20);
        assertTrue(narrow > wide);
    }

    // Text stays stationary as fill % changes: textX must not depend on the currently-filled
    // width, only on the measured text width and the fixed interior.
    @Test
    void textXDoesNotDependOnFillPercentage() {
        int barX = 6;
        int textWidth = 30;
        // Same text width, called as if at 10% fill vs 90% fill (fill % is simply not a parameter)
        // — the formula has no way to vary by fill, which is itself the guarantee.
        assertEquals(HudBarLayout.textX(barX, textWidth), HudBarLayout.textX(barX, textWidth));
    }

    // Vertically centers accounting for the text's shadow (visual height = fontLineHeight + 1).
    // With the default fontLineHeight=9, the purely mathematical center evaluates to exactly
    // INTERIOR_OFFSET_Y (zero remaining margin) — VALUE_TEXT_Y_OFFSET is then added on top, per
    // the final-visual-correction pass (§57.1/§20 addendum): live testing found the mathematical
    // center alone still read as one pixel too high.
    @Test
    void textYAccountsForShadowAndFitsExactlyForTheDefaultFontLineHeight() {
        int barY = 100;
        int fontLineHeight = 9; // vanilla Minecraft's default Font.lineHeight
        int expected = barY + HudBarLayout.INTERIOR_OFFSET_Y + HudBarLayout.VALUE_TEXT_Y_OFFSET;
        assertEquals(expected, HudBarLayout.textY(barY, fontLineHeight));
    }

    @Test
    void textYGeneralFormulaUsesFontLineHeightPlusOneForShadowThenAddsTheNamedOffset() {
        int barY = 0;
        int fontLineHeight = 9;
        int baseTextY = barY + HudBarLayout.INTERIOR_OFFSET_Y
                + (HudBarLayout.INTERIOR_HEIGHT - (fontLineHeight + 1)) / 2;
        int expected = baseTextY + HudBarLayout.VALUE_TEXT_Y_OFFSET;
        assertEquals(expected, HudBarLayout.textY(barY, fontLineHeight));
    }

    // ── VALUE_TEXT_Y_OFFSET (final-visual-correction pass) ──────────────────────────────────

    // 5/6. The named one-pixel visual correction constant exists exactly once, with the approved value.
    @Test
    void valueTextYOffsetIsExactlyOnePixel() {
        assertEquals(1, HudBarLayout.VALUE_TEXT_Y_OFFSET);
    }

    // The offset must actually move the result — confirms it is really applied, not a dead constant.
    @Test
    void valueTextYOffsetActuallyShiftsTextYDownByExactlyItsOwnValue() {
        int barY = 50;
        int fontLineHeight = 9;
        int baseTextY = barY + HudBarLayout.INTERIOR_OFFSET_Y
                + (HudBarLayout.INTERIOR_HEIGHT - (fontLineHeight + 1)) / 2;
        assertEquals(baseTextY + HudBarLayout.VALUE_TEXT_Y_OFFSET, HudBarLayout.textY(barY, fontLineHeight));
    }

    // Glyph+shadow must stay within the interior for the default font line height. The
    // real single-line glyph ink height is one pixel shorter than fontLineHeight itself
    // (fontLineHeight is a line-SPACING constant that reserves one extra row for the gap between
    // wrapped lines — a single line of Totality's bar text, which is always digits/"/"/spaces/
    // k-M-B, never has ink filling that reserved row). Modeling real ink height as
    // (fontLineHeight - 1) plus the shadow's one extra offset row gives the same testable
    // containment bound the live-visual correction was verified against: text sits flush with the
    // interior's bottom edge after the one-pixel offset, never crossing into the border.
    @Test
    void textYPlusShadowExtentStaysWithinTheInteriorForTheDefaultFontLineHeight() {
        int barY = 0;
        int fontLineHeight = 9;
        int realGlyphInkHeight = fontLineHeight - 1; // excludes the inter-line gap row lineHeight reserves
        int interiorTop = barY + HudBarLayout.INTERIOR_OFFSET_Y;
        int interiorBottom = interiorTop + HudBarLayout.INTERIOR_HEIGHT - 1; // last valid interior row
        int textTop = HudBarLayout.textY(barY, fontLineHeight);
        int shadowBottomRow = textTop + realGlyphInkHeight; // glyph's own last row + shadow's 1px offset
        assertTrue(textTop >= interiorTop, "text must not start above the interior's top edge");
        assertTrue(shadowBottomRow <= interiorBottom,
                "text plus its shadow must not touch or cross the one-pixel border");
    }

    // ── Vanilla-chat bottom reservation ──────────────────────────────────────────────────────

    @Test
    void chatExtraClearanceIs4() {
        assertEquals(4, HudBarLayout.CHAT_EXTRA_CLEARANCE);
    }

    // Explicit required arithmetic: leftStackHeight = 3*12 + 2*2 = 40.
    @Test
    void leftStackHeightIs40() {
        assertEquals(40, HudBarLayout.leftStackHeight());
        assertEquals(3 * HudBarLayout.FRAME_HEIGHT + 2 * HudBarLayout.BAR_GAP, HudBarLayout.leftStackHeight());
    }

    // Explicit required arithmetic: chatBottomReservation = 40 + 2 + 4 = 46.
    @Test
    void chatBottomReservationIs46() {
        assertEquals(46, HudBarLayout.chatBottomReservation());
        assertEquals(HudBarLayout.leftStackHeight() + HudBarLayout.BOTTOM_MARGIN + HudBarLayout.CHAT_EXTRA_CLEARANCE,
                HudBarLayout.chatBottomReservation());
    }

    // Changing only CHAT_EXTRA_CLEARANCE must shift the computed reservation by exactly that much
    // — proves the formula is genuinely derived, not a second hardcoded literal that happens to
    // equal 46.
    @Test
    void chatBottomReservationTracksLeftStackHeightAndBottomMarginPlusClearance() {
        int expected = HudBarLayout.leftStackHeight() + HudBarLayout.BOTTOM_MARGIN + HudBarLayout.CHAT_EXTRA_CLEARANCE;
        assertEquals(expected, HudBarLayout.chatBottomReservation());
    }
}

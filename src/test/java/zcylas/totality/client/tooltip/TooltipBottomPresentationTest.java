package zcylas.totality.client.tooltip;

import org.junit.jupiter.api.Test;
import zcylas.totality.client.tooltip.contributor.ResourceFormat;
import zcylas.totality.client.tooltip.footer.TooltipFooter;
import zcylas.totality.client.tooltip.footer.TooltipFooter.Placement;
import zcylas.totality.client.tooltip.footer.TooltipFooter.Slot;
import zcylas.totality.client.tooltip.renderer.TooltipModifierPanels;
import zcylas.totality.client.tooltip.renderer.TooltipModifierPanels.Modifier;
import zcylas.totality.client.tooltip.renderer.TooltipResourceColors;
import zcylas.totality.client.tooltip.renderer.TooltipResourceGaugePainter;
import zcylas.totality.client.tooltip.renderer.TooltipTextScale;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.client.tooltip.TooltipDisclosureLevel.*;

/** Tooltip V2 bottom presentation: resources, footer, modifier panels, pixel-safe text scale. */
class TooltipBottomPresentationTest {

    // ── Resource figures ──────────────────────────────────────────────────────

    @Test
    void compactFiguresAreFlooredToOneDecimalNeverRoundedUp() {
        assertEquals("31.2k", ResourceFormat.compact(31_200));
        assertEquals("48k", ResourceFormat.compact(48_000));
        assertEquals("47.9k", ResourceFormat.compact(47_990), "never shown as the full 48k");
        assertEquals("999", ResourceFormat.compact(999));
        assertEquals("5M", ResourceFormat.compact(5_000_000));
        assertEquals("0", ResourceFormat.compact(0));
    }

    @Test
    void percentageIsZeroOnlyWhenEmptyAndHundredOnlyWhenFull() {
        assertEquals("0%", ResourceFormat.percent(0, 48_000));
        assertEquals("100%", ResourceFormat.percent(48_000, 48_000));
        assertEquals("99%", ResourceFormat.percent(47_999, 48_000));
        assertEquals("<1%", ResourceFormat.percent(1, 48_000));
        assertEquals("65%", ResourceFormat.percent(31_200, 48_000));
        assertEquals("0%", ResourceFormat.percent(0, 0), "no capacity -> nothing invented");
    }

    @Test
    void energyAndDurabilityFiguresFollowTheAgreedLayout() {
        assertEquals("31.2k / 48k UE (65%)", ResourceFormat.figures(31_200, 48_000, "UE", false));
        assertEquals("0 / 48k UE (0%)", ResourceFormat.figures(0, 48_000, "UE", false));
        assertEquals("31200 / 48000 UE (65%)", ResourceFormat.figures(31_200, 48_000, "UE", true));
        assertEquals("920 / 1000 (92%)", ResourceFormat.figures(920, 1000, "", true));
    }

    @Test
    void theBarIsExactlyEmptyAtZeroExactlyFullAtMaxAndHonestInBetween() {
        int track = 100;
        assertEquals(0, TooltipResourceGaugePainter.fillWidth(0, 48_000, track));
        assertEquals(track, TooltipResourceGaugePainter.fillWidth(48_000, 48_000, track));
        assertEquals(65, TooltipResourceGaugePainter.fillWidth(31_200, 48_000, track));
        assertEquals(1, TooltipResourceGaugePainter.fillWidth(48, 48_000, track), "a non-empty resource never looks empty");
        assertEquals(track - 1, TooltipResourceGaugePainter.fillWidth(47_990, 48_000, track), "a non-full resource never looks full");
        assertEquals(0, TooltipResourceGaugePainter.fillWidth(10, 0, track), "no capacity -> nothing drawn");
    }

    // ── Large-value precision (long capacities) ─────────────────────────────────

    @Test
    void anAlmostFullBillionUeBarIsNotDrawnFullEvenThoughItsFloatFractionRoundsToOne() {
        long max = 1_000_000_000L, cur = 999_999_999L;
        assertEquals(1f, (float) cur / max, "the float fraction really does round to exactly 1.0 — the old bug's trigger");
        assertEquals(99, TooltipResourceGaugePainter.fillWidth(cur, max, 100));
        assertEquals("99%", ResourceFormat.percent(cur, max));
        assertEquals("999999999 / 1000000000 UE (99%)", ResourceFormat.figures(cur, max, "UE", true));
    }

    @Test
    void roughlyHalfOfLongMaxDoesNotOverflow() {
        long max = Long.MAX_VALUE, cur = Long.MAX_VALUE / 2;
        assertEquals("49%", ResourceFormat.percent(cur, max), "floored 49.99..%, and no current * 100 overflow");
        assertEquals("50%", ResourceFormat.percent(Long.MAX_VALUE / 2, Long.MAX_VALUE - 1), "exactly half of a huge even capacity");
        assertEquals(50, TooltipResourceGaugePainter.fillWidth(cur, max, 100));
        assertEquals(0.5f, ResourceFormat.fraction(cur, max), 1e-6f);
    }

    @Test
    void exactZeroAndExactMaximumAtLongMax() {
        assertEquals(0, TooltipResourceGaugePainter.fillWidth(0, Long.MAX_VALUE, 100));
        assertEquals("0%", ResourceFormat.percent(0, Long.MAX_VALUE));
        assertEquals(100, TooltipResourceGaugePainter.fillWidth(Long.MAX_VALUE, Long.MAX_VALUE, 100));
        assertEquals("100%", ResourceFormat.percent(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    void verySmallPositiveValuesBelowOnePercentAreNeitherEmptyNorZeroPercent() {
        assertEquals(1, TooltipResourceGaugePainter.fillWidth(1, Long.MAX_VALUE, 100));
        assertEquals("<1%", ResourceFormat.percent(1, Long.MAX_VALUE));
        assertEquals(1, TooltipResourceGaugePainter.fillWidth(1, 1_000_000_000L, 100));
        assertEquals("<1%", ResourceFormat.percent(9_999_999L, 1_000_000_000L));
        assertEquals("1%", ResourceFormat.percent(10_000_000L, 1_000_000_000L));
    }

    @Test
    void valuesCloseToMaximumAreNeitherFullNorOneHundredPercent() {
        assertEquals(99, TooltipResourceGaugePainter.fillWidth(Long.MAX_VALUE - 1, Long.MAX_VALUE, 100));
        assertEquals("99%", ResourceFormat.percent(Long.MAX_VALUE - 1, Long.MAX_VALUE));
        assertEquals(99, TooltipResourceGaugePainter.fillWidth(47_999, 48_000, 100));
        assertEquals("99%", ResourceFormat.percent(47_999, 48_000));
        assertEquals(1, TooltipResourceGaugePainter.fillWidth(1, 2, 2), "half of a 2px interior is 1px — neither empty nor full");
        assertEquals(0, TooltipResourceGaugePainter.fillWidth(1, 2, 0), "no interior -> nothing drawn");
    }

    // ── Resource colors ───────────────────────────────────────────────────────

    @Test
    void energyStaysUeBlueAndDarkensAsItDepletes() {
        assertEquals(TooltipResourceColors.UE_BLUE, TooltipResourceColors.energy(1f));
        int previous = Integer.MAX_VALUE;
        for (float f = 1f; f >= 0f; f -= 0.1f) {
            int c = TooltipResourceColors.energy(f);
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            assertTrue(b > r && b >= g * 0.9, "blue family at " + f + " (never orange/red): " + Integer.toHexString(c));
            int brightness = r + g + b;
            assertTrue(brightness <= previous, "darker as it depletes");
            previous = brightness;
        }
    }

    @Test
    void durabilityStepsGreenOrangeRed() {
        assertEquals(TooltipResourceColors.DURABILITY_GREEN, TooltipResourceColors.durability(1f));
        assertEquals(TooltipResourceColors.DURABILITY_GREEN, TooltipResourceColors.durability(0.51f));
        assertEquals(TooltipResourceColors.DURABILITY_ORANGE, TooltipResourceColors.durability(0.50f));
        assertEquals(TooltipResourceColors.DURABILITY_ORANGE, TooltipResourceColors.durability(0.21f));
        assertEquals(TooltipResourceColors.DURABILITY_RED, TooltipResourceColors.durability(0.20f));
        assertEquals(TooltipResourceColors.DURABILITY_RED, TooltipResourceColors.durability(0f));
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    @Test
    void footerFieldsShareOneRowWithTheOriginTrulyCentred() {
        List<Placement> p = TooltipFooter.layout(180, 60, 40, 30);
        assertEquals(1, TooltipFooter.rows(p));
        assertEquals(new Placement(Slot.LEFT, 0, 0), p.get(0));
        assertEquals(new Placement(Slot.CENTER, 70, 0), p.get(1));
        assertEquals(new Placement(Slot.RIGHT, 150, 0), p.get(2));
    }

    @Test
    void theOriginStaysCentredWhenWeightOrPriceIsAbsent() {
        assertEquals(new Placement(Slot.CENTER, 70, 0), TooltipFooter.layout(180, -1, 40, -1).get(0));
        assertEquals(new Placement(Slot.CENTER, 70, 0), TooltipFooter.layout(180, 60, 40, -1).get(1));
    }

    @Test
    void aCrowdedFooterMovesTheOriginToItsOwnRowInsteadOfOverlapping() {
        List<Placement> p = TooltipFooter.layout(120, 55, 40, 40);
        assertEquals(2, TooltipFooter.rows(p));
        assertEquals(Slot.CENTER, p.get(0).slot());
        assertEquals(0, p.get(0).row());
        assertTrue(p.stream().filter(x -> x.slot() != Slot.CENTER).allMatch(x -> x.row() == 1));
    }

    @Test
    void missingFooterDataIsOmittedAndAnEmptyFooterHasNoRows() {
        assertEquals(0, TooltipFooter.rows(TooltipFooter.layout(180, -1, -1, -1)));
        assertTrue(new TooltipFooter.Info(null, null, null).isEmpty());
        assertEquals(List.of(Slot.RIGHT), TooltipFooter.layout(180, -1, -1, 30).stream().map(Placement::slot).toList());
    }

    @Test
    void footerFormatsFollowExistingConventions() {
        assertEquals("1", TooltipFooter.formatWeight(1.0f));
        assertEquals("2", TooltipFooter.formatWeight(2.0f));
        assertEquals("1.5", TooltipFooter.formatWeight(1.5f));
        assertEquals("0.25", TooltipFooter.formatWeight(0.25f), "meaningful precision is kept, not rounded to one decimal");
        assertEquals("0.1", TooltipFooter.formatWeight(0.1f), "the float's shortest decimal form, not 0.100000001");
        assertEquals("10", TooltipFooter.formatWeight(10f), "only insignificant zeroes are trimmed");
        assertEquals("0", TooltipFooter.formatWeight(0f));
        assertEquals("450 ₵", TooltipFooter.formatPrice(450));
        assertEquals("0 ₵", TooltipFooter.formatPrice(0), "a real authored zero value is shown, not hidden");
    }

    // ── Modifier panels ───────────────────────────────────────────────────────

    @Test
    void onlyApplicablePanelsAreShownInShiftAltCtrlOrder() {
        assertEquals(List.of(), TooltipModifierPanels.visible(false, false, false, DEFAULT));
        assertEquals(List.of(Modifier.SHIFT), modifiers(TooltipModifierPanels.visible(true, false, false, DEFAULT)));
        assertEquals(List.of(Modifier.CTRL), modifiers(TooltipModifierPanels.visible(false, false, true, DEFAULT)));
        assertEquals(List.of(Modifier.SHIFT, Modifier.ALT, Modifier.CTRL),
                modifiers(TooltipModifierPanels.visible(true, true, true, DEFAULT)), "ALT slots in without a redesign");
    }

    @Test
    void theHeldModifiersPanelIsHighlighted() {
        var shift = TooltipModifierPanels.visible(true, false, true, DETAILS);
        assertTrue(shift.get(0).active());
        assertFalse(shift.get(1).active());
        var ctrl = TooltipModifierPanels.visible(true, false, true, TECHNICAL);
        assertFalse(ctrl.get(0).active());
        assertTrue(ctrl.get(1).active());
        var both = TooltipModifierPanels.visible(true, false, true, DETAILS_AND_TECHNICAL);
        assertTrue(both.get(0).active() && both.get(1).active());
    }

    @Test
    void panelsAddHeightOnlyWhenVisibleAndStayOnScreen() {
        assertEquals(0, TooltipModifierPanels.blockHeight(List.of()));
        assertTrue(TooltipModifierPanels.blockHeight(TooltipModifierPanels.visible(true, false, false, DEFAULT)) > 0);
        assertEquals(40 + (200 - 100) / 2, TooltipModifierPanels.rowX(40, 200, 100, 960, 6), "centred under the tooltip");
        assertEquals(6, TooltipModifierPanels.rowX(0, 60, 150, 960, 6), "clamped to the left margin");
        assertEquals(960 - 6 - 150, TooltipModifierPanels.rowX(900, 60, 150, 960, 6), "clamped to the right margin");
        assertEquals(2 * 50 + TooltipModifierPanels.GAP_BETWEEN, TooltipModifierPanels.rowWidth(50, 2));
    }

    private static List<Modifier> modifiers(List<TooltipModifierPanels.Panel> panels) {
        return panels.stream().map(TooltipModifierPanels.Panel::modifier).toList();
    }

    // ── Pixel-safe text scale (GUI scale 1) ─────────────────────────────────────

    @Test
    void compactTextNeverFallsBelowOneScreenPixelPerFontTexel() {
        for (int gui = 1; gui <= 6; gui++) {
            float scale = TooltipTextScale.pixelSafe(0.875f, gui);
            assertTrue(TooltipTextScale.screenPixelsPerTexel(scale, gui) >= 1f - 1e-6f, "GUI " + gui);
        }
        assertEquals(1f, TooltipTextScale.pixelSafe(0.875f, 1), "unscaled at GUI 1 — the 0.875 row-dropping cause");
        assertEquals(0.875f, TooltipTextScale.pixelSafe(0.875f, 2), "compact appearance kept at GUI 2");
        assertEquals(0.875f, TooltipTextScale.pixelSafe(0.875f, 4));
    }
}

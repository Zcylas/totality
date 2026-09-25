package zcylas.totality.client.tooltip.preview;

import org.junit.jupiter.api.Test;
import zcylas.totality.client.tooltip.preview.TooltipGroupLayout.CardSide;
import zcylas.totality.client.tooltip.preview.TooltipGroupLayout.Placement;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.client.tooltip.preview.TooltipGroupLayout.*;

class TooltipGroupLayoutTest {

    private static final int M = 6, SW = 480, SH = 270, PW = 150, PH = 180, CW = 60, CH = 92;

    /** Tooltip V1's placement rule, verbatim, as the reference for the no-companion case. */
    private static int[] v1(int x, int y, int panelW, int panelH, int screenW, int screenH) {
        int panelX = x + 12, panelY = y - 12;
        if (panelX + panelW > screenW - M) panelX = x - panelW - 12;
        if (panelX < M) panelX = M;
        if (panelY + panelH > screenH - M) panelY = screenH - panelH - M;
        if (panelY < M) panelY = M;
        return new int[]{panelX, panelY};
    }

    @Test
    void withoutACompanionPlacementIsExactlyV1() {
        for (int x = 0; x < SW; x += 23) {
            for (int y = 0; y < SH; y += 17) {
                Placement p = place(x, y, PW, PH, 0, 0, SW, SH, M);
                int[] expected = v1(x, y, PW, PH, SW, SH);
                assertEquals(expected[0], p.panelX(), "x=" + x + " y=" + y);
                assertEquals(expected[1], p.panelY(), "x=" + x + " y=" + y);
                assertEquals(CardSide.NONE, p.cardSide());
            }
        }
    }

    @Test
    void companionPrefersTheLeftSideTopAlignedWithTheMainPanel() {
        Placement p = place(200, 60, PW, PH, CW, CH, SW, SH, M);
        assertEquals(CardSide.LEFT, p.cardSide());
        assertEquals(p.panelX() - COMPANION_GAP - CW, p.cardX());
        assertEquals(p.panelY(), p.cardY());
    }

    @Test
    void nearTheLeftEdgeTheWholeGroupShiftsRightInsteadOfPushingTheCardOffScreen() {
        Placement p = place(10, 60, PW, PH, CW, CH, SW, SH, M);
        assertEquals(CardSide.LEFT, p.cardSide());
        assertEquals(M, p.cardX());
        assertEquals(M + CW + COMPANION_GAP, p.panelX());
    }

    @Test
    void nearTheRightEdgeTheMainPanelFlipsLeftOfTheCursorAndTheCardStaysLeft() {
        Placement p = place(SW - 20, 60, PW, PH, CW, CH, SW, SH, M);
        assertEquals(CardSide.LEFT, p.cardSide());
        assertTrue(p.panelX() + PW <= SW - M);
        assertTrue(p.cardX() >= M);
    }

    @Test
    void whenTheLeftCannotFitTheCardGoesRight() {
        int narrowW = 2 * M + CW + COMPANION_GAP + PW - 1 + CW;   // no room left of the panel after shifting
        Placement p = place(0, 60, PW + 40, PH, CW, CH, narrowW + 40, SH, M);
        assertNotEquals(CardSide.HIDDEN, p.cardSide());
    }

    @Test
    void onScreensTooNarrowForSideBySideTheCardStacksAboveOrBelow() {
        int screenW = PW + 2 * M;          // the main panel alone fills the width
        Placement p = place(0, 150, PW, 80, CW, CH, screenW, 400, M);
        assertTrue(p.cardSide() == CardSide.ABOVE || p.cardSide() == CardSide.BELOW, "got " + p.cardSide());
    }

    @Test
    void theCardIsHiddenOnlyWhenNoPlacementCanFitAndIsNeverDrawnOffScreen() {
        Placement p = place(0, 0, 200, 250, CW, CH, 212, 262, M);
        assertEquals(CardSide.HIDDEN, p.cardSide());
    }

    @Test
    void everyVisiblePlacementKeepsBothPartsOnScreenAndSeparated() {
        for (int sw : new int[]{320, 427, 480, 640, 960}) {
            for (int sh : new int[]{240, 270, 360, 540}) {
                for (int x = 0; x < sw; x += 31) {
                    for (int y = 0; y < sh; y += 29) {
                        Placement p = place(x, y, PW, PH, CW, CH, sw, sh, M);
                        assertTrue(p.panelX() >= M && p.panelX() + PW <= sw - M, "panel x on screen");
                        if (p.cardSide() == CardSide.HIDDEN || p.cardSide() == CardSide.NONE) continue;
                        assertTrue(p.cardX() >= M && p.cardX() + CW <= sw - M, "card x on screen " + p);
                        assertTrue(p.cardY() >= M && p.cardY() + CH <= sh - M, "card y on screen " + p);
                        boolean overlaps = p.cardX() < p.panelX() + PW && p.panelX() < p.cardX() + CW
                                && p.cardY() < p.panelY() + PH && p.panelY() < p.cardY() + CH;
                        assertFalse(overlaps, "card must not overlap the main panel " + p);
                    }
                }
            }
        }
    }
}

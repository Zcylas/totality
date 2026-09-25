package zcylas.totality.client.tooltip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.client.tooltip.TooltipDisclosureLevel.*;

/**
 * Pure tests for the disclosure contract — no Minecraft bootstrap involved. Details (Shift) and
 * Technical (Ctrl) are independent: each shows only its own content, and holding both shows both
 * (Tooltip V2 bottom-presentation slice; previously Ctrl was a superset of Shift).
 */
class TooltipDisclosureLevelTest {

    @Test
    void defaultShowsOnlyDefaultContent() {
        assertTrue(DEFAULT.includes(DEFAULT));
        assertFalse(DEFAULT.includes(DETAILS));
        assertFalse(DEFAULT.includes(TECHNICAL));
    }

    @Test
    void shiftShowsDetailsButNotTechnical() {
        assertTrue(DETAILS.includes(DEFAULT));
        assertTrue(DETAILS.includes(DETAILS));
        assertFalse(DETAILS.includes(TECHNICAL));
    }

    @Test
    void ctrlShowsTechnicalButNotTheShiftDetails() {
        assertTrue(TECHNICAL.includes(DEFAULT));
        assertTrue(TECHNICAL.includes(TECHNICAL));
        assertFalse(TECHNICAL.includes(DETAILS));
    }

    @Test
    void shiftPlusCtrlShowsBoth() {
        assertTrue(DETAILS_AND_TECHNICAL.includes(DEFAULT));
        assertTrue(DETAILS_AND_TECHNICAL.includes(DETAILS));
        assertTrue(DETAILS_AND_TECHNICAL.includes(TECHNICAL));
        assertTrue(DETAILS_AND_TECHNICAL.includes(DETAILS_AND_TECHNICAL));
        assertFalse(DETAILS.includes(DETAILS_AND_TECHNICAL));
        assertFalse(TECHNICAL.includes(DETAILS_AND_TECHNICAL));
    }

    @Test
    void heldModifiersMapToTheirLevel() {
        assertEquals(DEFAULT, TooltipDisclosureLevel.of(false, false));
        assertEquals(DETAILS, TooltipDisclosureLevel.of(true, false));
        assertEquals(TECHNICAL, TooltipDisclosureLevel.of(false, true));
        assertEquals(DETAILS_AND_TECHNICAL, TooltipDisclosureLevel.of(true, true));
    }
}

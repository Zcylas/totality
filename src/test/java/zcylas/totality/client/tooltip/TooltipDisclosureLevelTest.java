package zcylas.totality.client.tooltip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure tests for the disclosure ordering contract — no Minecraft bootstrap involved. */
class TooltipDisclosureLevelTest {

    @Test
    void defaultIsNotAtLeastDetails() {
        assertFalse(TooltipDisclosureLevel.DEFAULT.atLeast(TooltipDisclosureLevel.DETAILS));
    }

    @Test
    void detailsIsAtLeastDefaultAndDetailsButNotTechnical() {
        assertTrue(TooltipDisclosureLevel.DETAILS.atLeast(TooltipDisclosureLevel.DEFAULT));
        assertTrue(TooltipDisclosureLevel.DETAILS.atLeast(TooltipDisclosureLevel.DETAILS));
        assertFalse(TooltipDisclosureLevel.DETAILS.atLeast(TooltipDisclosureLevel.TECHNICAL));
    }

    @Test
    void technicalIsAtLeastEveryLevel() {
        assertTrue(TooltipDisclosureLevel.TECHNICAL.atLeast(TooltipDisclosureLevel.DEFAULT));
        assertTrue(TooltipDisclosureLevel.TECHNICAL.atLeast(TooltipDisclosureLevel.DETAILS));
        assertTrue(TooltipDisclosureLevel.TECHNICAL.atLeast(TooltipDisclosureLevel.TECHNICAL));
    }

    @Test
    void ordinalOrderMatchesDefaultThenDetailsThenTechnical() {
        assertEquals(0, TooltipDisclosureLevel.DEFAULT.ordinal());
        assertEquals(1, TooltipDisclosureLevel.DETAILS.ordinal());
        assertEquals(2, TooltipDisclosureLevel.TECHNICAL.ordinal());
    }
}

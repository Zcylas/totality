package zcylas.totality.client.tooltip;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.item.IdentificationStatus;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for the neutral visibility contract. Deliberately does not construct an
 * {@code ItemStack} — see {@code HealingPotionItemContractTest}'s class Javadoc for why no test
 * in this repository does that under plain JUnit.
 */
class TooltipKnowledgeViewTest {

    @Test
    void alwaysIsVisibleRegardlessOfIdentificationOrDisclosure() {
        TooltipKnowledgeView unidentified = TooltipKnowledgeView.unidentified();
        assertTrue(unidentified.isVisible(TooltipVisibility.ALWAYS, TooltipDisclosureLevel.DEFAULT));
        assertTrue(unidentified.isVisible(TooltipVisibility.ALWAYS, TooltipDisclosureLevel.TECHNICAL));
    }

    @Test
    void whenRecognizedHiddenUntilAtLeastPartiallyIdentified() {
        TooltipKnowledgeView unidentified = TooltipKnowledgeView.unidentified();
        assertFalse(unidentified.isVisible(TooltipVisibility.WHEN_RECOGNIZED, TooltipDisclosureLevel.DEFAULT));

        TooltipKnowledgeView recognized = TooltipKnowledgeView.partiallyIdentified();
        assertTrue(recognized.isVisible(TooltipVisibility.WHEN_RECOGNIZED, TooltipDisclosureLevel.DEFAULT));

        TooltipKnowledgeView identified = TooltipKnowledgeView.identified();
        assertTrue(identified.isVisible(TooltipVisibility.WHEN_RECOGNIZED, TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void whenIdentifiedHiddenUntilFullyIdentified() {
        TooltipKnowledgeView recognized = TooltipKnowledgeView.partiallyIdentified();
        assertFalse(recognized.isVisible(TooltipVisibility.WHEN_IDENTIFIED, TooltipDisclosureLevel.DEFAULT));

        TooltipKnowledgeView identified = TooltipKnowledgeView.identified();
        assertTrue(identified.isVisible(TooltipVisibility.WHEN_IDENTIFIED, TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void technicalVisibilityFollowsDisclosureNotIdentification() {
        TooltipKnowledgeView unidentified = TooltipKnowledgeView.unidentified();
        assertFalse(unidentified.isVisible(TooltipVisibility.TECHNICAL, TooltipDisclosureLevel.DEFAULT));
        assertTrue(unidentified.isVisible(TooltipVisibility.TECHNICAL, TooltipDisclosureLevel.TECHNICAL));

        TooltipKnowledgeView identified = TooltipKnowledgeView.identified();
        assertFalse(identified.isVisible(TooltipVisibility.TECHNICAL, TooltipDisclosureLevel.DEFAULT),
                "Technical content must not leak just because the item is identified");
    }

    @Test
    void identifiedFactoryReportsFullyIdentified() {
        assertEquals(IdentificationStatus.IDENTIFIED, TooltipKnowledgeView.identified().identification());
    }

    @Test
    void thisPassIntroducesNoPersistentKnowledgeStateBeyondExistingIdentificationStatus() {
        // TooltipKnowledgeView is a plain record wrapping IdentificationStatus — nothing here
        // stores or mutates state; it only interprets a value the item already persists.
        TooltipKnowledgeView view = new TooltipKnowledgeView(IdentificationStatus.PARTIALLY);
        assertEquals(IdentificationStatus.PARTIALLY, view.identification());
    }

    // ── Final correction pass, Finding 1: explicit component values are always honored exactly ──
    // {@code of(ItemStack)} itself cannot be exercised here (needs a real ItemStack — see class
    // Javadoc), but every explicit value it could ever wrap is a plain construction of this
    // record, so the "honored exactly as authored" contract is fully provable here.

    @Test
    void explicitUnidentifiedIsHonoredExactly() {
        TooltipKnowledgeView view = new TooltipKnowledgeView(IdentificationStatus.UNIDENTIFIED);
        assertEquals(IdentificationStatus.UNIDENTIFIED, view.identification());
        assertFalse(view.isVisible(TooltipVisibility.WHEN_IDENTIFIED, TooltipDisclosureLevel.DEFAULT));
        assertFalse(view.isVisible(TooltipVisibility.WHEN_RECOGNIZED, TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void explicitPartiallyIdentifiedIsHonoredExactly() {
        TooltipKnowledgeView view = new TooltipKnowledgeView(IdentificationStatus.PARTIALLY);
        assertEquals(IdentificationStatus.PARTIALLY, view.identification());
        assertTrue(view.isVisible(TooltipVisibility.WHEN_RECOGNIZED, TooltipDisclosureLevel.DEFAULT));
        assertFalse(view.isVisible(TooltipVisibility.WHEN_IDENTIFIED, TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void explicitIdentifiedIsHonoredExactly() {
        TooltipKnowledgeView view = new TooltipKnowledgeView(IdentificationStatus.IDENTIFIED);
        assertEquals(IdentificationStatus.IDENTIFIED, view.identification());
        assertTrue(view.isVisible(TooltipVisibility.WHEN_IDENTIFIED, TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void identifiedOnlyFilteringStillWorksAgainstAnExplicitUnidentifiedContext() {
        // The exact mechanism a future Identification API would rely on: an explicitly
        // unidentified context still correctly hides WHEN_IDENTIFIED content.
        TooltipKnowledgeView explicitlyUnidentified = new TooltipKnowledgeView(IdentificationStatus.UNIDENTIFIED);
        assertFalse(explicitlyUnidentified.isVisible(TooltipVisibility.WHEN_IDENTIFIED, TooltipDisclosureLevel.DEFAULT));
        assertTrue(TooltipKnowledgeView.identified().isVisible(TooltipVisibility.WHEN_IDENTIFIED, TooltipDisclosureLevel.DEFAULT));
    }
}

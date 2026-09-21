package zcylas.totality.client.tooltip;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.client.tooltip.section.TooltipSection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the locked unidentified-item policy against the actual section definitions the
 * migrated contributors construct — not just the enum in isolation (already covered by
 * {@link TooltipKnowledgeViewTest}). Each test builds a {@link TooltipSection} exactly the way
 * the real contributor does (same section type, same {@code withVisibility} call) and checks it
 * against both an unidentified and an identified {@link TooltipKnowledgeView}.
 *
 * <p><b>Evidentiary limit:</b> this proves the section-plus-knowledge-view <em>mechanism</em>
 * works correctly for the exact shapes the contributors use. It cannot invoke the contributors'
 * {@code contribute(TooltipContext)} methods themselves (that needs a real {@code ItemStack},
 * which cannot be constructed under plain JUnit here — see
 * {@code HealingPotionItemContractTest}'s class Javadoc). That the contributors actually
 * construct sections this way is instead confirmed by source-regression sentinels in
 * {@link TooltipApiFoundationSourceRegressionTest}.
 */
class TooltipIdentificationVisibilityTest {

    private static final TooltipKnowledgeView UNIDENTIFIED = TooltipKnowledgeView.unidentified();
    private static final TooltipKnowledgeView IDENTIFIED = TooltipKnowledgeView.identified();

    @Test
    void exactRarityIsHiddenUntilIdentified() {
        // Mirrors MetadataContributor: new RarityBadge(rarity).withVisibility(WHEN_IDENTIFIED)
        var section = new TooltipSection.RarityBadge(ItemRarity.EPIC)
                .withVisibility(TooltipVisibility.WHEN_IDENTIFIED);

        assertFalse(UNIDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
        assertTrue(IDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void loreAndHistoryIsHiddenUntilIdentified() {
        // Mirrors MetadataContributor: new Description(text).withVisibility(WHEN_IDENTIFIED)
        var section = new TooltipSection.Description("A blade that remembers every target it has touched.")
                .withVisibility(TooltipVisibility.WHEN_IDENTIFIED);

        assertFalse(UNIDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
        assertTrue(IDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void attunementRequirementLineIsHiddenUntilIdentified() {
        // Mirrors AttunementContributor: new Requirement("Requires Attunement", false).withVisibility(WHEN_IDENTIFIED)
        var section = new TooltipSection.Requirement("Requires Attunement", false)
                .withVisibility(TooltipVisibility.WHEN_IDENTIFIED);

        assertFalse(UNIDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
        assertTrue(IDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void attunementStatusRowIsHiddenUntilIdentified() {
        // Mirrors AttunementContributor's "Attuned"/"Not Attuned" StatRow
        var section = new TooltipSection.StatRow(null, 0xFFFF5555, "Attunement", "Not Attuned", 0xFFFF5555)
                .withVisibility(TooltipVisibility.WHEN_IDENTIFIED);

        assertFalse(UNIDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
        assertTrue(IDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void attunementGatedAcBonusLineIsHiddenUntilIdentified() {
        // Mirrors AttunementContributor's "+1 AC Bonus" Requirement line
        var section = new TooltipSection.Requirement("+1 AC Bonus, +1 Save Bonus", false)
                .withVisibility(TooltipVisibility.WHEN_IDENTIFIED);

        assertFalse(UNIDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
        assertTrue(IDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void grimoireSelectedSpellStateIsHiddenUntilIdentified() {
        // Mirrors GrimoireContributor's "Active"/"No spell active" StatRow
        var section = new TooltipSection.StatRow(null, 0xFFAAAAFF, "Active", "Fireball", 0xFFAAAAFF)
                .withVisibility(TooltipVisibility.WHEN_IDENTIFIED);

        assertFalse(UNIDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
        assertTrue(IDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void grimoireTierStaysVisibleWhileUnidentified() {
        // Tier is an obvious physical fact (a thicker, more ornate tome) — GrimoireContributor
        // does NOT gate it; it uses the default ALWAYS visibility.
        var section = new TooltipSection.StatRow(null, 0xFF9966FF, "Tier", "III", 0xFF9966FF);
        assertEquals(TooltipVisibility.ALWAYS, section.visibility());
        assertTrue(UNIDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void basicWeaponDamageRemainsVisibleWhileUnidentified() {
        // Mirrors WeaponContributor's Damage StatRow — never gated, an obvious physical fact.
        var section = new TooltipSection.StatRow(null, 0xFFE08060, "Damage", "2d8 Piercing (STR or DEX)", 0xFFE08060);
        assertEquals(TooltipVisibility.ALWAYS, section.visibility());
        assertTrue(UNIDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT),
                "basic weapon damage must remain visible even on an unidentified weapon");
    }

    @Test
    void basicWeaponPropertiesRemainVisibleWhileUnidentified() {
        var section = new TooltipSection.PropertyBadges(java.util.List.of("Finesse", "Light"));
        assertEquals(TooltipVisibility.ALWAYS, section.visibility());
        assertTrue(UNIDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void preservedExternalContentIsHiddenUntilIdentified() {
        // Mirrors ExternalContentContributor — a conservative, uniform gate (no per-line
        // recognition), chosen so preserved third-party lines cannot bypass identification.
        var section = new TooltipSection.ExternalContent(java.util.List.of())
                .withVisibility(TooltipVisibility.WHEN_IDENTIFIED);

        assertFalse(UNIDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
        assertTrue(IDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
    }

    @Test
    void classificationBadgesRemainVisibleWhileUnidentified() {
        // "This is a weapon" is an obvious, observable fact, not identified-only.
        var section = new TooltipSection.ClassificationBadges(
                java.util.List.of(zcylas.totality.api.core.rpgutils.rarity.ItemType.WEAPON));
        assertEquals(TooltipVisibility.ALWAYS, section.visibility());
        assertTrue(UNIDENTIFIED.isVisible(section.visibility(), TooltipDisclosureLevel.DEFAULT));
    }
}

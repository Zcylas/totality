package zcylas.totality.client.tooltip;

import org.junit.jupiter.api.Test;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for {@link TotalityTooltipRenderer#orderedBody}, the final-correction-pass
 * replacement for the old global {@code kindRank} sort. Every section here is a plain
 * {@link TooltipSection} record constructed directly (no {@code ItemStack} or {@code Font}
 * involved) and every {@link TotalityTooltipRenderer.ContributorBlock} mirrors exactly what a
 * real contributor's filtered output looks like — proving the grouping algorithm itself, not the
 * contributors that feed it (those are covered by {@link TooltipApiFoundationSourceRegressionTest}
 * sentinels, since {@code contribute(TooltipContext)} needs a real {@code ItemStack}).
 */
class TotalityTooltipRendererGroupingTest {

    @Test
    void netheriteShurikenOrderIsWeaponThenAttunementThenLore() {
        // Mirrors WeaponContributor's own internal emission order exactly.
        var weaponHeading = new TooltipSection.Heading("Weapon");
        var damage = new TooltipSection.StatRow(null, 0xFFE08060, "Damage", "2d8 Piercing (STR or DEX)", 0xFFE08060);
        var properties = new TooltipSection.PropertyBadges(List.of("Finesse", "Thrown"));
        var category = new TooltipSection.StatRow(null, 0xFF6B7280, "Category", "Simple", 0xFF6B7280);
        var range = new TooltipSection.StatRow(null, 0xFF42A5F5, "Range", "20 / 60 ft.", 0xFF42A5F5);
        var stamina = new TooltipSection.StatRow(null, 0xFF66BB6A, "Stamina Cost", "10", 0xFF66BB6A);
        var weaponBlock = new TotalityTooltipRenderer.ContributorBlock(
                TooltipSectionGroup.PRIMARY, List.of(weaponHeading, damage, properties, category, range, stamina));

        // Mirrors AttunementContributor's own internal emission order for an attuned item with no bonus.
        var requiresAttunement = new TooltipSection.Requirement("Requires Attunement", false);
        var attunementStatus = new TooltipSection.StatRow(null, 0xFFFFD700, "Attunement", "Attuned", 0xFFFFD700);
        var attunementBlock = new TotalityTooltipRenderer.ContributorBlock(
                TooltipSectionGroup.ATTUNEMENT, List.of(requiresAttunement, attunementStatus));

        var lore = new TooltipSection.Description("A blade forged in shadow.");
        var loreBlock = new TotalityTooltipRenderer.ContributorBlock(TooltipSectionGroup.LORE, List.of(lore));

        var external = new TooltipSection.ExternalContent(List.of());
        var externalBlock = new TotalityTooltipRenderer.ContributorBlock(TooltipSectionGroup.EXTERNAL, List.of(external));

        // Deliberately fed out of final order, to prove the group ordinal (not input order)
        // decides placement.
        List<TooltipSection> body = TotalityTooltipRenderer.orderedBody(
                List.of(externalBlock, loreBlock, attunementBlock, weaponBlock));

        assertEquals(List.of(weaponHeading, damage, properties, category, range, stamina,
                requiresAttunement, attunementStatus, lore, external), body);
    }

    @Test
    void ringOfProtectionOrderIsAcBonusThenRequiresAttunementThenStatusThenLore() {
        // Mirrors AttunementContributor's own internal emission order exactly: the AC/save bonus
        // Requirement is emitted before "Requires Attunement", which is emitted before the
        // Attuned/Not Attuned StatRow — all in ONE block, so this ordering is preserved by
        // construction regardless of grouping.
        var bonus = new TooltipSection.Requirement("+1 AC Bonus, +1 Save Bonus", false);
        var requiresAttunement = new TooltipSection.Requirement("Requires Attunement", false);
        var status = new TooltipSection.StatRow(null, 0xFFFF5555, "Attunement", "Not Attuned", 0xFFFF5555);
        var attunementBlock = new TotalityTooltipRenderer.ContributorBlock(
                TooltipSectionGroup.ATTUNEMENT, List.of(bonus, requiresAttunement, status));

        var lore = new TooltipSection.Description("A simple silver band, humming faintly.");
        var loreBlock = new TotalityTooltipRenderer.ContributorBlock(TooltipSectionGroup.LORE, List.of(lore));

        List<TooltipSection> body = TotalityTooltipRenderer.orderedBody(List.of(loreBlock, attunementBlock));

        assertEquals(List.of(bonus, requiresAttunement, status, lore), body);
    }

    @Test
    void eachHeadingStaysAttachedToItsOwnRowsRatherThanBeingGroupedWithOtherHeadings() {
        // A synthetic contributor whose single block emits TWO headings, each followed by its own
        // row — proving grouping never collects "all Heading sections" together the way the old
        // global kindRank sort effectively did (Heading always ranked 3, ahead of every StatRow).
        var headingA = new TooltipSection.Heading("Section A");
        var rowA = new TooltipSection.StatRow(null, 0xFFFFFFFF, "Alpha", "1", 0xFFFFFFFF);
        var headingB = new TooltipSection.Heading("Section B");
        var rowB = new TooltipSection.StatRow(null, 0xFFFFFFFF, "Beta", "2", 0xFFFFFFFF);
        var syntheticBlock = new TotalityTooltipRenderer.ContributorBlock(
                TooltipSectionGroup.PRIMARY, List.of(headingA, rowA, headingB, rowB));

        var externalRow = new TooltipSection.ExternalContent(List.of());
        var externalBlock = new TotalityTooltipRenderer.ContributorBlock(TooltipSectionGroup.EXTERNAL, List.of(externalRow));

        List<TooltipSection> body = TotalityTooltipRenderer.orderedBody(List.of(externalBlock, syntheticBlock));

        assertEquals(List.of(headingA, rowA, headingB, rowB, externalRow), body,
                "headingA must stay immediately before rowA, and headingB immediately before rowB — "
                        + "not both headings grouped ahead of both rows");
    }

    @Test
    void blocksSharingAGroupPreserveContributorRegistrationOrder() {
        // Two independent blocks both declaring the same group — the stable sort must not
        // reorder them relative to each other; the first-registered contributor's block stays first.
        var first = new TooltipSection.StatRow(null, 0, "First", "1", 0);
        var second = new TooltipSection.StatRow(null, 0, "Second", "2", 0);
        var blockA = new TotalityTooltipRenderer.ContributorBlock(TooltipSectionGroup.RESOURCES, List.of(first));
        var blockB = new TotalityTooltipRenderer.ContributorBlock(TooltipSectionGroup.RESOURCES, List.of(second));

        List<TooltipSection> body = TotalityTooltipRenderer.orderedBody(List.of(blockA, blockB));

        assertEquals(List.of(first, second), body);
    }

    @Test
    void externalAndTechnicalGroupsAlwaysSortAfterEverySemanticGroup() {
        var technical = new TotalityTooltipRenderer.ContributorBlock(
                TooltipSectionGroup.TECHNICAL, List.of(new TooltipSection.TechnicalInfo(List.of("Registry: totality:test"))));
        var external = new TotalityTooltipRenderer.ContributorBlock(
                TooltipSectionGroup.EXTERNAL, List.of(new TooltipSection.ExternalContent(List.of())));
        var primary = new TotalityTooltipRenderer.ContributorBlock(
                TooltipSectionGroup.PRIMARY, List.of(new TooltipSection.Heading("X")));
        var lore = new TotalityTooltipRenderer.ContributorBlock(
                TooltipSectionGroup.LORE, List.of(new TooltipSection.Description("flavor")));

        List<TooltipSection> body = TotalityTooltipRenderer.orderedBody(List.of(technical, external, lore, primary));

        assertTrue(body.get(0) instanceof TooltipSection.Heading);
        assertTrue(body.get(1) instanceof TooltipSection.Description);
        assertTrue(body.get(2) instanceof TooltipSection.ExternalContent);
        assertTrue(body.get(3) instanceof TooltipSection.TechnicalInfo);
    }

    @Test
    void emptyBlockListProducesAnEmptyBody() {
        assertTrue(TotalityTooltipRenderer.orderedBody(List.of()).isEmpty());
    }
}

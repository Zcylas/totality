package zcylas.totality.client.tooltip;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.core.rpgutils.rarity.Classification;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.client.tooltip.TotalityTooltipRenderer.BodyPart;
import zcylas.totality.client.tooltip.TotalityTooltipRenderer.ContributorBlock;
import zcylas.totality.client.tooltip.group.TooltipGroup;
import zcylas.totality.client.tooltip.group.TooltipGroups;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The semantic body-group merge/order/heading logic, on plain section records (no ItemStack or Font). */
class TotalityTooltipRendererBodyGroupsTest {

    private static final TooltipSection DAMAGE = row("Mining Damage", "100");
    private static final TooltipSection DAMAGE_SHIFT = new TooltipSection.ProvenanceGroup(List.of(
            new TooltipSection.ProvenanceLine("Base", "80"), new TooltipSection.ProvenanceLine("Impact", "+20")));
    private static final TooltipSection SPEED = row("Mining Speed", "2.5/s");
    private static final TooltipSection SPEED_SHIFT = new TooltipSection.ProvenanceGroup(List.of(
            new TooltipSection.ProvenanceLine("Base", "2.5/s")));
    private static final TooltipSection LORE = new TooltipSection.Description("flavor");

    private static TooltipSection row(String label, String value) {
        return new TooltipSection.StatRow(null, 0xFFFFFFFF, label, value, 0xFFFFFFFF);
    }

    private static ContributorBlock grouped(TooltipGroup group, TooltipSection... sections) {
        return new ContributorBlock(TooltipSectionGroup.PRIMARY, group, List.of(sections));
    }

    private static List<TooltipSection> body(List<ContributorBlock> blocks, List<Identifier> order, ItemType primary) {
        return TotalityTooltipRenderer.flattenWithHeadings(TotalityTooltipRenderer.groupedBody(blocks, order, primary));
    }

    private static long headings(List<TooltipSection> body) {
        return body.stream().filter(s -> s instanceof TooltipSection.GroupHeading).count();
    }

    @Test
    void aSinglePopulatedGroupKeepsItsHeading() {
        TooltipSection durability = row("Block Durability", "100");
        List<TooltipSection> body = body(List.of(grouped(TooltipGroups.PROPERTIES, durability)), List.of(), null);
        assertEquals(List.of(new TooltipSection.GroupHeading(TooltipGroups.PROPERTIES), durability), body);
    }

    @Test
    void emptyGroupsAreOmittedAndAnItemWithoutBodyContentHasNoHeading() {
        assertEquals(List.of(), body(List.of(grouped(TooltipGroups.MINING)), List.of(), null));
        assertEquals(List.of(), body(List.of(), List.of(), null));
        List<TooltipSection> loreOnly = body(List.of(new ContributorBlock(TooltipSectionGroup.LORE, List.of(LORE))), List.of(), null);
        assertEquals(List.of(LORE), loreOnly, "unheaded content never gets a placeholder heading");
    }

    @Test
    void contributorsTargetingTheSameGroupMergeUnderOneHeadingInRegistrationOrder() {
        TooltipSection extra = row("Force Tolerance", "1.5");
        List<TooltipSection> body = body(List.of(
                grouped(TooltipGroups.MINING, DAMAGE, DAMAGE_SHIFT),
                grouped(TooltipGroups.COMBAT, row("Damage", "1d8")),
                grouped(TooltipGroups.MINING, extra)), List.of(), null);
        assertEquals(2, headings(body), "one Mining heading, one Combat heading — never a duplicate");
        assertEquals(List.of(new TooltipSection.GroupHeading(TooltipGroups.MINING), DAMAGE, DAMAGE_SHIFT, extra),
                body.subList(0, 4));
    }

    @Test
    void shiftBreakdownsStayDirectlyUnderTheirOwnEntryWhenGroupsMerge() {
        List<TooltipSection> body = body(List.of(
                grouped(TooltipGroups.MINING, DAMAGE, DAMAGE_SHIFT),
                grouped(TooltipGroups.MINING, SPEED, SPEED_SHIFT)), List.of(), null);
        assertEquals(body.indexOf(DAMAGE) + 1, body.indexOf(DAMAGE_SHIFT));
        assertEquals(body.indexOf(SPEED) + 1, body.indexOf(SPEED_SHIFT));
    }

    @Test
    void anEntryAlreadyInTheGroupIsNotShownTwice() {
        TooltipSection weight = row("Weight", "2 lb");
        List<TooltipSection> body = body(List.of(
                grouped(TooltipGroups.PROPERTIES, weight), grouped(TooltipGroups.PROPERTIES, weight)), List.of(), null);
        assertEquals(1, body.stream().filter(weight::equals).count());
    }

    @Test
    void groupsAreOrderedByClassificationThenPriorityAndUnheadedContentFollows() {
        List<ContributorBlock> blocks = List.of(
                new ContributorBlock(TooltipSectionGroup.LORE, List.of(LORE)),
                grouped(TooltipGroups.PROPERTIES, row("Weight", "2")),
                grouped(TooltipGroups.COMBAT, row("Damage", "1d8")),
                grouped(TooltipGroups.MINING, DAMAGE));
        List<BodyPart> parts = TotalityTooltipRenderer.groupedBody(blocks, List.of(), ItemType.WEAPON);
        assertEquals(TooltipGroups.COMBAT, parts.get(0).group());
        assertEquals(TooltipGroups.MINING, parts.get(1).group());
        assertEquals(TooltipGroups.PROPERTIES, parts.get(2).group());
        assertNull(parts.get(3).group());
        assertEquals(List.of(LORE), parts.get(3).sections());
    }

    @Test
    void anAuthoredOrderOverrideIsApplied() {
        List<BodyPart> parts = TotalityTooltipRenderer.groupedBody(List.of(
                grouped(TooltipGroups.MINING, DAMAGE), grouped(TooltipGroups.MAGIC, row("Tier", "I"))),
                List.of(TooltipGroups.MAGIC.id()), ItemType.TOOL);
        assertEquals(List.of(TooltipGroups.MAGIC, TooltipGroups.MINING), parts.stream().map(BodyPart::group).toList());
    }

    @Test
    void theOrderIsDeterministicRegardlessOfContributorInputOrder() {
        ContributorBlock mining = grouped(TooltipGroups.MINING, DAMAGE);
        ContributorBlock combat = grouped(TooltipGroups.COMBAT, row("Damage", "1d8"));
        assertEquals(body(List.of(mining, combat), List.of(), null), body(List.of(combat, mining), List.of(), null));
    }

    @Test
    void legacyUngroupedBlocksKeepTheirPreviousOrder() {
        TooltipSection external = new TooltipSection.ExternalContent(List.of());
        TooltipSection technical = new TooltipSection.TechnicalInfo(List.of("id"));
        List<TooltipSection> body = body(List.of(
                new ContributorBlock(TooltipSectionGroup.TECHNICAL, List.of(technical)),
                new ContributorBlock(TooltipSectionGroup.EXTERNAL, List.of(external)),
                new ContributorBlock(TooltipSectionGroup.LORE, List.of(LORE))), List.of(), null);
        assertEquals(List.of(LORE, external, technical), body);
    }

    @Test
    void theUnheadedTailStartsRightAfterTheLastGroupSoLoreIsSeparatedFromIt() {
        List<BodyPart> parts = TotalityTooltipRenderer.groupedBody(List.of(
                grouped(TooltipGroups.MINING, DAMAGE, DAMAGE_SHIFT),
                new ContributorBlock(TooltipSectionGroup.LORE, List.of(LORE))), List.of(), null);
        List<TooltipSection> body = TotalityTooltipRenderer.flattenWithHeadings(parts);
        int tailStart = TotalityTooltipRenderer.headedLength(parts);
        assertEquals(3, tailStart, "heading + 2 entries");
        assertEquals(LORE, body.get(tailStart));
        assertEquals(0, TotalityTooltipRenderer.headedLength(TotalityTooltipRenderer.groupedBody(
                List.of(new ContributorBlock(TooltipSectionGroup.LORE, List.of(LORE))), List.of(), null)),
                "no groups -> no gap before lore");
    }

    @Test
    void requirementsThenEnergyThenDurabilityCloseTheBodyBeforeLore() {
        List<BodyPart> parts = TotalityTooltipRenderer.groupedBody(List.of(
                grouped(TooltipGroups.DURABILITY, row("wear", "90%")),
                grouped(TooltipGroups.ENERGY, row("charge", "50%")),
                new ContributorBlock(TooltipSectionGroup.LORE, List.of(LORE)),
                grouped(TooltipGroups.REQUIREMENTS, row("Attunement", "Free")),
                grouped(TooltipGroups.MINING, DAMAGE)), List.of(TooltipGroups.DURABILITY.id()), ItemType.TOOL);
        assertEquals(java.util.Arrays.asList(TooltipGroups.MINING, TooltipGroups.REQUIREMENTS, TooltipGroups.ENERGY,
                TooltipGroups.DURABILITY, null), parts.stream().map(BodyPart::group).toList());
    }

    @Test
    void anItemWithOnlyOneResourceShowsOnlyThatResource() {
        List<BodyPart> parts = TotalityTooltipRenderer.groupedBody(List.of(
                grouped(TooltipGroups.DURABILITY, row("wear", "90%"))), List.of(), null);
        assertEquals(List.of(TooltipGroups.DURABILITY), parts.stream().map(BodyPart::group).toList());
    }

    @Test
    void thePrimaryCategoryIsTheFirstAuthoredClassification() {
        assertEquals(ItemType.TOOL, TotalityTooltipRenderer.primaryCategory(
                List.of(Classification.of(ItemType.TOOL), Classification.of(ItemType.WEAPON))));
        assertNull(TotalityTooltipRenderer.primaryCategory(List.of()));
    }
}

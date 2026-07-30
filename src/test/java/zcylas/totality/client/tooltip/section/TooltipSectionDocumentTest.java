package zcylas.totality.client.tooltip.section;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.TooltipDocument;
import zcylas.totality.client.tooltip.TooltipVisibility;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for the semantic section/document model — no {@code ItemStack} or Minecraft
 * bootstrap involved; every section here is a plain record constructed directly.
 */
class TooltipSectionDocumentTest {

    @Test
    void statRowDefaultsToAlwaysVisibleAndDefaultDisclosure() {
        var row = new TooltipSection.StatRow(null, 0xFFFFFF, "Weight", "2.0", 0xFFFFFF);
        assertEquals(TooltipVisibility.ALWAYS, row.visibility());
        assertEquals(TooltipDisclosureLevel.DEFAULT, row.minDisclosure());
    }

    @Test
    void statRowWithMinDisclosureProducesANewImmutableInstance() {
        var row = new TooltipSection.StatRow(null, 0xFFFFFF, "Energy", "5M", 0xFFFFFF);
        var detailsOnly = row.withMinDisclosure(TooltipDisclosureLevel.DETAILS);

        assertEquals(TooltipDisclosureLevel.DEFAULT, row.minDisclosure(), "original row must be unchanged");
        assertEquals(TooltipDisclosureLevel.DETAILS, detailsOnly.minDisclosure());
        assertEquals(row.label(), detailsOnly.label());
    }

    @Test
    void technicalInfoDefaultsToTechnicalVisibilityAndDisclosure() {
        var info = new TooltipSection.TechnicalInfo(List.of("Registry: totality:copper_battery"));
        assertEquals(TooltipVisibility.TECHNICAL, info.visibility());
        assertEquals(TooltipDisclosureLevel.TECHNICAL, info.minDisclosure());
    }

    @Test
    void classificationBadgesPreservesAuthoredOrder() {
        var badges = new TooltipSection.ClassificationBadges(List.of(ItemType.WEAPON, ItemType.MAGICAL));
        assertEquals(List.of(ItemType.WEAPON, ItemType.MAGICAL), badges.classifications());
    }

    @Test
    void classificationBadgesIsImmutableAgainstLaterMutationOfTheConstructorArgument() {
        var mutable = new java.util.ArrayList<>(List.of(ItemType.BATTERY, ItemType.ENERGY));
        var badges = new TooltipSection.ClassificationBadges(mutable);
        mutable.add(ItemType.MAGICAL);
        assertEquals(List.of(ItemType.BATTERY, ItemType.ENERGY), badges.classifications());
    }

    @Test
    void rarityBadgeCarriesTheAuthoredRarity() {
        var badge = new TooltipSection.RarityBadge(ItemRarity.ANCIENT);
        assertEquals(ItemRarity.ANCIENT, badge.rarity());
    }

    @Test
    void documentBuilderPreservesInsertionOrder() {
        var doc = TooltipDocument.builder()
                .add(new TooltipSection.Heading("Weapon"))
                .add(new TooltipSection.StatRow(null, 0, "Damage", "2d8", 0))
                .add(new TooltipSection.Description("Flavor text"))
                .build();

        assertEquals(3, doc.sections().size());
        assertInstanceOf(TooltipSection.Heading.class, doc.sections().get(0));
        assertInstanceOf(TooltipSection.StatRow.class, doc.sections().get(1));
        assertInstanceOf(TooltipSection.Description.class, doc.sections().get(2));
    }

    @Test
    void documentSectionsListIsImmutable() {
        var doc = TooltipDocument.builder().add(new TooltipSection.Heading("X")).build();
        assertThrows(UnsupportedOperationException.class, () -> doc.sections().add(new TooltipSection.Heading("Y")));
    }

    @Test
    void addAllAppendsInOrder() {
        var doc = TooltipDocument.builder()
                .add(new TooltipSection.Heading("A"))
                .addAll(List.of(new TooltipSection.Heading("B"), new TooltipSection.Heading("C")))
                .build();
        assertEquals(3, doc.sections().size());
    }
}

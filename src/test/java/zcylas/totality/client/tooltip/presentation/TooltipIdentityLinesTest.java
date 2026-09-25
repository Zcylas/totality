package zcylas.totality.client.tooltip.presentation;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.core.rpgutils.rarity.Classification;
import zcylas.totality.api.core.rpgutils.rarity.ClassificationTypes;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.api.core.rpgutils.rarity.RarityFamily;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V2 identity block text. Successor of the V1 badge-row source checks: same intent — rarity is shown
 * explicitly and first, classifications follow in authored order, nothing is fabricated — tested on the
 * actual output rather than on source text.
 */
class TooltipIdentityLinesTest {

    @Test
    void rarityLineIsTheExplicitUppercaseRarityName() {
        assertEquals("EPIC", TooltipIdentityLines.rarityLine(ItemRarity.EPIC));
        assertEquals("ANCIENT", TooltipIdentityLines.rarityLine(ItemRarity.ANCIENT));
        assertEquals("GODFORGED", TooltipIdentityLines.rarityLine(ItemRarity.GODFORGED));
    }

    @Test
    void anItemWithoutAuthoredRarityGetsNoRarityLine() {
        assertEquals("", TooltipIdentityLines.rarityLine(null));
    }

    @Test
    void categoryTypeLineKeepsAuthoredOrderWithACentredBullet() {
        assertEquals("BATTERY • ENERGY",
                TooltipIdentityLines.categoryTypeLine(List.of(ItemType.BATTERY, ItemType.ENERGY)));
        assertEquals("ENERGY • BATTERY",
                TooltipIdentityLines.categoryTypeLine(List.of(ItemType.ENERGY, ItemType.BATTERY)));
    }

    @Test
    void aSingleClassificationShowsOneValueWithoutFabricatingASecond() {
        assertEquals("WEAPON", TooltipIdentityLines.categoryTypeLine(List.of(ItemType.WEAPON)));
        assertEquals("", TooltipIdentityLines.categoryTypeLine(List.of()));
    }

    @Test
    void duplicateClassificationsAreNotRepeated() {
        assertEquals("MAGICAL", TooltipIdentityLines.categoryTypeLine(List.of(ItemType.MAGICAL, ItemType.MAGICAL)));
    }

    /** Stand-in for the localized type name (the lang file: Axe, Two-Handed, ...). */
    private static final Function<Identifier, String> NAMES = id -> switch (id.getPath()) {
        case "axe" -> "Axe";
        case "two_handed" -> "Two-Handed";
        default -> id.getPath();
    };

    @Test
    void aSingleClassificationPairIsOneCentredCategoryTypeLine() {
        assertEquals(List.of("TOOL • AXE"), TooltipIdentityLines.classificationLines(
                List.of(Classification.of(ItemType.TOOL, ClassificationTypes.AXE)), NAMES));
    }

    @Test
    void multiplePairsEachGetTheirOwnLineInAuthoredOrder() {
        assertEquals(List.of("TOOL • AXE", "WEAPON • TWO-HANDED"), TooltipIdentityLines.classificationLines(List.of(
                Classification.of(ItemType.TOOL, ClassificationTypes.AXE),
                Classification.of(ItemType.WEAPON, ClassificationTypes.TWO_HANDED)), NAMES));
    }

    @Test
    void legacyCategoryOnlyDataKeepsItsExistingSingleLine() {
        assertEquals(List.of("BATTERY • ENERGY"), TooltipIdentityLines.classificationLines(
                List.of(Classification.of(ItemType.BATTERY), Classification.of(ItemType.ENERGY)), NAMES));
        assertEquals(List.of("WEAPON"), TooltipIdentityLines.classificationLines(List.of(Classification.of(ItemType.WEAPON)), NAMES));
    }

    @Test
    void categoryOnlyEntriesAreGroupedWhereTheFirstOfThemWasAuthored() {
        assertEquals(List.of("MAGICAL • RITUAL", "TOOL • AXE"), TooltipIdentityLines.classificationLines(List.of(
                Classification.of(ItemType.MAGICAL),
                Classification.of(ItemType.TOOL, ClassificationTypes.AXE),
                Classification.of(ItemType.RITUAL)), NAMES));
    }

    @Test
    void noClassificationsMeansNoLinesAndNoPlaceholder() {
        assertEquals(List.of(), TooltipIdentityLines.classificationLines(List.of(), NAMES));
    }

    @Test
    void duplicatePairsAreShownOnce() {
        Classification axe = Classification.of(ItemType.TOOL, ClassificationTypes.AXE);
        assertEquals(List.of("TOOL • AXE"), TooltipIdentityLines.classificationLines(List.of(axe, axe), NAMES));
    }

    @Test
    void standardLadderEndsAtAncientAndArtifactIsNotARarity() {
        List<ItemRarity> standard = Arrays.stream(ItemRarity.values())
                .filter(r -> r.family() == RarityFamily.STANDARD).toList();
        assertEquals(List.of(ItemRarity.COMMON, ItemRarity.UNCOMMON, ItemRarity.RARE, ItemRarity.EPIC,
                ItemRarity.LEGENDARY, ItemRarity.MYTHICAL, ItemRarity.ANCIENT), standard);
        assertTrue(Arrays.stream(ItemRarity.values()).noneMatch(r -> r.name().equals("ARTIFACT")));
    }
}

package zcylas.totality.client.tooltip.renderer;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.client.tooltip.theme.TooltipColors;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.client.tooltip.renderer.TooltipGroupHeadingPainter.*;

class TooltipGroupHeadingPainterTest {

    @Test
    void iconAndTitleAreCentredAsOneUnitWithEqualLinesOnBothSides() {
        int x = 10, width = 180, iconW = 7, titleW = 40;
        Layout l = layout(x, width, iconW, titleW);
        assertEquals(iconW + ICON_GAP + titleW, l.unitW());
        int leftSpace = l.unitX() - x, rightSpace = x + width - (l.unitX() + l.unitW());
        assertTrue(Math.abs(leftSpace - rightSpace) <= 1, "unit centred");
        assertEquals(l.lineLength(), Math.min(leftSpace, rightSpace) - LINE_GAP, "lines fill the space and are equal");
    }

    @Test
    void theDividerAdaptsToTheAvailableWidth() {
        assertTrue(layout(0, 240, 7, 40).lineLength() > layout(0, 140, 7, 40).lineLength());
    }

    @Test
    void longTitlesAreTruncatedAndNeverOverlapTheLines() {
        int width = 120, iconW = 7;
        Layout l = layout(0, width, iconW, 400);
        assertTrue(l.titleMaxW() < 400);
        assertTrue(l.lineLength() >= MIN_LINE, "the lines keep their minimum length");
        assertTrue(l.unitX() - LINE_GAP - l.lineLength() >= 0, "left line starts inside the content");
        assertTrue(l.unitX() + l.unitW() + LINE_GAP + l.lineLength() <= width, "right line ends inside the content");
        assertEquals(0, layout(0, 10, iconW, 50).titleMaxW(), "degenerate width never goes negative");
    }

    @Test
    void naturalWidthFitsTheFullTitleWithMinimumLines() {
        int natural = naturalWidth(7, 60);
        Layout l = layout(0, natural, 7, 60);
        assertEquals(60, Math.min(60, l.titleMaxW()));
        assertTrue(l.lineLength() >= MIN_LINE);
    }

    @Test
    void linesFadeOutwardFromTheUnitAndCoverTheirWholeLength() {
        for (int i = 1; i < SEGMENTS; i++) assertTrue(segmentAlpha(i) < segmentAlpha(i - 1));
        assertTrue(segmentAlpha(SEGMENTS - 1) > 0f && segmentAlpha(SEGMENTS - 1) < 0.1f);
        List<int[]> segs = segments(50);
        assertEquals(0, segs.get(0)[0]);
        assertEquals(50, segs.get(segs.size() - 1)[1]);
        for (int k = 1; k < segs.size(); k++) assertEquals(segs.get(k - 1)[1], segs.get(k)[0], "contiguous");
    }

    @Test
    void headingColorIsAReadableVariantOfEveryRaritysOwnColor() {
        for (ItemRarity rarity : ItemRarity.values()) {
            int rarityColor = TooltipColors.forRarity(rarity);
            int text = textColor(rarityColor);
            assertEquals(TooltipRarityPlaquePainter.labelColor(rarityColor), text, rarity + " uses the plaque's readable lift");
            assertEquals(0xFF, text >>> 24);
            for (int shift : new int[]{16, 8, 0}) {
                assertTrue(((text >> shift) & 0xFF) >= ((rarityColor >> shift) & 0xFF), rarity + " is lifted, never darkened");
            }
        }
    }

    @Test
    void headingHeightIsTheTextRowPlusConsistentGaps() {
        assertEquals(GAP_ABOVE + TEXT_H + GAP_BELOW, HEIGHT);
    }
}

package zcylas.totality.client.tooltip.renderer;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.client.tooltip.theme.TooltipColors;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TooltipPresentationPaintersTest {

    @Test
    void vignetteTintIsTheRarityColorForEveryRarity() {
        for (ItemRarity rarity : ItemRarity.values()) {
            int rarityColor = TooltipColors.forRarity(rarity);
            int tint = TooltipVignettePainter.withAlpha(rarityColor, TooltipVignettePainter.STRENGTH);
            assertEquals(rarityColor & 0xFFFFFF, tint & 0xFFFFFF, rarity + " vignette must use its own rarity color");
        }
    }

    @Test
    void vignetteStaysSubtleForReadability() {
        int alpha = TooltipVignettePainter.withAlpha(0xFFFFFFFF, TooltipVignettePainter.STRENGTH) >>> 24;
        assertTrue(alpha > 0 && alpha <= 0x60, "peak vignette opacity must stay low, got " + alpha);
    }

    @Test
    void edgeFrameFadesFromTheOuterEdgeToNothing() {
        int depth = TooltipVignettePainter.EDGE_FRAME_DEPTH;
        float previous = Float.MAX_VALUE;
        for (int ring = 0; ring < depth; ring++) {
            float a = TooltipVignettePainter.edgeFrameAlpha(ring, depth);
            assertTrue(a < previous && a >= 0f);
            previous = a;
        }
        assertEquals(0f, TooltipVignettePainter.edgeFrameAlpha(depth, depth));
    }

    @Test
    void gradientDividerIsSymmetricInsideItsBoundsAndFadesOutward() {
        int left = 10, right = 190;
        List<TooltipDividerPainter.Segment> segments = TooltipDividerPainter.gradientSegments(left, right, 0);
        assertFalse(segments.isEmpty());
        int mid = (left + right) / 2;
        for (TooltipDividerPainter.Segment s : segments) {
            assertTrue(s.x0() >= left && s.x1() <= right && s.x0() < s.x1());
            assertTrue(s.alpha() > 0f && s.alpha() <= 1f);
        }
        // strongest next to the centre, weakest at the ends
        float nearCentre = segments.get(0).alpha(), atEnds = segments.get(segments.size() - 1).alpha();
        assertTrue(nearCentre > atEnds);
        // mirrored halves have equal length
        int leftLen = segments.stream().filter(s -> s.x1() <= mid).mapToInt(s -> s.x1() - s.x0()).sum();
        int rightLen = segments.stream().filter(s -> s.x0() >= mid).mapToInt(s -> s.x1() - s.x0()).sum();
        assertEquals(leftLen, rightLen);
    }

    @Test
    void gradientOrnamentLeavesAClearGapForTheCentreOrnament() {
        int left = 10, right = 190, gap = TooltipDividerPainter.ORNAMENT_GAP, mid = (left + right) / 2;
        for (TooltipDividerPainter.Segment s : TooltipDividerPainter.gradientSegments(left, right, gap)) {
            assertTrue(s.x1() <= mid - gap || s.x0() >= mid + gap, "segment " + s + " intrudes on the ornament gap");
        }
    }

    @Test
    void aDividerTooNarrowForItsGapDrawsNoSegments() {
        assertTrue(TooltipDividerPainter.gradientSegments(0, 8, 5).isEmpty());
    }
}

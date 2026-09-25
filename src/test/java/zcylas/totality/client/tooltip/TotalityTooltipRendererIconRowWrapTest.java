package zcylas.totality.client.tooltip;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Final correction pass: a wrapped IconStatRow (e.g. a long "Effective Tool" value on a narrow panel) draws its value below the full
 * icon row and the full wrapped label, so it never sits under the 16px icon sprite or overlaps a label line. Layout and
 * drawing share these helpers, so the reserved height and the drawn positions cannot diverge.
 */
class TotalityTooltipRendererIconRowWrapTest {

    private static final int ICON_SPRITE = 16;

    @Test
    void wrappedValueStartsBelowTheIconSpriteAndTheWholeLabel() {
        for (int rowH : new int[]{9, 10, 12}) {
            for (int labelLines = 1; labelLines <= 4; labelLines++) {
                int labelTop = TotalityTooltipRenderer.iconRowLabelTop(labelLines, rowH);
                int valueTop = TotalityTooltipRenderer.iconRowValueTop(labelLines, rowH);
                assertTrue(labelTop >= 0, "label starts inside its row");
                assertTrue(valueTop >= TotalityTooltipRenderer.iconTop() + ICON_SPRITE, "value clears the icon sprite: " + rowH + "/" + labelLines);
                assertTrue(valueTop >= labelTop + labelLines * rowH, "value clears every label line: " + rowH + "/" + labelLines);
            }
        }
    }

    @Test
    void singleWrappedLabelIsCentredOnTheIconLikeTheOneLineRow() {
        // One-line rows draw text at (18 - lineHeight) / 2; with rowH = lineHeight + 1 the wrapped label lands on the same line.
        int lineHeight = 9;
        assertEquals((18 - lineHeight) / 2, TotalityTooltipRenderer.iconRowLabelTop(1, lineHeight + 1));
    }
}

package zcylas.totality.client.tooltip.renderer;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.RarityFamily;
import zcylas.totality.client.tooltip.theme.TooltipColors;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TooltipRarityPlaquePainterTest {

    @Test
    void plaqueIsSlimRelativeToTheNameLine() {
        // One font line is 9 px; the plaque stays a slim band, not a block.
        assertTrue(TooltipRarityPlaquePainter.HEIGHT >= 10 && TooltipRarityPlaquePainter.HEIGHT <= 13);
    }

    @Test
    void endsTaperOnePixelPerRowToAFlatTwoRowTip() {
        int h = TooltipRarityPlaquePainter.HEIGHT;
        assertEquals(TooltipRarityPlaquePainter.TAPER, TooltipRarityPlaquePainter.inset(0));
        assertEquals(TooltipRarityPlaquePainter.TAPER, TooltipRarityPlaquePainter.inset(h - 1));
        assertEquals(0, TooltipRarityPlaquePainter.inset(h / 2 - 1));
        assertEquals(0, TooltipRarityPlaquePainter.inset(h / 2));
        for (int row = 0; row < h; row++) {
            assertEquals(TooltipRarityPlaquePainter.inset(row), TooltipRarityPlaquePainter.inset(h - 1 - row), "vertically symmetric");
            if (row > 0 && row < h / 2) {
                assertEquals(TooltipRarityPlaquePainter.inset(row - 1) - 1, TooltipRarityPlaquePainter.inset(row), "1px per row");
            }
        }
    }

    @Test
    void widthLeavesEqualPaddingAroundTheLabelAndFlourishesAreExtra() {
        for (int textW : new int[]{20, 21, 57}) {
            int w = TooltipRarityPlaquePainter.width(textW);
            assertEquals(textW + 2 * (TooltipRarityPlaquePainter.TEXT_PAD + TooltipRarityPlaquePainter.TAPER), w);
            assertEquals(w + 2 * TooltipRarityPlaquePainter.FLOURISH, TooltipRarityPlaquePainter.totalWidth(textW));
        }
    }

    @Test
    void everyRaritySchemeGetsTheSameGeometryAndItsOwnExistingColor() {
        Set<RarityFamily> families = EnumSet.noneOf(RarityFamily.class);
        for (ItemRarity rarity : ItemRarity.values()) {
            families.add(rarity.family());
            int rarityColor = TooltipColors.forRarity(rarity);
            int label = TooltipRarityPlaquePainter.labelColor(rarityColor);
            assertEquals(0xFF, label >>> 24, rarity + " label must be opaque");
            // lightened toward white: no channel darker than the rarity's own
            for (int shift : new int[]{16, 8, 0}) {
                assertTrue(((label >> shift) & 0xFF) >= ((rarityColor >> shift) & 0xFF), rarity + " label is a lightened rarity color");
            }
            int fill = TooltipVignettePainter.withAlpha(rarityColor, TooltipRarityPlaquePainter.FILL_ALPHA);
            assertEquals(rarityColor & 0xFFFFFF, fill & 0xFFFFFF, rarity + " plaque fill uses its own rarity color");
        }
        assertEquals(EnumSet.allOf(RarityFamily.class), families, "Standard, Special, Religious and Industrial are all covered");
    }

    @Test
    void darkRarityLabelsAreLiftedForReadability() {
        int cursed = TooltipColors.forRarity(ItemRarity.CURSED);
        int label = TooltipRarityPlaquePainter.labelColor(cursed);
        int lum = ((label >> 16) & 0xFF) + ((label >> 8) & 0xFF) + (label & 0xFF);
        int base = ((cursed >> 16) & 0xFF) + ((cursed >> 8) & 0xFF) + (cursed & 0xFF);
        assertTrue(lum > base + 100, "Cursed's dark crimson label must be noticeably lighter than the raw theme color");
    }
}

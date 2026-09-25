package zcylas.totality.client.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import zcylas.totality.util.color.ColorUtils;

/**
 * The header's rarity plaque: one shared ornamental shape for every rarity scheme — a slim, centred
 * banner with pointed (tapered) ends, a 1px frame, a faint rarity-tinted fill, and a short fading
 * flourish off each point, with the rarity label centred inside. Only the color varies; it is always
 * the caller's already-resolved rarity color ({@code TooltipColors.forRarity}), never a new palette.
 *
 * <p>Geometry is fixed and pure (unit-tested): {@link #HEIGHT} rows; each row is inset from the outer
 * points by {@link #inset(int)}, giving a 1-pixel-per-row diagonal taper that meets in a 2-row tip.
 */
public final class TooltipRarityPlaquePainter {

    /** Plaque height in GUI pixels. Even, so the pointed ends meet in a flat 2-pixel tip. */
    public static final int HEIGHT = 12;
    /** Length of each tapered end — one pixel of inset per row from the tip. */
    static final int TAPER = HEIGHT / 2 - 1;
    /** Clear space between the label and the start of each taper. */
    static final int TEXT_PAD = 4;
    /** Length of the fading flourish drawn outward from each point (outside {@link #width(int)}). */
    static final int FLOURISH = 8;
    static final float FILL_ALPHA = 0.20f;
    static final float FRAME_ALPHA = 0.90f;
    static final float FLOURISH_ALPHA = 0.60f;
    /** How far the label is lightened toward white, so dark rarity colors (Cursed, Forbidden) stay readable. */
    static final float LABEL_LIGHTEN = 0.35f;
    /** Vertical offset of the label inside the plaque (font cap height 7 + shadow, centred in 12). */
    static final int TEXT_Y = 2;

    /** Plaque width, point to point, for a label {@code textW} pixels wide. */
    public static int width(int textW) {
        return textW + 2 * (TEXT_PAD + TAPER);
    }

    /** Width including both flourishes — what the plaque needs horizontally so nothing is clipped. */
    public static int totalWidth(int textW) {
        return width(textW) + 2 * FLOURISH;
    }

    /** Inset of row {@code row} (0 = top) from the plaque's outer points: {@link #TAPER} at top/bottom, 0 at the tip. */
    static int inset(int row) {
        int fromEdge = Math.min(row, HEIGHT - 1 - row);
        return Math.max(0, TAPER - fromEdge);
    }

    /** Label color: the rarity color lightened toward white, fully opaque. */
    static int labelColor(int rarityColor) {
        return ColorUtils.blend(rarityColor | 0xFF000000, 0xFFFFFFFF, LABEL_LIGHTEN);
    }

    /** Draws the plaque centred on {@code centerX} with its top edge at {@code y}. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, String label, int centerX, int y, int rarityColor) {
        int textW = font.width(label);
        int w = width(textW);
        int left = centerX - w / 2;
        int right = left + w;
        int fill = TooltipVignettePainter.withAlpha(rarityColor, FILL_ALPHA);
        int frame = TooltipVignettePainter.withAlpha(rarityColor, FRAME_ALPHA);

        for (int row = 0; row < HEIGHT; row++) {
            int x0 = left + inset(row), x1 = right - inset(row);
            if (row == 0 || row == HEIGHT - 1) {
                graphics.fill(x0, y + row, x1, y + row + 1, frame);
            } else {
                graphics.fill(x0 + 1, y + row, x1 - 1, y + row + 1, fill);
                graphics.fill(x0, y + row, x0 + 1, y + row + 1, frame);
                graphics.fill(x1 - 1, y + row, x1, y + row + 1, frame);
            }
        }

        // Fading flourish off each point, on the tip's two rows.
        int tipY = y + TAPER;
        for (int i = 0; i < FLOURISH; i++) {
            int c = TooltipVignettePainter.withAlpha(rarityColor, FLOURISH_ALPHA * (1f - (i + 0.5f) / FLOURISH));
            graphics.fill(left - 1 - i, tipY, left - i, tipY + 2, c);
            graphics.fill(right + i, tipY, right + i + 1, tipY + 2, c);
        }

        graphics.text(font, label, centerX - textW / 2, y + TEXT_Y, labelColor(rarityColor), true);
    }

    private TooltipRarityPlaquePainter() {}
}

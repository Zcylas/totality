package zcylas.totality.client.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import zcylas.totality.util.color.ColorUtils;

/**
 * A resource section's body under its group heading — the bar, then the centred figures:
 * <pre>
 *   [======================]
 *     31.2k / 48k UE (65%)
 * </pre>
 * Shared by Energy and Durability (and any future resource). The heading itself (icon, title, fading lines)
 * is the group heading, so no second label is drawn here. Geometry is pure and unit-tested.
 */
public final class TooltipResourceGaugePainter {

    /** Bar height including its 1px frame. */
    public static final int BAR_H = 6;
    /** Gap between the bar and the figures. */
    static final int FIGURES_GAP = 2;
    static final int FRAME_COLOR = 0xFF3A3F47;
    static final int TRACK_COLOR = 0xFF15171B;

    /** Total height for figures {@code lineHeight} tall. */
    public static int height(int lineHeight) {
        return BAR_H + FIGURES_GAP + lineHeight + 1;
    }

    /**
     * Filled width of a bar whose inner track is {@code trackW} wide, from the exact amounts. Exactly empty only at
     * zero and exactly full only at the maximum — decided by comparing the {@code long}s, never a rounded fraction;
     * any other amount is clamped to {@code [1, trackW - 1]}, so a nearly empty or nearly full resource is never
     * shown as completely empty or full. The ratio uses {@code double} division, which cannot overflow.
     */
    public static int fillWidth(long current, long max, int trackW) {
        if (trackW <= 0 || max <= 0 || current <= 0) return 0;
        if (current >= max) return trackW;
        int w = (int) Math.round((double) current / max * trackW);
        return Math.max(1, Math.min(trackW - 1, w));
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, long current, long max, int fillColor, String figures,
                            int figuresColor, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + BAR_H, FRAME_COLOR);
        int trackX = x + 1, trackW = width - 2;
        graphics.fill(trackX, y + 1, trackX + trackW, y + BAR_H - 1, TRACK_COLOR);
        int fill = fillWidth(current, max, trackW);
        if (fill > 0) {
            graphics.fill(trackX, y + 1, trackX + fill, y + BAR_H - 1, fillColor);
            // 1px highlight along the top of the fill, so the bar reads as a bar against the vignette.
            graphics.fill(trackX, y + 1, trackX + fill, y + 2, ColorUtils.blend(fillColor | 0xFF000000, 0xFFFFFFFF, 0.25f));
        }
        int textY = y + BAR_H + FIGURES_GAP;
        graphics.text(font, figures, x + (width - font.width(figures)) / 2, textY, figuresColor, true);
    }

    private TooltipResourceGaugePainter() {}
}

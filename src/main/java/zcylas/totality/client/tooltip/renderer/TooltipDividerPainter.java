package zcylas.totality.client.tooltip.renderer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import zcylas.totality.api.core.rpgutils.rarity.TooltipDividerStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * The divider between the identity header and the tooltip body — a generalization of Tooltip V1's
 * separator (two lines with a small centre diamond, formerly {@code TooltipPainter.drawSeparator}), which
 * becomes {@link TooltipDividerStyle#ORNAMENT}. Colors come from the caller's resolved rarity color.
 *
 * <ul>
 *   <li>{@code GRADIENT} — a 1px line, full strength at the centre fading to nothing at both ends;</li>
 *   <li>{@code ORNAMENT} — V1's solid lines with the centre diamond;</li>
 *   <li>{@code GRADIENT_ORNAMENT} — the fading line broken around the centre diamond (the V2 default);</li>
 *   <li>{@code NONE} — nothing, the divider keeps its height.</li>
 * </ul>
 */
public final class TooltipDividerPainter {

    /** Vertical space a divider occupies — matches the renderer's header/body {@code separatorH} (8). */
    public static final int HEIGHT = 8;
    /** Line opacity at its strongest point. */
    static final float LINE_ALPHA = 0.85f;
    /** Half-width of the clear gap around the centre ornament. */
    static final int ORNAMENT_GAP = 5;
    /** Horizontal inset of the line from the content edges. */
    static final int INSET = 4;
    static final int SEGMENTS_PER_SIDE = 16;

    /** One piece of a fading line: {@code [x0, x1)} drawn at {@code alpha} (0..1). */
    record Segment(int x0, int x1, float alpha) {}

    public static void draw(GuiGraphicsExtractor graphics, TooltipDividerStyle style, int x, int y, int width, int rarityColor) {
        int lineY = y + HEIGHT / 2;
        int midX = x + width / 2;
        switch (style) {
            case NONE -> { }
            case GRADIENT -> drawSegments(graphics, gradientSegments(x + INSET, x + width - INSET, 0), lineY, rarityColor);
            case ORNAMENT -> {
                int solid = TooltipVignettePainter.withAlpha(rarityColor, LINE_ALPHA * 0.6f);
                graphics.fill(x + INSET, lineY, midX - ORNAMENT_GAP, lineY + 1, solid);
                graphics.fill(midX + ORNAMENT_GAP, lineY, x + width - INSET, lineY + 1, solid);
                TooltipPainter.drawSmallDiamond(graphics, midX, lineY, rarityColor | 0xFF000000);
            }
            case GRADIENT_ORNAMENT -> {
                drawSegments(graphics, gradientSegments(x + INSET, x + width - INSET, ORNAMENT_GAP), lineY, rarityColor);
                TooltipPainter.drawSmallDiamond(graphics, midX, lineY, rarityColor | 0xFF000000);
            }
        }
    }

    private static void drawSegments(GuiGraphicsExtractor graphics, List<Segment> segments, int lineY, int rarityColor) {
        for (Segment s : segments) {
            graphics.fill(s.x0(), lineY, s.x1(), lineY + 1, TooltipVignettePainter.withAlpha(rarityColor, s.alpha()));
        }
    }

    /**
     * Splits the line {@code [left, right)} into fading segments, symmetric about its centre, leaving a
     * {@code gap}-pixel clearance on each side of the centre. Opacity is highest next to the centre and
     * falls linearly to zero at both ends. Pure — unit-tested.
     */
    static List<Segment> gradientSegments(int left, int right, int gap) {
        List<Segment> out = new ArrayList<>();
        int mid = (left + right) / 2;
        int halfLen = mid - gap - left;
        if (halfLen <= 0) return out;
        for (int i = 0; i < SEGMENTS_PER_SIDE; i++) {
            int a = halfLen * i / SEGMENTS_PER_SIDE;
            int b = halfLen * (i + 1) / SEGMENTS_PER_SIDE;
            if (b <= a) continue;
            float alpha = LINE_ALPHA * (1f - (i + 0.5f) / SEGMENTS_PER_SIDE);
            out.add(new Segment(mid - gap - b, mid - gap - a, alpha));   // left half, outward from centre
            out.add(new Segment(mid + gap + a, mid + gap + b, alpha));   // right half, mirrored
        }
        return out;
    }

    private TooltipDividerPainter() {}
}

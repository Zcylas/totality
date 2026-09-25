package zcylas.totality.client.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.client.tooltip.group.TooltipGroupIcon;

import java.util.ArrayList;
import java.util.List;

/**
 * A body group heading: {@code fade ───── ⛏ MINING ───── fade}. The icon and the title are centred as one
 * unit; a 1px line runs from each side of the unit to the content edge, strongest next to the unit and
 * fading to nothing at its end. Both lines always have the same length, so the heading is symmetric.
 *
 * <p>Everything is tinted with a readable variant of the item's resolved rarity color (the same lift the
 * rarity plaque uses), except a texture icon, which keeps its own colors. The header/body divider is a
 * separate element ({@link TooltipDividerPainter}) and is unaffected.
 *
 * <p>Geometry is pure ({@link #layout}) and unit-tested.
 */
public final class TooltipGroupHeadingPainter {

    /** Space above the heading line (separates it from the previous group). */
    public static final int GAP_ABOVE = 3;
    /** Space below the heading line, before the group's first entry. */
    public static final int GAP_BELOW = 2;
    /** Text row height. */
    static final int TEXT_H = 9;
    /** Total vertical space one heading occupies. */
    public static final int HEIGHT = GAP_ABOVE + TEXT_H + GAP_BELOW;
    /** Gap between the icon and the title. */
    static final int ICON_GAP = 3;
    /** Gap between the centred unit and each line. */
    static final int LINE_GAP = 4;
    /** Lines never get shorter than this; a title that would squeeze them further is truncated instead. */
    static final int MIN_LINE = 8;
    static final String ELLIPSIS = "...";
    static final float LINE_ALPHA = 0.85f;
    static final int SEGMENTS = 12;

    /**
     * Horizontal layout of one heading.
     *
     * @param unitX      left edge of the icon+title unit
     * @param unitW      width of the unit
     * @param titleMaxW  width available to the title (the title is truncated to it when longer)
     * @param lineLength length of each of the two lines (equal, for symmetry)
     */
    public record Layout(int unitX, int unitW, int titleMaxW, int lineLength) {}

    /** Lays out a heading across {@code [x, x + width)} for an icon {@code iconW} and title {@code titleW} wide. */
    public static Layout layout(int x, int width, int iconW, int titleW) {
        int reserved = 2 * (LINE_GAP + MIN_LINE);
        int titleMaxW = Math.max(0, width - reserved - iconW - ICON_GAP);
        int shownTitleW = Math.min(titleW, titleMaxW);
        int unitW = iconW + ICON_GAP + shownTitleW;
        int unitX = x + (width - unitW) / 2;
        int lineLength = Math.max(0, Math.min(unitX - LINE_GAP - x, x + width - (unitX + unitW) - LINE_GAP));
        return new Layout(unitX, unitW, titleMaxW, lineLength);
    }

    /** Minimum width at which a heading shows its full title with minimum-length lines. */
    public static int naturalWidth(int iconW, int titleW) {
        return iconW + ICON_GAP + titleW + 2 * (LINE_GAP + MIN_LINE);
    }

    /** Opacity of line segment {@code i} (0 = next to the unit) of {@link #SEGMENTS}: fades outward to 0. */
    static float segmentAlpha(int i) {
        return LINE_ALPHA * (1f - (i + 0.5f) / SEGMENTS);
    }

    /** {@code [start, end)} offsets from the unit of each of the {@link #SEGMENTS} pieces of a line {@code length} long. */
    static List<int[]> segments(int length) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < SEGMENTS; i++) {
            int a = length * i / SEGMENTS, b = length * (i + 1) / SEGMENTS;
            if (b > a) out.add(new int[]{a, b, i});
        }
        return out;
    }

    /** The heading text and tinted-icon color for a rarity color: the plaque's readable variant. */
    public static int textColor(int rarityColor) {
        return TooltipRarityPlaquePainter.labelColor(rarityColor);
    }

    /**
     * The icon actually drawn for a heading: a texture resource (drawn untinted) or glyph text (tinted), already
     * resolved against what is available — see {@code TooltipGlyphSupport.resolveIcon}.
     */
    public record DrawnIcon(@Nullable Identifier texture, String glyph) {
        public static DrawnIcon texture(Identifier texture) {
            return new DrawnIcon(texture, "");
        }

        public static DrawnIcon glyph(String glyph) {
            return new DrawnIcon(null, glyph);
        }

        public boolean tinted() {
            return texture == null;
        }
    }

    /** Width of the icon as drawn. */
    public static int iconWidth(Font font, DrawnIcon icon) {
        return icon.texture() != null ? TooltipGroupIcon.Texture.SIZE : font.width(icon.glyph());
    }

    /**
     * Draws a heading whose top (including {@link #GAP_ABOVE}) is at {@code y}.
     *
     * @param title the localized, upper-cased title
     */
    public static void draw(GuiGraphicsExtractor graphics, Font font, DrawnIcon icon, String title,
                            int x, int y, int width, int rarityColor) {
        int iconW = iconWidth(font, icon);
        int titleMaxW = layout(x, width, iconW, font.width(title)).titleMaxW();
        String shown = font.width(title) <= titleMaxW ? title
                : font.plainSubstrByWidth(title, Math.max(0, titleMaxW - font.width(ELLIPSIS))) + ELLIPSIS;
        // Laid out again with the text actually shown, so a truncated title stays exactly centred.
        Layout layout = layout(x, width, iconW, font.width(shown));
        int textY = y + GAP_ABOVE;
        int color = textColor(rarityColor);

        if (icon.texture() != null) {
            // Whole texture into an integer-aligned SIZE x SIZE box, in its own colors (normalized UVs: any resolution).
            int size = TooltipGroupIcon.Texture.SIZE;
            graphics.blit(icon.texture(), layout.unitX(), textY, layout.unitX() + size, textY + size, 0f, 1f, 0f, 1f);
        } else {
            graphics.text(font, icon.glyph(), layout.unitX(), textY, color, true);
        }
        graphics.text(font, shown, layout.unitX() + iconW + ICON_GAP, textY, color, true);

        int lineY = textY + 4;
        int leftEnd = layout.unitX() - LINE_GAP;
        int rightStart = layout.unitX() + layout.unitW() + LINE_GAP;
        for (int[] s : segments(layout.lineLength())) {
            int c = TooltipVignettePainter.withAlpha(rarityColor, segmentAlpha(s[2]));
            graphics.fill(leftEnd - s[1], lineY, leftEnd - s[0], lineY + 1, c);
            graphics.fill(rightStart + s[0], lineY, rightStart + s[1], lineY + 1, c);
        }
    }

    private TooltipGroupHeadingPainter() {}
}

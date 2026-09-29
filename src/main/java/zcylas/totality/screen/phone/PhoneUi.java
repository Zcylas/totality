package zcylas.totality.screen.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The display's building blocks — header, tiles, dock, page dots, buttons, text — drawn in a
 * {@link PhoneDeviceStyle.Display} palette. Content screens compose these; they never pick colours or draw
 * the device themselves.
 *
 * <p><b>Crisp text.</b> Display text uses one scale per frame: 1 when the content fits, otherwise the
 * largest scale at which every font pixel still covers a whole number of screen pixels (GUI scale 4 → 0.75,
 * i.e. 3 screen pixels per font pixel). A fractional, blurry scale is never used.
 */
public final class PhoneUi {

    public enum TileState { NORMAL, ACTIVE, LOCKED, PRESSED }

    private final GuiGraphicsExtractor g;
    private final Font font;
    private final PhoneDeviceStyle style;
    private final PhoneDeviceStyle.Display d;
    /** Display text scale for this frame (see class doc). */
    public final float scale;

    public PhoneUi(GuiGraphicsExtractor g, Font font, PhoneDeviceStyle style, float scale) {
        this.g = g;
        this.font = font;
        this.style = style;
        this.d = style.display();
        this.scale = scale;
    }

    /**
     * The largest crisp text scale at which {@code text} fits {@code available} GUI pixels (1 if it fits at
     * full size). {@code guiScale} is the window's integer GUI scale.
     */
    public static float crispScale(Font font, List<String> texts, int available, int guiScale) {
        int widest = 0;
        for (String t : texts) widest = Math.max(widest, font.width(t));
        if (widest <= available || guiScale <= 1) return 1f;
        for (int k = guiScale - 1; k >= 2; k--) {
            float s = k / (float) guiScale;
            if (widest * s <= available) return s;
        }
        return 2f / guiScale;
    }

    /**
     * Content width at which display text is drawn at full size. Narrower displays (GUI scale 4 on a 1080p
     * window: 147 px) use the largest crisp scale below — one scale for every phone screen, so text sizes
     * match between them.
     */
    static final int FULL_SIZE_DISPLAY_WIDTH = 180;

    /** The display text scale for a device layout (see {@link #FULL_SIZE_DISPLAY_WIDTH}). */
    public static float displayScale(PhoneFrameRenderer.Layout l) {
        int guiScale = guiScale();
        float wanted = l.dw() / (float) FULL_SIZE_DISPLAY_WIDTH;
        if (wanted >= 1f || guiScale <= 1) return 1f;
        for (int k = guiScale - 1; k >= 2; k--) {
            float s = k / (float) guiScale;
            if (s <= wanted) return s;
        }
        return 2f / guiScale;
    }

    public static int guiScale() {
        return Math.max(1, (int) Math.round(Minecraft.getInstance().getWindow().getGuiScale()));
    }

    public PhoneDeviceStyle.Display colors() {
        return d;
    }

    // ── Text ──────────────────────────────────────────────────────────────────

    public int width(String s) {
        return Math.round(font.width(s) * scale);
    }

    /** Line height of display text (font glyphs are 8 px + 1 px descent). */
    public int lineHeight() {
        return Math.round(9 * scale);
    }

    public void text(String s, int x, int y, int color) {
        if (scale == 1f) {
            g.text(font, s, x, y, color, false);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, s, 0, 0, color, false);
        g.pose().popMatrix();
    }

    public void textCentered(String s, int centerX, int y, int color) {
        text(s, centerX - width(s) / 2, y, color);
    }

    // ── Pieces ────────────────────────────────────────────────────────────────

    /** Header height for the current text scale. */
    public int headerHeight() {
        return lineHeight() + 5;
    }

    /** Status/header strip at the top of the display: a title left (accent) and a quiet note right. */
    public void header(int x, int y, int w, String title, String right) {
        int h = headerHeight();
        g.fill(x, y, x + w, y + h, d.headerBackground());
        g.fill(x, y + h - 1, x + w, y + h, d.line());
        int ty = y + (h - 1 - lineHeight()) / 2 + 1;
        text(title, x + 4, ty, d.accent());
        if (right != null && !right.isEmpty() && width(title) + width(right) + 12 <= w) {
            text(right, x + w - 4 - width(right), ty, d.textDim());
        }
    }

    /**
     * One entry tile: a flat raised key (1 px light top edge, dark bottom edge). ACTIVE (hover/focus) gets
     * a copper outline and warm fill; LOCKED is sunk and dim with a padlock; PRESSED inverts briefly.
     */
    public void tile(int x, int y, int w, int h, String label, TileState state) {
        tile(x, y, w, h, label, state, null);
    }

    /** App icons are drawn at this many GUI pixels: one texel of a 32x32 icon = guiScale screen pixels (always crisp). */
    public static final int ICON_SIZE = 32;

    /**
     * A tile with an optional app icon (a GUI sprite, drawn as-is in every state: the tile's fill and outline give the
     * selection, the artwork never changes). With an icon the label sits under it; without one it is centred.
     */
    public void tile(int x, int y, int w, int h, String label, TileState state, Identifier icon) {
        int fill, top, bottom, textColor;
        switch (state) {
            case ACTIVE -> { fill = d.tileActive(); top = d.tileLight(); bottom = d.tileDark(); textColor = 0xFFFFF4E6; }
            case PRESSED -> { fill = d.accent(); top = d.accentBright(); bottom = d.tileDark(); textColor = 0xFF1A0E08; }
            case LOCKED -> { fill = d.tileLocked(); top = d.tileDark(); bottom = d.tileLocked(); textColor = 0xFF77726A; }
            default -> { fill = d.tile(); top = d.tileLight(); bottom = d.tileDark(); textColor = d.text(); }
        }
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, top);
        g.fill(x, y + h - 1, x + w, y + h, bottom);
        if (state == TileState.ACTIVE) outline(x - 1, y - 1, w + 2, h + 2, d.accent());
        int gap = 3;
        if (icon != null && h >= ICON_SIZE + gap + lineHeight() + 4 && w >= ICON_SIZE + 4) {
            int top0 = y + (h - ICON_SIZE - gap - lineHeight()) / 2;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x + (w - ICON_SIZE) / 2, top0, ICON_SIZE, ICON_SIZE);
            textCentered(label, x + w / 2, top0 + ICON_SIZE + gap, textColor);
        } else {
            textCentered(label, x + w / 2, y + (h - lineHeight()) / 2 + 1, textColor);
        }
        if (state == TileState.LOCKED) lock(x + w - 8, y + 3, d.textFaint());
    }

    public void lock(int x, int y, int color) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, style.lockSprite(), x, y, 5, 7, color);
    }

    /** A 1 px rectangle outline. */
    public void outline(int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public void separator(int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, d.line());
    }

    /** Page dots: filled copper for the current page, hollow for the others. */
    public void pageDots(int centerX, int y, int count, int current) {
        int size = 3, gap = 4;
        int total = count * size + (count - 1) * gap;
        int x = centerX - total / 2;
        for (int i = 0; i < count; i++) {
            int dx = x + i * (size + gap);
            if (i == current) g.fill(dx, y, dx + size, y + size, d.accent());
            else outline(dx, y, size, size, d.textFaint());
        }
    }

    /** Width of the page-dot row for {@code count} pages. */
    public static int pageDotsWidth(int count) {
        return count * 3 + (count - 1) * 4;
    }

    /** A small chevron ("<" or ">") drawn in pixels, 3x5. */
    public void chevron(int x, int y, boolean right, int color) {
        for (int i = 0; i < 3; i++) {
            int col = right ? x + i : x + 2 - i;
            g.fill(col, y + i, col + 1, y + i + 1, color);
            g.fill(col, y + 4 - i, col + 1, y + 5 - i, color);
        }
    }
}

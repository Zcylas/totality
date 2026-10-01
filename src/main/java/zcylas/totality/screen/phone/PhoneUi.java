package zcylas.totality.screen.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The display's building blocks — header, tiles, app icons, badges, status glyphs, page indicator, buttons,
 * text — drawn in the OS {@link PhoneTheme}. Content screens compose these; they never pick colours or draw
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
    private final PhoneTheme d;
    /** Display text scale for this frame (see class doc). */
    public final float scale;

    public PhoneUi(GuiGraphicsExtractor g, Font font, float scale) {
        this.g = g;
        this.font = font;
        this.d = PhoneTheme.DEFAULT;
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
     * window: 113 px) use the largest crisp scale below — one scale for every phone screen, so text sizes
     * match between them.
     */
    static final int FULL_SIZE_DISPLAY_WIDTH = 120;

    /** The display text scale for a device layout (see {@link #FULL_SIZE_DISPLAY_WIDTH}). */
    public static float displayScale(PhoneFrameRenderer.Layout l) {
        return displayScale(l, guiScale());
    }

    /** {@link #displayScale(PhoneFrameRenderer.Layout)} for an explicit integer GUI scale (pure; unit-tested). */
    public static float displayScale(PhoneFrameRenderer.Layout l, int guiScale) {
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

    public PhoneTheme colors() {
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
     * an accent outline; LOCKED is sunk and dim with a padlock; PRESSED inverts briefly.
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
            case ACTIVE -> { fill = d.tileActive(); top = d.tileLight(); bottom = d.tileDark(); textColor = d.accentBright(); }
            case PRESSED -> { fill = d.accent(); top = d.accentBright(); bottom = d.tileDark(); textColor = d.onAccent(); }
            case LOCKED -> { fill = d.tileLocked(); top = d.tileDark(); bottom = d.tileLocked(); textColor = d.textFaint(); }
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

    /** The 5x7 padlock glyph ({@link PhoneHomeGeometry#PADLOCK_W} x {@link PhoneHomeGeometry#PADLOCK_H}). */
    public void lock(int x, int y, int color) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, d.lockSprite(), x, y, 5, 7, color);
    }

    /** A 1 px outline of {@code r} = {x, y, w, h} (development bounds overlay). */
    public static void outlineRect(GuiGraphicsExtractor g, int[] r, int color) {
        g.fill(r[0], r[1], r[0] + r[2], r[1] + 1, color);
        g.fill(r[0], r[1] + r[3] - 1, r[0] + r[2], r[1] + r[3], color);
        g.fill(r[0], r[1], r[0] + 1, r[1] + r[3], color);
        g.fill(r[0] + r[2] - 1, r[1], r[0] + r[2], r[1] + r[3], color);
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

    /** Page dots: filled accent for the current page, hollow for the others. */
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

    // ── Home screen: app icons, badges, labels ────────────────────────────────

    /** ACTIVE = hovered or keyboard-focused (brighter abbreviation); the frame is drawn by {@link #iconFrame}. */
    public enum IconState { NORMAL, ACTIVE, PRESSED, LOCKED }

    /** A filled rectangle with 1 px cut corners: the pixel-art "rounded" shape of icons, cards and badges. */
    public void roundRect(int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** A 1 px outline with cut corners, matching {@link #roundRect}. */
    public void roundOutline(int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /**
     * One app icon: a rounded square of {@code size} GUI pixels holding either a sprite (drawn at the largest
     * size where each of its {@code artSize} texels is a whole number of screen pixels) or a provisional text
     * abbreviation. The artwork never changes with the state; the background and outline do.
     */
    public void appIcon(int x, int y, int size, String abbreviation, Identifier sprite, int artSize, IconState state) {
        int fill = switch (state) {
            case PRESSED -> d.accent();
            case LOCKED -> d.iconLocked();
            default -> d.iconBackground();
        };
        roundRect(x, y, size, size, fill);
        if (state != IconState.PRESSED) {
            g.fill(x + 1, y, x + size - 1, y + 1, state == IconState.LOCKED ? d.iconDark() : d.iconLight());
            g.fill(x + 1, y + size - 1, x + size - 1, y + size, d.iconDark());
        }
        boolean locked = state == IconState.LOCKED;
        if (sprite != null) {
            crispSprite(sprite, x + size / 2, y + size / 2, size, artSize);
            // A locked sprite sinks almost into the tile, so the padlock reads first.
            if (locked) roundRect(x, y, size, size, (0xC8 << 24) | (d.iconLocked() & 0xFFFFFF));
        } else {
            int color = switch (state) {
                case PRESSED -> d.onAccent();
                // Barely above the tile colour: the padlock on top must stay clearly visible.
                case LOCKED -> PhoneNotificationShade.mix(d.iconLocked(), d.textFaint(), 0.45f);
                case ACTIVE -> d.accentBright();
                default -> d.accent();
            };
            textCentered(abbreviation, x + size / 2, y + (size - lineHeight()) / 2 + 1, color);
        }
        if (locked) {
            // Centred in the tile and always inside it (PhoneHomeGeometry.padlock): the tile, and so the hover and
            // focus frames, are the same size for locked and unlocked apps.
            int[] p = PhoneHomeGeometry.padlock(x, y, size);
            lock(p[0], p[1], d.text());
        }
    }

    /**
     * The 1 px frame around an icon ({@code r} = {x, y, w, h} from {@link PhoneHomeGeometry#frame}). Mouse hover is
     * a quiet frame; keyboard focus is a bright frame with corner ticks, so the two never look alike.
     */
    public void iconFrame(int[] r, boolean keyboardFocus, boolean locked) {
        int x = r[0], y = r[1], w = r[2], h = r[3];
        if (!keyboardFocus) {
            roundOutline(x, y, w, h, locked ? d.textDim() : d.accent());
            return;
        }
        int c = d.accentBright();
        roundOutline(x, y, w, h, c);
        // Corner ticks just outside the frame.
        g.fill(x, y - 1, x + 3, y, c);
        g.fill(x - 1, y, x, y + 3, c);
        g.fill(x + w - 3, y - 1, x + w, y, c);
        g.fill(x + w, y, x + w + 1, y + 3, c);
        g.fill(x, y + h, x + 3, y + h + 1, c);
        g.fill(x - 1, y + h - 3, x, y + h, c);
        g.fill(x + w - 3, y + h, x + w, y + h + 1, c);
        g.fill(x + w, y + h - 3, x + w + 1, y + h, c);
    }

    /**
     * Draws a square sprite of {@code artSize} texels centred on ({@code cx}, {@code cy}), as large as fits in
     * {@code maxSize} GUI pixels while each texel stays a whole number of screen pixels (never blurry). If even one
     * screen pixel per texel does not fit, it is drawn at {@code maxSize} so it never leaves its tile.
     */
    public void crispSprite(Identifier sprite, int cx, int cy, int maxSize, int artSize) {
        int gui = guiScale();
        int k = maxSize * gui / artSize;
        if (k < 1) {
            // Not even one screen pixel per texel fits: draw it at exactly the space available (nearest-neighbour
            // downscale) rather than overflowing its tile.
            g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, cx - maxSize / 2, cy - maxSize / 2, maxSize, maxSize);
            return;
        }
        float size = artSize * k / (float) gui;
        g.pose().pushMatrix();
        // Snap the top-left corner to a screen pixel.
        float left = Math.round((cx - size / 2) * gui) / (float) gui, top = Math.round((cy - size / 2) * gui) / (float) gui;
        g.pose().translate(left, top);
        g.pose().scale(k / (float) gui, k / (float) gui);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, 0, 0, artSize, artSize);
        g.pose().popMatrix();
    }

    /** Badge text for a count: the number, or "99+". */
    public static String badgeText(int count) {
        return count > 99 ? "99+" : Integer.toString(count);
    }

    /** A neutral numeric badge whose top-right corner sits at ({@code right}, {@code top}). */
    public void badge(int right, int top, int count) {
        if (count <= 0) return;
        String s = badgeText(count);
        int h = lineHeight() + 1, w = Math.max(h, width(s) + 3);
        roundRect(right - w, top, w, h, d.badge());
        text(s, right - w + (w - width(s)) / 2 + 1, top + 1, d.badgeText());
    }

    /**
     * Splits a label into at most two centred lines of at most {@code maxWidth} GUI pixels, breaking at spaces;
     * whatever still does not fit ends in "..." (never drawn outside its column).
     */
    public List<String> wrapLabel(String label, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String rest = label;
        while (!rest.isEmpty() && lines.size() < 2) {
            if (width(rest) <= maxWidth) {
                lines.add(rest);
                rest = "";
                break;
            }
            int cut = -1;
            for (int i = rest.indexOf(' '); i > 0; i = rest.indexOf(' ', i + 1)) {
                if (width(rest.substring(0, i)) <= maxWidth) cut = i;
                else break;
            }
            if (cut < 0 || lines.size() == 1) {
                lines.add(ellipsize(rest, maxWidth));
                rest = "";
                break;
            }
            lines.add(rest.substring(0, cut));
            rest = rest.substring(cut + 1);
        }
        return lines;
    }

    /** {@code s}, shortened with "..." to fit {@code maxWidth} GUI pixels. */
    public String ellipsize(String s, int maxWidth) {
        if (width(s) <= maxWidth) return s;
        for (int n = s.length() - 1; n > 0; n--) {
            String t = s.substring(0, n).stripTrailing() + "...";
            if (width(t) <= maxWidth) return t;
        }
        return "...";
    }

    // ── Status bar glyphs (pixel art in GUI pixels, 5 px tall) ────────────────

    public static final int GLYPH_H = 5;
    public static final int SIGNAL_W = 7;
    public static final int BATTERY_W = 10;

    /** Cosmetic signal strength: four rising bars, all lit. */
    public void signal(int x, int y, int color) {
        for (int i = 0; i < 4; i++) {
            int h = i + 2;
            g.fill(x + i * 2, y + GLYPH_H - h, x + i * 2 + 1, y + GLYPH_H, color);
        }
    }

    /** Cosmetic full battery: outlined cell with a tip, filled. */
    public void battery(int x, int y, int color) {
        outline(x, y, BATTERY_W - 1, GLYPH_H, color);
        g.fill(x + BATTERY_W - 1, y + 1, x + BATTERY_W, y + GLYPH_H - 1, color);
        g.fill(x + 2, y + 2, x + BATTERY_W - 3, y + GLYPH_H - 2, color);
    }

    /** Width of {@link #severityGlyph} for a severity. */
    public static int severityGlyphWidth(PhoneNotificationShade.Severity s) {
        return switch (s) {
            case NORMAL -> 3;
            case IMPORTANT -> 1;
            case CRITICAL -> 7;
        };
    }

    /** Green dot, orange exclamation mark, or red warning triangle (with a cut-out "!"), 5 px tall. */
    public void severityGlyph(int x, int y, PhoneNotificationShade.Severity s) {
        int c = d.severity(s);
        switch (s) {
            case NORMAL -> {
                g.fill(x, y + 2, x + 3, y + 3, c);
                g.fill(x + 1, y + 1, x + 2, y + 4, c);
            }
            case IMPORTANT -> {
                g.fill(x, y, x + 1, y + 3, c);
                g.fill(x, y + 4, x + 1, y + 5, c);
            }
            case CRITICAL -> {
                for (int row = 0; row < GLYPH_H; row++) {
                    int half = (row + 1) * 3 / GLYPH_H;
                    g.fill(x + 3 - half, y + row, x + 4 + half, y + row + 1, c);
                }
                g.fill(x + 3, y + 1, x + 4, y + 3, d.background());
                g.fill(x + 3, y + 4, x + 4, y + 5, d.background());
            }
        }
    }

    // ── Page indicator ────────────────────────────────────────────────────────

    public static final int PAGE_DOT_PITCH = 7;

    /** Centre x of page marker {@code i} of {@code count}, for drawing and hit-testing. */
    public static int pageMarkerX(int centerX, int count, int i) {
        return centerX - (count - 1) * PAGE_DOT_PITCH / 2 + i * PAGE_DOT_PITCH;
    }

    /**
     * Small page dots centred on {@code centerX}. The main home page is always a cyan 5x5 ring, so it reads as "home"
     * from any page; it is filled when it is the current page. Other pages are 2x2 dots, cyan when current.
     */
    public void pageIndicator(int centerX, int y, int count, int current, int main) {
        for (int i = 0; i < count; i++) {
            int cx = pageMarkerX(centerX, count, i);
            if (i == main) {
                roundOutline(cx - 2, y - 1, 5, 5, d.accent());
                if (i == current) g.fill(cx - 1, y, cx + 2, y + 3, d.accentBright());
            } else {
                g.fill(cx - 1, y, cx + 1, y + 2, i == current ? d.accent() : d.textFaint());
            }
        }
    }

    // ── Small controls ────────────────────────────────────────────────────────

    /** A 5x3 chevron pointing down (expand) or up (collapse). */
    public void chevronVertical(int x, int y, boolean down, int color) {
        for (int i = 0; i < 3; i++) {
            int row = down ? y + i : y + 2 - i;
            g.fill(x + i, row, x + i + 1, row + 1, color);
            g.fill(x + 4 - i, row, x + 5 - i, row + 1, color);
        }
    }

    /** A 5x5 "x" (dismiss / clear). */
    public void cross(int x, int y, int color) {
        for (int i = 0; i < 5; i++) {
            g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
            g.fill(x + 4 - i, y + i, x + 5 - i, y + i + 1, color);
        }
    }

    public GuiGraphicsExtractor graphics() {
        return g;
    }
}

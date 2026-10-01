package zcylas.totality.screen.phone;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Draws the opened phone DEVICE for any {@link PhoneDeviceStyle}: its casing, hardware details and display
 * surface, and computes where the display is so screens can lay out their content inside it. Everything
 * is drawn at whole GUI pixels (sprites at 1 art pixel = 1 GUI pixel), so it stays crisp at every GUI scale.
 *
 * <p>The phone keeps its device proportions ({@link PhoneDeviceStyle#aspect()}) and is anchored to the
 * right of the window, vertically centred, so the world stays visible around it.
 */
public final class PhoneFrameRenderer {

    private static final int MARGIN = 6;
    private static final int RIGHT_MARGIN = 24;
    /**
     * Beyond this the phone would only grow emptier; larger GUIs keep it at this height. High enough that at GUI
     * scale 2 the phone still fills most of a 1080p window and every app label fits at full size.
     */
    private static final int MAX_HEIGHT = 400;

    private static final long SLIDE_NANOS = 170_000_000L;
    private static final long POWER_DELAY_NANOS = 70_000_000L;
    private static final long POWER_NANOS = 190_000_000L;
    private static final int SLIDE_DISTANCE = 14;
    /** A phone screen drawn this recently means the device is already in hand: no new open animation. */
    private static final long CONTINUITY_NANOS = 250_000_000L;

    private static long lastDrawnNanos;

    private PhoneFrameRenderer() {}

    /** Body {@code x, y, w, h} and display {@code dx, dy, dw, dh}, in GUI pixels. */
    public record Layout(int x, int y, int w, int h, int dx, int dy, int dw, int dh) {
        Layout shifted(int by) {
            return new Layout(x + by, y, w, h, dx + by, dy, dw, dh);
        }

        public boolean inDisplay(double mx, double my) {
            return mx >= dx && mx < dx + dw && my >= dy && my < dy + dh;
        }
    }

    public static Layout layout(int guiWidth, int guiHeight, PhoneDeviceStyle style) {
        int h = Math.min(guiHeight - MARGIN * 2, MAX_HEIGHT);
        int w = Math.round(h * style.aspect());
        int x = Math.max(MARGIN, guiWidth - w - RIGHT_MARGIN);
        int y = (guiHeight - h) / 2;
        return new Layout(x, y, w, h, x + style.insetLeft(), y + style.insetTop(),
                w - style.insetLeft() - style.insetRight(), h - style.insetTop() - style.insetBottom());
    }

    /** Open transition state of one screen: the device slides in, then the display powers on. */
    public record Transition(int slide, float power) {
        public static final Transition NONE = new Transition(0, 1);
    }

    /**
     * Called when a phone screen is created: the start of its open animation, or -1 when another phone
     * screen was just on screen (navigating between the phone's own screens keeps the device in place).
     */
    public static long beginOpen() {
        long now = System.nanoTime();
        return now - lastDrawnNanos < CONTINUITY_NANOS ? -1 : now;
    }

    public static Transition transition(long openedNanos) {
        if (openedNanos < 0) return Transition.NONE;
        long t = System.nanoTime() - openedNanos;
        float slide = Math.min(1f, t / (float) SLIDE_NANOS);
        float eased = 1 - (1 - slide) * (1 - slide) * (1 - slide);
        float power = Math.max(0f, Math.min(1f, (t - POWER_DELAY_NANOS) / (float) POWER_NANOS));
        return new Transition(Math.round(SLIDE_DISTANCE * (1 - eased)), power);
    }

    /** Draws the device and its display's wallpaper; returns the layout actually drawn (after the slide). */
    public static Layout drawDevice(GuiGraphicsExtractor g, Layout base, PhoneDeviceStyle style, Transition t) {
        Layout l = base.shifted(t.slide());
        PhoneTheme os = PhoneTheme.DEFAULT;

        // Soft contact shadow under the body.
        g.fill(l.x() + 3, l.y() + 3, l.x() + l.w() + 2, l.y() + l.h() + 2, 0x40000000);
        g.fill(l.x() + 2, l.y() + 2, l.x() + l.w() + 1, l.y() + l.h() + 1, 0x30000000);

        // Right-edge buttons, behind the casing (they protrude 2 px, as on the item).
        for (float[] b : style.sideButtons()) {
            int top = l.y() + Math.round(l.h() * b[0]);
            int bottom = l.y() + Math.round(l.h() * b[1]);
            g.fill(l.x() + l.w() - 2, top, l.x() + l.w() + 2, bottom, style.outline());
            g.fill(l.x() + l.w() - 2, top + 1, l.x() + l.w() + 1, bottom - 1, style.buttonFace());
        }

        // Display surface: the OS wallpaper, dark navy with a subtle cyan-blue rise toward the bottom.
        int x0 = l.dx(), y0 = l.dy(), x1 = l.dx() + l.dw(), y1 = l.dy() + l.dh();
        g.fillGradient(x0, y0, x1, y1, os.wallpaperTop(), os.wallpaperBottom());
        g.fillGradient(x0, y0 + l.dh() * 3 / 5, x1, y1, 0x00000000, os.wallpaperGlow());
        // Recessed glass: a 1 px shadow under the top bezel and inside the left bezel.
        g.fill(x0, y0, x1, y0 + 1, 0x50000000);
        g.fill(x0, y0 + 1, x0 + 1, y1, 0x30000000);

        // Casing (nine-slice, tiled edges), speaker slot with the front camera, chin grille.
        g.blitSprite(RenderPipelines.GUI_TEXTURED, style.frameSprite(), l.x(), l.y(), l.w(), l.h());
        int speakerX = l.x() + (l.w() - style.speakerWidth()) / 2;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, style.speakerSprite(), speakerX, l.y() + style.speakerY(),
                style.speakerWidth(), style.speakerHeight());
        g.blitSprite(RenderPipelines.GUI_TEXTURED, style.cameraSprite(), speakerX - style.cameraGap() - style.cameraSize(),
                l.y() + style.speakerY() + (style.speakerHeight() - style.cameraSize()) / 2, style.cameraSize(), style.cameraSize());
        g.blitSprite(RenderPipelines.GUI_TEXTURED, style.grilleSprite(), l.x() + (l.w() - style.grilleWidth()) / 2,
                l.y() + l.h() - style.grilleY() - style.grilleHeight(), style.grilleWidth(), style.grilleHeight());
        return l;
    }

    /**
     * After the content: the display's rounded inner corners (bezel colour, as on the item) and the power-on
     * fade (content comes up with the backlight). Also records that a phone screen was on screen.
     */
    public static void finishDisplay(GuiGraphicsExtractor g, Layout l, PhoneDeviceStyle style, Transition t) {
        if (t.power() < 1f) {
            int alpha = Math.round((1 - t.power()) * 255);
            g.fill(l.dx(), l.dy(), l.dx() + l.dw(), l.dy() + l.dh(), (alpha << 24) | 0x0B0C0B);
        }
        int bz = style.bezel();
        int x0 = l.dx(), y0 = l.dy(), x1 = l.dx() + l.dw() - 1, y1 = l.dy() + l.dh() - 1;
        for (int[] c : new int[][] {{x0, y0, 1, 1}, {x1, y0, -1, 1}, {x0, y1, 1, -1}, {x1, y1, -1, -1}}) {
            g.fill(Math.min(c[0], c[0] + c[2] * 2), c[1], Math.max(c[0], c[0] + c[2] * 2) + 1, c[1] + 1, bz);
            g.fill(c[0], Math.min(c[1], c[1] + c[3]), c[0] + 1, Math.max(c[1], c[1] + c[3]) + 1, bz);
        }
        lastDrawnNanos = System.nanoTime();
    }
}

package zcylas.totality.screen.phone;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws the phone-shaped body/bezel shared by {@link PhoneSetupScreen} and
 * {@link PhoneAppGridScreen}, so both look like an actual phone rather than a plain box,
 * and so a new {@link PhoneFrame} tier only needs a palette, not new drawing code.
 * TODO(visual pass): flat-color approximation of Context/phone_mockup.png — real
 * pixel-art frame texture still belongs to a later art pass.
 */
public final class PhoneFrameRenderer {

    private static final int BORDER = 10;
    private static final int TOP_BEZEL = 22;
    private static final int BOTTOM_BEZEL = 16;
    private static final int RIGHT_MARGIN = 24;
    private static final int EXTRA_WIDTH = 5;

    private PhoneFrameRenderer() {}

    /** Anchors a phone-shaped silhouette to the right edge of the window (vertically
     *  centered), capped at ~98% height / 78% width, so the game world stays visible
     *  around it instead of the phone sitting over a full-screen backdrop. */
    public static int[] bounds(int windowW, int windowH) {
        int maxH = (int) (windowH * 0.98f);
        int maxW = (int) (windowW * 0.78f);
        int h = maxH;
        int w = (int) (h * 0.62f);
        if (w > maxW) {
            w = maxW;
            h = (int) (w / 0.62f);
        }
        w += EXTRA_WIDTH;
        int x = windowW - w - RIGHT_MARGIN;
        int y = windowH / 2 - h / 2;
        return new int[]{ x, y, w, h };
    }

    /** Pure geometry — the inner screen content bounds {x, y, w, h} for a given phone body. */
    public static int[] screenBounds(int x, int y, int w, int h) {
        return new int[]{ x + BORDER, y + TOP_BEZEL, w - BORDER * 2, h - TOP_BEZEL - BOTTOM_BEZEL };
    }

    /** Draws the phone body/frame. Returns the inner screen content bounds {x, y, w, h}. */
    public static int[] draw(GuiGraphicsExtractor g, int x, int y, int w, int h, PhoneFrame frame) {
        g.fill(x, y, x + w, y + h, 0xFF08090B);

        fillBorder(g, x, y, w, h, BORDER, frame.colorDim);
        fillBorder(g, x + 2, y + 2, w - 4, h - 4, BORDER - 3, frame.color);

        drawCorner(g, x + 2, y + 2, true, true, frame.colorBright);
        drawCorner(g, x + w - 2, y + 2, false, true, frame.colorBright);
        drawCorner(g, x + 2, y + h - 2, true, false, frame.colorBright);
        drawCorner(g, x + w - 2, y + h - 2, false, false, frame.colorBright);

        int btnW = 3;
        g.fill(x - btnW, y + h / 6, x, y + h / 6 + 14, frame.colorBright);
        g.fill(x - btnW, y + h / 6 + 20, x, y + h / 6 + 34, frame.colorBright);
        g.fill(x + w, y + h / 5, x + w + btnW, y + h / 5 + 18, frame.colorBright);

        int[] screen = screenBounds(x, y, w, h);
        int screenX = screen[0], screenY = screen[1], screenW = screen[2], screenH = screen[3];

        g.fill(screenX, screenY, screenX + screenW, screenY + screenH, 0xFF03060A);
        drawBorder(g, screenX, screenY, screenW, screenH, 1, 0xFF0A2A3A);

        int notchW = Math.max(20, screenW / 3);
        g.fill(screenX + screenW / 2 - notchW / 2, y + 5, screenX + screenW / 2 + notchW / 2, screenY - 2, 0xFF000000);

        return screen;
    }

    private static void fillBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int t, int color) {
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        g.fill(x, y, x + t, y + h, color);
        g.fill(x + w - t, y, x + w, y + h, color);
    }

    private static void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int t, int color) {
        fillBorder(g, x, y, w, h, t, color);
    }

    private static void drawCorner(GuiGraphicsExtractor g, int x, int y, boolean left, boolean top, int color) {
        int dx = left ? 1 : -1, dy = top ? 1 : -1;
        int size = 6;
        g.fill(x, y, x + dx * size, y + dy, color);
        g.fill(x, y, x + dx, y + dy * size, color);
    }
}

package zcylas.totality.client.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import zcylas.totality.client.photo.Gallery;
import zcylas.totality.client.photo.PhotoCapture;
import zcylas.totality.client.photo.PhotoMetadata;
import zcylas.totality.client.photo.PhotoTextures;
import zcylas.totality.screen.phone.PhoneTheme;
import zcylas.totality.screen.phone.PhoneUi;

import java.util.List;
import java.util.Locale;

/**
 * Draws the full-screen viewfinder (HUD layer, never in a photograph) and owns its layout, which input hit-testing
 * uses too. Controls: Gallery shortcut (bottom left, latest photo), shutter (bottom centre), mode selector above it,
 * zoom presets above that with the live magnification, and a back control (top left). All sizes are whole multiples of
 * a unit chosen from the window height, so the controls keep their physical size across GUI scales and stay crisp.
 */
public final class CameraViewfinder {

    /** Hit-test results. The ZOOM_* entries stay last and in preset order. */
    public enum Control { NONE, SHUTTER, GALLERY, BACK, MODE_NORMAL, MODE_SCAN, ZOOM_0, ZOOM_1, ZOOM_2 }

    static final long OPEN_NANOS = 220_000_000L;
    static final long HINT_NANOS = 4_000_000_000L;
    static final String BACK_LABEL = "‹ Phone";

    private CameraViewfinder() {}

    /** Geometry for a {@code w}x{@code h} GUI-pixel window; {@code u} is the size unit (see class doc). */
    public record Layout(int w, int h, int u) {
        public int shutterX() { return w / 2; }
        public int shutterY() { return h - 24 * u; }
        public int shutterR() { return 13 * u; }
        public int thumbS() { return 24 * u; }
        public int thumbX() { return 16 * u; }
        public int thumbY() { return shutterY() - thumbS() / 2; }
        public int modeY() { return shutterY() - shutterR() - 13 * u; }
        public int modeSlot() { return 46 * u; }
        public int chipW() { return 22 * u; }
        public int chipH() { return 12 * u; }
        public int chipGap() { return 4 * u; }
        public int chipY() { return modeY() - 22 * u; }
        public int barTop() { return chipY() - 14 * u; }
        public int backX() { return 8 * u; }
        public int backY() { return 8 * u; }
        public int backW() { return 40 * u; }
        public int backH() { return 13 * u; }
        /** Top of the zoom presets' backing pill (the chips are drawn 2u inside it). */
        public int chipPillTop() { return chipY() - 2 * u; }
        /** Scan placeholder: centred between the top hint and the zoom presets, so it touches neither. */
        public int scanCenterY() { return (backY() + backH() + chipPillTop()) / 2; }
        public int scanHalf() { return 24 * u; }
        public int scanPanelTop() { return scanCenterY() - 15 * u; }
        public int scanPanelH() { return 30 * u; }
        /** Lowest pixel row the Scan placeholder draws (its brackets reach below the panel). */
        public int scanBottom() { return Math.max(scanPanelTop() + scanPanelH(), scanCenterY() + scanHalf() + u); }
        /** Highest pixel row it draws. */
        public int scanTop() { return Math.min(scanPanelTop(), scanCenterY() - scanHalf() - u); }

        /** Left edge of zoom chip {@code i} of {@code count}. */
        public int chipX(int i, int count) {
            int total = count * chipW() + (count - 1) * chipGap();
            return w / 2 - total / 2 + i * (chipW() + chipGap());
        }

        /** Centre x of mode label {@code i} when the selector sits at {@code position}. */
        public float modeX(int i, float position) {
            return w / 2f + (i - position) * modeSlot();
        }

        /** The control under ({@code mx}, {@code my}) with mode {@code selected} centred in the selector. */
        public Control at(double mx, double my, CameraMode selected) {
            double dx = mx - shutterX(), dy = my - shutterY();
            if (dx * dx + dy * dy <= (shutterR() + u) * (double) (shutterR() + u)) return Control.SHUTTER;
            if (mx >= thumbX() && mx < thumbX() + thumbS() && my >= thumbY() && my < thumbY() + thumbS()) return Control.GALLERY;
            if (mx >= backX() && mx < backX() + backW() && my >= backY() && my < backY() + backH()) return Control.BACK;
            int presets = CameraZoom.BASIC.presets().size();
            for (int i = 0; i < presets; i++) {
                int x = chipX(i, presets);
                if (mx >= x && mx < x + chipW() && my >= chipY() && my < chipY() + chipH()) {
                    return Control.values()[Control.ZOOM_0.ordinal() + i];
                }
            }
            if (Math.abs(my - modeY() - 4 * u) <= 7 * u) {
                for (CameraMode m : CameraMode.values()) {
                    if (Math.abs(mx - modeX(m.ordinal(), selected.ordinal())) <= modeSlot() / 2.0) {
                        return m == CameraMode.NORMAL ? Control.MODE_NORMAL : Control.MODE_SCAN;
                    }
                }
            }
            return Control.NONE;
        }
    }

    public static Layout layout(int w, int h) {
        return new Layout(w, h, Math.max(1, Math.round(h / 253f)));
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    /** Called by the HUD mixin every frame while the Camera is open. */
    public static void draw(GuiGraphicsExtractor g) {
        CameraSession s = CameraSession.active();
        if (s == null) return;
        s.frame();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Layout l = layout(g.guiWidth(), g.guiHeight());
        int u = l.u();
        PhoneTheme os = PhoneTheme.DEFAULT;
        long now = System.nanoTime();
        float open = Math.min(1f, (now - s.openedNanos) / (float) OPEN_NANOS);
        Control hover = s.cursor ? l.at(mc.mouseHandler.getScaledXPos(mc.getWindow()), mc.mouseHandler.getScaledYPos(mc.getWindow()), s.mode)
                : Control.NONE;

        // Frame: quiet corner marks and a soft bottom gradient behind the controls.
        corners(g, l, 0x60FFFFFF);
        g.fillGradient(0, l.barTop(), l.w(), l.h(), 0x00000000, 0x78000000);

        // Back.
        boolean backHover = hover == Control.BACK;
        roundRect(g, l.backX(), l.backY(), l.backW(), l.backH(), backHover ? 0xC0173A52 : 0x70000000);
        text(g, font, BACK_LABEL, l.backX() + (l.backW() - font.width(BACK_LABEL) * u) / 2, l.backY() + (l.backH() - 8 * u) / 2,
                backHover ? os.accentBright() : 0xFFFFFFFF, u);

        // Zoom presets; the chip nearest the current magnification shows the exact value.
        List<Float> presets = s.zoom.range().presets();
        int near = s.zoom.nearestPreset();
        roundRect(g, l.chipX(0, presets.size()) - 2 * u, l.chipPillTop(),
                presets.size() * l.chipW() + (presets.size() - 1) * l.chipGap() + 4 * u, l.chipH() + 4 * u, 0x70000000);
        for (int i = 0; i < presets.size(); i++) {
            int x = l.chipX(i, presets.size());
            boolean on = i == near;
            boolean hot = hover.ordinal() == Control.ZOOM_0.ordinal() + i;
            if (on) roundRect(g, x, l.chipY(), l.chipW(), l.chipH(), 0xE0101820);
            if (on || hot) roundOutline(g, x, l.chipY(), l.chipW(), l.chipH(), hot ? os.accentBright() : os.accent());
            String label = on ? CameraZoom.label(s.zoom.current()) : CameraZoom.label(presets.get(i));
            float ts = on ? u : Math.max(1, u * 3 / 4f);
            int tw = Math.round(font.width(label) * ts);
            text(g, font, label, x + (l.chipW() - tw) / 2, l.chipY() + Math.round((l.chipH() - 8 * ts) / 2), on ? os.accentBright() : 0xFFE4EDF5, ts);
        }

        // Mode selector: the active mode slides to the centre.
        g.enableScissor(l.w() / 2 - l.modeSlot() * 3 / 2, l.modeY() - 2 * u, l.w() / 2 + l.modeSlot() * 3 / 2, l.modeY() + 12 * u);
        for (CameraMode m : CameraMode.values()) {
            String label = m.label().toUpperCase(Locale.ROOT);
            float cx = l.modeX(m.ordinal(), s.modePosition);
            boolean sel = m == s.mode;
            boolean hot = (m == CameraMode.NORMAL ? Control.MODE_NORMAL : Control.MODE_SCAN) == hover;
            int color = sel ? os.accent() : hot ? 0xFFFFFFFF : 0xB0E4EDF5;
            text(g, font, label, Math.round(cx - font.width(label) * u / 2f), l.modeY(), color, u);
        }
        g.disableScissor();
        g.fill(l.w() / 2 - u, l.modeY() + 10 * u, l.w() / 2 + u, l.modeY() + 12 * u, os.accent());

        // Shutter: a white ring around a white disc (dim in Scan, which takes no photographs yet).
        boolean scan = !s.mode.available();
        long sinceShot = s.flying != null ? now - s.flyingSince : Long.MAX_VALUE;
        float press = sinceShot < 120_000_000L ? 0.82f : 1f;
        int ringColor = hover == Control.SHUTTER ? os.accentBright() : 0xFFFFFFFF;
        ring(g, l.shutterX(), l.shutterY(), l.shutterR(), l.shutterR() - 2 * u, scan ? os.accent() : ringColor);
        disc(g, l.shutterX(), l.shutterY(), (l.shutterR() - 4 * u) * press, scan ? 0x603CC4EE : 0xFFFFFFFF);

        // Gallery shortcut.
        drawShortcut(g, s, l, hover == Control.GALLERY);

        // Hints and messages.
        String hint = s.cursor ? "Release Alt to aim" : now - s.openedNanos < HINT_NANOS ? "Hold Alt for the cursor" : null;
        if (scan) drawScanPanel(g, font, l, os);
        if (s.message != null) hint = s.message;
        if (hint != null) {
            int tw = font.width(hint) * u;
            roundRect(g, (l.w() - tw) / 2 - 5 * u, l.backY(), tw + 10 * u, l.backH(), 0x90000000);
            text(g, font, hint, (l.w() - tw) / 2, l.backY() + (l.backH() - 8 * u) / 2, s.message != null ? os.important() : 0xFFE4EDF5, u);
        }

        drawShutterFeedback(g, s, l, now);

        // Opening: the view comes up from black.
        if (open < 1f) g.fill(0, 0, l.w(), l.h(), Math.round((1 - open) * 255) << 24);
    }

    private static void drawShortcut(GuiGraphicsExtractor g, CameraSession s, Layout l, boolean hover) {
        int x = l.thumbX(), y = l.thumbY(), size = l.thumbS(), u = l.u();
        roundRect(g, x - u, y - u, size + 2 * u, size + 2 * u, hover ? PhoneTheme.DEFAULT.accentBright() : 0xFFFFFFFF);
        g.fill(x, y, x + size, y + size, 0xFF0B1119);
        Gallery gallery = Gallery.current().orElse(null);
        List<PhotoMetadata> photos = gallery != null ? gallery.photos() : List.of();
        // While a photo flies in, the shortcut still shows what was there before it.
        PhotoMetadata shown = null;
        for (PhotoMetadata m : photos) {
            if (s.flying != null && m.id().equals(s.flying.id())) continue;
            shown = m;
            break;
        }
        if (s.flying == null && s.shortcutPreview != null) {
            PhotoMetadata latest = gallery != null ? gallery.latest() : null;
            boolean saved = latest != null && latest.id().equals(s.shortcutPreview.id());
            PhotoTextures.Loaded t = saved ? PhotoTextures.thumbnail(gallery, latest) : null;
            if (t == null) {
                drawPreview(g, s.shortcutPreview, x, y, size, size, 1f);
                return;
            }
            s.releaseShortcutPreview();
        }
        if (shown == null) return;
        PhotoTextures.Loaded t = PhotoTextures.thumbnail(gallery, shown);
        if (t == null) return;
        float[] uv = ShutterAnimation.cropUv(t.width(), t.height(), 1f);
        g.blit(t.texture().getTextureView(), PhotoTextures.sampler(), x, y, x + size, y + size, uv[0], uv[2], uv[1], uv[3]);
    }

    private static void drawShutterFeedback(GuiGraphicsExtractor g, CameraSession s, Layout l, long now) {
        if (s.flying == null) return;
        boolean reduced = CameraSession.reducedMotion();
        ShutterAnimation.Frame f = ShutterAnimation.frame(now - s.flyingSince, new ShutterAnimation.Rect(0, 0, l.w(), l.h()),
                new ShutterAnimation.Rect(l.thumbX(), l.thumbY(), l.thumbS(), l.thumbS()), reduced);
        if (f.photo() != null) {
            ShutterAnimation.Rect r = f.photo();
            float border = Math.max(1, l.u() * (1 - f.crop()) * 2);
            fillF(g, r.x() - border, r.y() - border, r.w() + 2 * border, r.h() + 2 * border, 0xF0FFFFFF);
            drawPreviewF(g, s.flying, r, f.crop());
        }
        if (f.flash() > 0) {
            boolean gentle = Minecraft.getInstance().options.hideLightningFlash().get();
            int a = Math.round(f.flash() * (gentle ? 0.35f : 1f) * 255);
            g.fill(0, 0, l.w(), l.h(), (a << 24) | (gentle ? 0x000000 : 0xFFFFFF));
        }
    }

    private static void drawScanPanel(GuiGraphicsExtractor g, Font font, Layout l, PhoneTheme os) {
        int u = l.u(), cx = l.w() / 2, cy = l.scanCenterY();
        int half = l.scanHalf(), arm = 9 * u;
        // Scan brackets around the centre of the view.
        for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            int x = cx + c[0] * half, y = cy + c[1] * half;
            g.fill(Math.min(x, x - c[0] * arm), y - (c[1] > 0 ? u : 0), Math.max(x, x - c[0] * arm), y + (c[1] > 0 ? 0 : u), os.accent());
            g.fill(x - (c[0] > 0 ? u : 0), Math.min(y, y - c[1] * arm), x + (c[0] > 0 ? 0 : u), Math.max(y, y - c[1] * arm), os.accent());
        }
        String title = "Scan", sub = "Coming with the Codex";
        int w = Math.max(font.width(title) * 2, font.width(sub)) * u + 16 * u;
        int top = l.scanPanelTop();
        roundRect(g, cx - w / 2, top, w, l.scanPanelH(), 0xB00B1119);
        roundOutline(g, cx - w / 2, top, w, l.scanPanelH(), os.accent());
        text(g, font, title, cx - font.width(title) * u, top + 4 * u, os.accent(), 2 * u);
        text(g, font, sub, cx - font.width(sub) * u / 2, top + 20 * u, 0xFFB8C6D4, u);
    }

    // ── Primitives (whole GUI pixels, or screen pixels for curves) ─────────────

    /** Four L-shaped corner marks framing the picture (the top-left one sits below the back control). */
    private static void corners(GuiGraphicsExtractor g, Layout l, int color) {
        int u = l.u(), m = 6 * u, arm = 12 * u, t = Math.max(1, u / 2);
        int top = l.backY() + l.backH() + 6 * u, bottom = l.barTop() - 4 * u;
        corner(g, m, top, 1, 1, arm, t, color);
        corner(g, l.w() - m, m, -1, 1, arm, t, color);
        corner(g, m, bottom, 1, -1, arm, t, color);
        corner(g, l.w() - m, bottom, -1, -1, arm, t, color);
    }

    /** An L with its corner at ({@code x}, {@code y}), arms running towards {@code dx}, {@code dy}. */
    private static void corner(GuiGraphicsExtractor g, int x, int y, int dx, int dy, int arm, int t, int color) {
        int x0 = dx > 0 ? x : x - t, y0 = dy > 0 ? y : y - t;
        g.fill(Math.min(x0, x0 + dx * arm), y0, Math.max(x0 + t, x0 + dx * arm + t), y0 + t, color);
        g.fill(x0, Math.min(y0, y0 + dy * arm), x0 + t, Math.max(y0 + t, y0 + dy * arm + t), color);
    }

    static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    static void roundOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    static void text(GuiGraphicsExtractor g, Font font, String s, int x, int y, int color, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.text(font, s, 0, 0, color, true);
        g.pose().popMatrix();
    }

    /** A filled disc, drawn row by row in screen pixels (smooth at every GUI scale). */
    static void disc(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
        ring(g, cx, cy, r, 0, color);
    }

    /** An annulus between {@code inner} and {@code outer} radius (GUI px), in screen pixels. */
    static void ring(GuiGraphicsExtractor g, float cx, float cy, float outer, float inner, int color) {
        int gs = PhoneUi.guiScale();
        int ro = Math.round(outer * gs), ri = Math.round(inner * gs);
        int px = Math.round(cx * gs), py = Math.round(cy * gs);
        g.pose().pushMatrix();
        g.pose().scale(1f / gs, 1f / gs);
        for (int dy = -ro; dy < ro; dy++) {
            double yc = dy + 0.5;
            int ho = (int) Math.round(Math.sqrt(Math.max(0, ro * (double) ro - yc * yc)));
            int hi = Math.abs(yc) < ri ? (int) Math.round(Math.sqrt(ri * (double) ri - yc * yc)) : 0;
            if (hi == 0) {
                g.fill(px - ho, py + dy, px + ho, py + dy + 1, color);
            } else {
                g.fill(px - ho, py + dy, px - hi, py + dy + 1, color);
                g.fill(px + hi, py + dy, px + ho, py + dy + 1, color);
            }
        }
        g.pose().popMatrix();
    }

    private static void fillF(GuiGraphicsExtractor g, float x, float y, float w, float h, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(w / 1000f, h / 1000f);
        g.fill(0, 0, 1000, 1000, color);
        g.pose().popMatrix();
    }

    /** The GPU preview of a capture (stored bottom-up, so v is flipped) into an integer rectangle, cropped by {@code crop}. */
    private static void drawPreview(GuiGraphicsExtractor g, PhotoCapture.Preview p, int x, int y, int w, int h, float crop) {
        float[] uv = ShutterAnimation.cropUv(p.width(), p.height(), crop);
        g.blit(p.view(), PhotoTextures.sampler(), x, y, x + w, y + h, uv[0], uv[2], 1 - uv[1], 1 - uv[3]);
    }

    private static void drawPreviewF(GuiGraphicsExtractor g, PhotoCapture.Preview p, ShutterAnimation.Rect r, float crop) {
        g.pose().pushMatrix();
        g.pose().translate(r.x(), r.y());
        g.pose().scale(r.w() / 1000f, r.h() / 1000f);
        drawPreview(g, p, 0, 0, 1000, 1000, crop);
        g.pose().popMatrix();
    }
}

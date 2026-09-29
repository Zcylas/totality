package zcylas.totality.client.hologram;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Draws hologram layers into a {@link SubmitNodeStorage}. Every call is submitted under its own,
 * increasing {@code order}, so layers composite exactly in call order (the pipelines have no depth
 * test). Colours are ARGB; {@link #alpha} fades everything drawn afterwards.
 */
final class HologramPainter {

    private static final int FULL_BRIGHT = 0xF000F0;
    /** {@code Font} treats an alpha of ~0 as opaque, so nearly invisible text is skipped instead. */
    private static final int MIN_TEXT_ALPHA = 8;

    private final SubmitNodeStorage storage;
    final PoseStack pose;
    private int order;
    float alpha = 1;

    HologramPainter(SubmitNodeStorage storage, PoseStack pose, int firstOrder) {
        this.storage = storage;
        this.pose = pose;
        this.order = firstOrder;
    }

    // ── Geometry ──────────────────────────────────────────────────────────────

    /** A textured rectangle with a vertical colour gradient. */
    void quad(RenderType type, float x0, float y0, float x1, float y1,
              float u0, float v0, float u1, float v1, int top, int bottom) {
        int ct = fade(top), cb = fade(bottom);
        if (((ct | cb) >>> 24) == 0) return;
        storage.order(order++).submitCustomGeometry(pose, type, (p, vc) -> {
            vc.addVertex(p, x0, y0, 0).setUv(u0, v0).setColor(ct);
            vc.addVertex(p, x0, y1, 0).setUv(u0, v1).setColor(cb);
            vc.addVertex(p, x1, y1, 0).setUv(u1, v1).setColor(cb);
            vc.addVertex(p, x1, y0, 0).setUv(u1, v0).setColor(ct);
        });
    }

    void sprite(RenderType type, float x0, float y0, float x1, float y1, int argb) {
        quad(type, x0, y0, x1, y1, 0, 0, 1, 1, argb, argb);
    }

    /** Solid (untextured-looking) rectangle. */
    void fill(float x0, float y0, float x1, float y1, int argb) {
        quad(HologramRenderTypes.WHITE.translucent(), x0, y0, x1, y1, 0.25f, 0.25f, 0.75f, 0.75f, argb, argb);
    }

    /**
     * Nine-slice: corners keep their size ({@code sliceTexels} of the texture shown as {@code sliceUnits}),
     * edges stretch along one axis, the centre along both. Gradient runs top to bottom.
     */
    void nineSlice(HologramRenderTypes.Tex tex, boolean additive, float x0, float y0, float x1, float y1,
                   float sliceTexels, float sliceUnits, int top, int bottom) {
        int ct = fade(top), cb = fade(bottom);
        if (((ct | cb) >>> 24) == 0) return;
        float sx = Math.min(sliceUnits, (x1 - x0) / 2), sy = Math.min(sliceUnits, (y1 - y0) / 2);
        float su = sliceTexels / tex.width * (sx / sliceUnits), sv = sliceTexels / tex.height * (sy / sliceUnits);
        float[] xs = {x0, x0 + sx, x1 - sx, x1};
        float[] ys = {y0, y0 + sy, y1 - sy, y1};
        float[] us = {0, su, 1 - su, 1};
        float[] vs = {0, sv, 1 - sv, 1};
        int[] cs = new int[4];
        for (int i = 0; i < 4; i++) cs[i] = lerpColor(ct, cb, (ys[i] - y0) / Math.max(1e-4f, y1 - y0));
        storage.order(order++).submitCustomGeometry(pose, additive ? tex.additive() : tex.translucent(), (p, vc) -> {
            for (int j = 0; j < 3; j++) {
                for (int i = 0; i < 3; i++) {
                    vc.addVertex(p, xs[i], ys[j], 0).setUv(us[i], vs[j]).setColor(cs[j]);
                    vc.addVertex(p, xs[i], ys[j + 1], 0).setUv(us[i], vs[j + 1]).setColor(cs[j + 1]);
                    vc.addVertex(p, xs[i + 1], ys[j + 1], 0).setUv(us[i + 1], vs[j + 1]).setColor(cs[j + 1]);
                    vc.addVertex(p, xs[i + 1], ys[j], 0).setUv(us[i + 1], vs[j]).setColor(cs[j]);
                }
            }
        });
    }

    /**
     * Horizontal three-slice: the left/right caps keep their shape ({@code capTexels} shown as
     * {@code capUnits}), the middle stretches. Height maps to the full texture height.
     */
    void threeSlice(HologramRenderTypes.Tex tex, boolean additive, float x0, float y0, float x1, float y1,
                    float capTexels, float capUnits, int top, int bottom) {
        int ct = fade(top), cb = fade(bottom);
        if (((ct | cb) >>> 24) == 0) return;
        float cap = Math.min(capUnits, (x1 - x0) / 2);
        float cu = capTexels / tex.width * (cap / capUnits);
        float[] xs = {x0, x0 + cap, x1 - cap, x1};
        float[] us = {0, cu, 1 - cu, 1};
        storage.order(order++).submitCustomGeometry(pose, additive ? tex.additive() : tex.translucent(), (p, vc) -> {
            for (int i = 0; i < 3; i++) {
                vc.addVertex(p, xs[i], y0, 0).setUv(us[i], 0).setColor(ct);
                vc.addVertex(p, xs[i], y1, 0).setUv(us[i], 1).setColor(cb);
                vc.addVertex(p, xs[i + 1], y1, 0).setUv(us[i + 1], 1).setColor(cb);
                vc.addVertex(p, xs[i + 1], y0, 0).setUv(us[i + 1], 0).setColor(ct);
            }
        });
    }

    /** One icon cell, optionally rotated about its centre. */
    void icon(HologramIcon icon, float cx, float cy, float size, int argb, float radians) {
        float u0 = (float) icon.ordinal() / HologramIcon.CELLS, u1 = (float) (icon.ordinal() + 1) / HologramIcon.CELLS;
        pose.pushPose();
        pose.translate(cx, cy, 0);
        if (radians != 0) pose.mulPose(new org.joml.Quaternionf().rotationZ(radians));
        float h = size / 2;
        quad(HologramRenderTypes.ICONS.translucent(), -h, -h, h, h, u0, 0, u1, 1, argb, argb);
        pose.popPose();
    }

    // ── Text ──────────────────────────────────────────────────────────────────

    /** Text follows the pass: see-through (overlay), depth-tested (world), or omitted (x-ray silhouette). */
    private static Font.@org.jetbrains.annotations.Nullable DisplayMode textMode() {
        return switch (HologramRenderTypes.mode) {
            case OVERLAY -> Font.DisplayMode.SEE_THROUGH;
            case WORLD -> Font.DisplayMode.POLYGON_OFFSET;
            case XRAY -> null;
        };
    }

    void text(FormattedCharSequence seq, float x, float y, float scale, int argb) {
        int c = fade(argb);
        Font.DisplayMode mode = textMode();
        if ((c >>> 24) < MIN_TEXT_ALPHA || mode == null) return;
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        storage.order(order++).submitText(pose, 0, 0, seq, false, mode, FULL_BRIGHT, c, 0, 0);
        pose.popPose();
    }

    /** Letter-spaced single-style text (headers, button labels). */
    void spacedText(Font font, String text, float x, float y, float scale, float spacing, int argb) {
        int c = fade(argb);
        Font.DisplayMode mode = textMode();
        if ((c >>> 24) < MIN_TEXT_ALPHA || mode == null) return;
        float cx = x;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            if (cp != ' ') {
                pose.pushPose();
                pose.translate(cx, y, 0);
                pose.scale(scale, scale, 1);
                storage.order(order++).submitText(pose, 0, 0, FormattedCharSequence.forward(ch, Style.EMPTY), false,
                        mode, FULL_BRIGHT, c, 0, 0);
                pose.popPose();
            }
            cx += font.width(ch) * scale + spacing;
            i += Character.charCount(cp);
        }
    }

    /** The current pose matrix (panel-local units → camera-relative world). */
    Matrix4f matrix() {
        return new Matrix4f(pose.last().pose());
    }

    // ── Colour helpers ────────────────────────────────────────────────────────

    private int fade(int argb) {
        if (alpha >= 1) return argb;
        int a = Math.round((argb >>> 24) * Mth.clamp(alpha, 0, 1));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    static int argb(float alpha, int rgb) {
        return (Math.round(Mth.clamp(alpha, 0, 1) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    static int mixRgb(int a, int b, float t) {
        int r = Math.round(Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
        int bl = Math.round(Mth.lerp(t, a & 0xFF, b & 0xFF));
        return (r << 16) | (g << 8) | bl;
    }

    private static int lerpColor(int a, int b, float t) {
        int alpha = Math.round(Mth.lerp(t, a >>> 24, b >>> 24));
        return (alpha << 24) | mixRgb(a, b, t);
    }
}

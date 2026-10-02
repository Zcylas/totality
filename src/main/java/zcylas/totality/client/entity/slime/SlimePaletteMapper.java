package zcylas.totality.client.entity.slime;

/**
 * Builds a Small Slime body texture from the grayscale source (pure function on ARGB pixels): the palette's gradient
 * map on every texel a body face uses (a defined height in {@link SlimeTexelHeights}), optionally an element
 * {@link SlimeMotif} mask composited into it, and optionally element eye colours on the eye texels. Alpha is kept;
 * every other texel is copied unchanged.
 */
public final class SlimePaletteMapper {

    private SlimePaletteMapper() {}

    /** The eye plate's front texels (6x12 at 0,64), the only texels the eye colours touch. */
    static final int EYE_X = 0, EYE_Y = 64, EYE_W = 6, EYE_H = 12;
    /** Overlay colours are shaded by the texel's gray value relative to this level (full colour at and above it). */
    private static final float OVERLAY_FULL_GRAY = 0.85F;
    private static final float OVERLAY_MIN_SHADE = 0.45F;

    public static int[] remap(int[] argb, float[] heights, SlimePalette palette) {
        return remap(argb, heights, palette, null, 0, null, 0);
    }

    /**
     * @param motif         a motif mask of the same size, or null for the palette only
     * @param overlayColour the motif's overlay colour (0xRRGGBB)
     * @param eyes          element eye colours, or null to leave the eye texels unchanged
     * @param width         texture width, needed to locate the eye region (ignored when {@code eyes} is null)
     */
    public static int[] remap(int[] argb, float[] heights, SlimePalette palette, int[] motif, int overlayColour,
                              SlimeEyeColours eyes, int width) {
        if (argb.length != heights.length) throw new IllegalArgumentException("pixel and height maps differ in size");
        if (motif != null && motif.length != argb.length) throw new IllegalArgumentException("motif mask differs in size");
        int[] out = argb.clone();
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            int alpha = p >>> 24;
            if (alpha == 0) continue;
            float h = heights[i];
            if (Float.isNaN(h)) {
                if (eyes != null && inEyeRegion(i, width)) out[i] = alpha << 24 | eyeColour(p & 0xFF, eyes);
                continue;
            }
            float gray = (((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF)) / (3.0F * 255.0F);
            int m = motif == null ? 0 : motif[i];
            if (m >>> 24 == 0) {
                out[i] = alpha << 24 | palette.colour(gray, h);
                continue;
            }
            float tone = (((m >> 16) & 0xFF) - 128) / 255.0F;
            float heightOffset = (((m >> 8) & 0xFF) - 128) / 255.0F;
            float overlay = (m & 0xFF) / 255.0F;
            float g = Math.clamp(gray + tone, 0.0F, 1.0F);
            int c = palette.colour(g, Math.clamp(h + heightOffset, 0.0F, 1.0F));
            if (overlay > 0.0F) {
                float shade = Math.clamp(g / OVERLAY_FULL_GRAY, OVERLAY_MIN_SHADE, 1.0F);
                c = SlimePalette.lerp(c, scale(overlayColour, shade), overlay);
            }
            out[i] = alpha << 24 | c;
        }
        return out;
    }

    static boolean inEyeRegion(int index, int width) {
        int x = index % width, y = index / width;
        return x >= EYE_X && x < EYE_X + EYE_W && y >= EYE_Y && y < EYE_Y + EYE_H;
    }

    /** The eye texels have four gray levels: rim 58, lower shade 214, interior 240, highlight 255. */
    static int eyeColour(int gray, SlimeEyeColours eyes) {
        if (gray < 128) return eyes.rim();
        if (gray < 228) return eyes.glow();
        if (gray < 250) return eyes.interior();
        return SlimePalette.lerp(eyes.interior(), 0xFFFFFF, 0.5F);
    }

    private static int scale(int rgb, float f) {
        int r = Math.round(((rgb >> 16) & 0xFF) * f), g = Math.round(((rgb >> 8) & 0xFF) * f), b = Math.round((rgb & 0xFF) * f);
        return r << 16 | g << 8 | b;
    }
}

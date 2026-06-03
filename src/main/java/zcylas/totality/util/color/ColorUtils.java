package zcylas.totality.util.color;

import net.minecraft.util.Mth;

/**
 * Static ARGB color utilities.
 *
 * <p>All colors are packed ints in {@code ARGB} order (bits 31-24 = alpha, 23-16 = red,
 * 15-8 = green, 7-0 = blue), matching vanilla {@code ARGB} / {@code FastColor} conventions.
 *
 * <p>Key additions over vanilla:
 * <ul>
 *   <li>{@link #blend(int, int, float)} — lerp between two packed colors by ratio.</li>
 *   <li>{@link #rgba(float, float, float, float)} — build a packed color from float [0,1] channels.</li>
 *   <li>{@link #setAlpha(int, int)} — replace alpha channel without touching RGB.</li>
 *   <li>{@link #subtract(int, int)} — clamped per-channel subtraction.</li>
 * </ul>
 *
 * Ported from CreativeCore {@code ColorUtils} (team.creative.creativecore).
 */
public final class ColorUtils {

    private ColorUtils() {}

    // -------------------------------------------------------------------------
    // Common opaque color constants (ARGB, alpha = 0xFF)
    // -------------------------------------------------------------------------

    public static final int WHITE      = 0xFFFFFFFF;
    public static final int RED        = 0xFFFF0000;
    public static final int GREEN      = 0xFF00FF00;
    public static final int BLUE       = 0xFF0000FF;
    public static final int LIGHT_BLUE = 0xFF00BFFF;
    public static final int ORANGE     = 0xFFFFA500;
    public static final int YELLOW     = 0xFFFFFF00;
    public static final int CYAN       = 0xFF00FFFF;
    public static final int MAGENTA    = 0xFFFF00FF;
    public static final int BLACK      = 0xFF000000;
    public static final int GRAY       = 0xFFAAAAAA;
    public static final int DARK_GRAY  = 0xFF555555;

    // -------------------------------------------------------------------------
    // Channel extraction
    // -------------------------------------------------------------------------

    public static int alpha(int color) { return (color >> 24) & 0xFF; }
    public static int red  (int color) { return (color >> 16) & 0xFF; }
    public static int green(int color) { return (color >>  8) & 0xFF; }
    public static int blue (int color) { return  color        & 0xFF; }

    public static float alphaF(int color) { return alpha(color) / 255f; }
    public static float redF  (int color) { return red  (color) / 255f; }
    public static float greenF(int color) { return green(color) / 255f; }
    public static float blueF (int color) { return blue (color) / 255f; }

    // -------------------------------------------------------------------------
    // Color construction
    // -------------------------------------------------------------------------

    /** Packs four [0-255] int channels into a single ARGB int. */
    public static int rgba(int r, int g, int b, int a) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /** Packs four [0.0-1.0] float channels into a single ARGB int. */
    public static int rgba(float r, float g, float b, float a) {
        return rgba((int)(r * 255), (int)(g * 255), (int)(b * 255), (int)(a * 255));
    }

    /** Packs three [0-255] int channels into a single ARGB int (alpha = 255). */
    public static int rgb(int r, int g, int b) {
        return rgba(r, g, b, 255);
    }

    /** Packs three [0.0-1.0] float channels into a single ARGB int (alpha = 1.0). */
    public static int rgb(float r, float g, float b) {
        return rgba(r, g, b, 1f);
    }

    // -------------------------------------------------------------------------
    // Channel manipulation
    // -------------------------------------------------------------------------

    /** Returns {@code color} with its alpha channel replaced by {@code alpha} [0-255]. */
    public static int setAlpha(int color, int alpha) {
        return ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF);
    }

    // -------------------------------------------------------------------------
    // Blend / mix
    // -------------------------------------------------------------------------

    /** Blends two packed ARGB colors 50/50. */
    public static int blend(int c1, int c2) {
        return blend(c1, c2, 0.5f);
    }

    /**
     * Lerps between two packed ARGB colors.
     *
     * @param ratio 0.0 → entirely {@code c1}, 1.0 → entirely {@code c2}
     */
    public static int blend(int c1, int c2, float ratio) {
        ratio = Mth.clamp(ratio, 0f, 1f);
        float inv = 1f - ratio;

        int a = (int)((alpha(c1) * inv) + (alpha(c2) * ratio));
        int r = (int)((red  (c1) * inv) + (red  (c2) * ratio));
        int g = (int)((green(c1) * inv) + (green(c2) * ratio));
        int b = (int)((blue (c1) * inv) + (blue (c2) * ratio));

        return rgba(r, g, b, a);
    }

    // -------------------------------------------------------------------------
    // Arithmetic
    // -------------------------------------------------------------------------

    /**
     * Subtracts each channel of {@code value} from {@code source}, clamped to [0, 255].
     * Useful for darkening or desaturating a color in-place.
     */
    public static int subtract(int source, int value) {
        return rgba(
            Mth.clamp(red  (source) - red  (value), 0, 255),
            Mth.clamp(green(source) - green(value), 0, 255),
            Mth.clamp(blue (source) - blue (value), 0, 255),
            Mth.clamp(alpha(source) - alpha(value), 0, 255)
        );
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public static boolean isTransparent(int color) { return alpha(color) < 255; }
    public static boolean isInvisible  (int color) { return alpha(color) == 0;  }
    public static boolean isWhite      (int color) { return (color & 0x00FFFFFF) == 0x00FFFFFF; }

    // -------------------------------------------------------------------------
    // Hex string helpers
    // -------------------------------------------------------------------------

    /** Returns the 6-digit hex string (RGB, no alpha) of a packed color, e.g. {@code "FF0000"}. */
    public static String toHexRGB(int color) {
        return String.format("%06X", color & 0xFFFFFF);
    }

    /** Returns the 8-digit hex string (ARGB) of a packed color, e.g. {@code "FFFF0000"}. */
    public static String toHexARGB(int color) {
        return String.format("%08X", color);
    }
}

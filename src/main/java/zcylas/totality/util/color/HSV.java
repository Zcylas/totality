package zcylas.totality.util.color;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Helper for packing/unpacking HSV (Hue, Saturation, Value) color components
 * to and from an integer, and for converting between RGB and HSV.
 *
 * <p>Packed HSV format: {@code H[23:16] | S[15:8] | V[7:0]} (no alpha).</p>
 *
 * <p>Ported from puzzles-lib (Fuzs). RGB-to-HSV algorithm by ChatGPT via that lib.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * // Pulse brightness on a color each tick:
 * int rgb   = 0xFF_AA44FF;
 * int hsv   = HSV.rgbToHsv(rgb);
 * float v   = HSV.valueFloat(hsv);
 * float newV = Mth.sin(tick * 0.1f) * 0.3f + 0.7f; // 0.4 – 1.0
 * int pulsed = HSV.hsvToRgb(HSV.hueFloat(hsv), HSV.saturationFloat(hsv), newV);
 * }</pre>
 */
public final class HSV {

    private HSV() {}

    // -----------------------------------------------------------------------
    // Unpack (int → component)
    // -----------------------------------------------------------------------

    public static int hue(int packed)        { return packed >> 16 & 0xFF; }
    public static int saturation(int packed) { return packed >> 8  & 0xFF; }
    public static int value(int packed)      { return packed       & 0xFF; }

    public static float hueFloat(int packed)        { return hue(packed)        / 255.0f; }
    public static float saturationFloat(int packed) { return saturation(packed) / 255.0f; }
    public static float valueFloat(int packed)      { return value(packed)      / 255.0f; }

    // -----------------------------------------------------------------------
    // Pack (components → int)
    // -----------------------------------------------------------------------

    public static int color(int hue, int saturation, int value) {
        return hue << 16 | saturation << 8 | value;
    }

    public static int colorFromFloat(float hue, float saturation, float value) {
        return color(ARGB.as8BitChannel(hue), ARGB.as8BitChannel(saturation), ARGB.as8BitChannel(value));
    }

    // -----------------------------------------------------------------------
    // Conversion
    // -----------------------------------------------------------------------

    /** Convert a packed ARGB/RGB integer to a packed HSV integer. Alpha is ignored. */
    public static int rgbToHsv(int rgb) {
        return rgbToHsv(ARGB.redFloat(rgb), ARGB.greenFloat(rgb), ARGB.blueFloat(rgb));
    }

    /**
     * Convert float RGB components [0,1] to a packed HSV integer.
     */
    public static int rgbToHsv(float red, float green, float blue) {
        float max   = Math.max(red, Math.max(green, blue));
        float min   = Math.min(red, Math.min(green, blue));
        float delta = max - min;

        float hue = 0.0f;
        if (delta != 0.0f) {
            if (max == red)        hue = (green - blue) / delta % 6.0f;
            else if (max == green) hue = (blue  - red)  / delta + 2.0f;
            else                   hue = (red   - green) / delta + 4.0f;

            hue /= 6.0f;
            if (hue < 0.0f) hue += 1.0f;
        }

        float saturation = max == 0.0f ? 0.0f : delta / max;
        return colorFromFloat(hue, saturation, max);
    }

    /** Convert a packed HSV integer to a packed RGB integer (no alpha). */
    public static int hsvToRgb(int packed) {
        return hsvToRgb(hueFloat(packed), saturationFloat(packed), valueFloat(packed));
    }

    /** Convert float HSV components [0,1] to a packed RGB integer (no alpha). */
    public static int hsvToRgb(float hue, float saturation, float value) {
        return Mth.hsvToRgb(hue, saturation, value);
    }

    /**
     * Modify only the brightness (value) component of a packed ARGB color, preserving hue and saturation.
     * Alpha in the original is preserved in the output.
     */
    public static int withBrightness(int argb, float newValue) {
        int alpha = argb & 0xFF000000;
        int hsv   = rgbToHsv(argb);
        int rgb   = hsvToRgb(hueFloat(hsv), saturationFloat(hsv), newValue);
        return alpha | (rgb & 0x00FFFFFF);
    }
}

package zcylas.totality.client.entity.slime;

/**
 * A gradient map for the grayscale Small Slime texture: a five-stop colour ramp over the texel's gray value, optionally
 * blended toward a second ramp near the top of the body (Dendro's green dome, Cryo's snow cap) between the heights
 * {@code topFrom} and {@code topTo} (0 at the base, 1 at the dome top). The stops sit at the
 * texture's measured gray range (min 0.33, median 0.66, p90 0.85, max 0.98); gray values outside them clamp to the end
 * colours. Colours are 0xRRGGBB.
 */
public record SlimePalette(String name, int[] ramp, int[] topRamp, float topFrom, float topTo) {

    /** Gray positions of the ramp colours. */
    public static final float[] STOPS = {0.30F, 0.50F, 0.68F, 0.85F, 0.98F};

    public SlimePalette {
        if (ramp.length != STOPS.length) throw new IllegalArgumentException(name + ": ramp needs " + STOPS.length + " colours");
        if (topRamp != null && topRamp.length != STOPS.length) throw new IllegalArgumentException(name + ": top ramp needs " + STOPS.length + " colours");
        if (topRamp != null && !(topFrom >= 0.0F && topFrom < topTo && topTo <= 1.0F)) {
            throw new IllegalArgumentException(name + ": need 0 <= topFrom < topTo <= 1");
        }
        ramp = ramp.clone();
        topRamp = topRamp == null ? null : topRamp.clone();
    }

    public static SlimePalette of(String name, int... ramp) {
        return new SlimePalette(name, ramp, null, 0.0F, 1.0F);
    }

    /** A top ramp blending in from {@code from} up to the dome top. */
    public SlimePalette withTop(float from, int... top) {
        return withTop(from, 1.0F, top);
    }

    /** A top ramp blending in between the heights {@code from} and {@code to}, fully applied above {@code to}. */
    public SlimePalette withTop(float from, float to, int... top) {
        return new SlimePalette(this.name, this.ramp, top, from, to);
    }

    public SlimePalette named(String newName) {
        return new SlimePalette(newName, this.ramp, this.topRamp, this.topFrom, this.topTo);
    }

    /**
     * The 0xRRGGBB colour for a texel of the given gray value (0..1) at the given body height (0 at the base, 1 at the
     * dome top; ignored without a top ramp). The top ramp's weight rises with a smoothstep from {@link #topFrom} to
     * {@link #topTo}.
     */
    public int colour(float gray, float height) {
        int base = sample(this.ramp, gray);
        if (this.topRamp == null) return base;
        float t = Math.clamp((height - this.topFrom) / (this.topTo - this.topFrom), 0.0F, 1.0F);
        float w = t * t * (3.0F - 2.0F * t);
        return w <= 0.0F ? base : lerp(base, sample(this.topRamp, gray), w);
    }

    private static int sample(int[] colours, float gray) {
        if (gray <= STOPS[0]) return colours[0];
        for (int i = 1; i < STOPS.length; i++) {
            if (gray <= STOPS[i]) return lerp(colours[i - 1], colours[i], (gray - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1]));
        }
        return colours[STOPS.length - 1];
    }

    static int lerp(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | g << 8 | bl;
    }
}

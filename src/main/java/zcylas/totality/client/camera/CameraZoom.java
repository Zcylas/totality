package zcylas.totality.client.camera;

import java.util.List;
import java.util.Locale;

/**
 * The Camera's zoom: a target magnification (presets or wheel steps) and a smoothed current value that follows it.
 * Shared by every camera mode (Normal now, Scan later). Pure: time comes in as nanoseconds, so it is unit-tested.
 *
 * <p>Magnification maps to a real projection: {@code tan(fov / 2)} scales by {@code 1 / zoom}, exactly how a phone's
 * 0.5× ultra-wide relates to its 1× main lens. 0.5× is therefore a genuinely wider rendered view, not a label; the
 * player's own FOV setting is the 1× reference.
 */
public final class CameraZoom {

    /** What a phone tier's camera can do: the magnification limits and its preset buttons (ascending). */
    public record Range(float min, float max, List<Float> presets) {
        public Range {
            presets = List.copyOf(presets);
            if (min <= 0 || max < min) throw new IllegalArgumentException("bad zoom range " + min + ".." + max);
        }
    }

    /** The Basic Copper Phone: 0.5×, 1× and 2×; the wheel moves smoothly anywhere between. */
    public static final Range BASIC = new Range(0.5f, 2f, List.of(0.5f, 1f, 2f));

    /** One wheel notch multiplies the zoom by this (six notches per doubling). */
    static final double WHEEL_STEP = Math.pow(2, 1 / 6.0);
    /** Smoothing time constant: the current zoom covers ~95% of a change in 3 τ (~0.2 s). */
    static final double TAU_NANOS = 70_000_000.0;
    /** The widest projection we ever ask for (degrees); beyond this the image degenerates. */
    static final float MAX_FOV = 165f;

    private final Range range;
    private float target = 1f;
    private float current = 1f;
    private long lastNanos = -1;

    public CameraZoom(Range range) {
        this.range = range;
    }

    public Range range() {
        return range;
    }

    public float target() {
        return target;
    }

    /** The smoothed magnification as of the last {@link #update}. */
    public float current() {
        return current;
    }

    public void setTarget(float zoom) {
        target = clamp(zoom);
    }

    /** Jumps to {@code zoom} with no transition (restoring a session). */
    public void snapTo(float zoom) {
        target = current = clamp(zoom);
    }

    /** Wheel: positive notches zoom in, negative out, multiplicatively (so 0.5→1 feels like 1→2). */
    public void wheel(double notches) {
        setTarget((float) (target * Math.pow(WHEEL_STEP, notches)));
    }

    /** The next preset above ({@code direction > 0}) or below the current target, or the target itself at the end. */
    public void stepPreset(int direction) {
        List<Float> p = range.presets();
        if (direction > 0) {
            for (float z : p) if (z > target + 1e-3f) { setTarget(z); return; }
        } else {
            for (int i = p.size() - 1; i >= 0; i--) if (p.get(i) < target - 1e-3f) { setTarget(p.get(i)); return; }
        }
    }

    /** The preset closest to the target (logarithmically): the one the indicator highlights. */
    public int nearestPreset() {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < range.presets().size(); i++) {
            double d = Math.abs(Math.log(range.presets().get(i) / target));
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /** Advances the smoothing to {@code nowNanos}; returns the current magnification. */
    public float update(long nowNanos) {
        if (lastNanos >= 0 && current != target) {
            double dt = Math.max(0, nowNanos - lastNanos);
            current = (float) (target + (current - target) * Math.exp(-dt / TAU_NANOS));
            if (Math.abs(current - target) < 0.002f) current = target;
        }
        lastNanos = nowNanos;
        return current;
    }

    private float clamp(float zoom) {
        return Math.max(range.min(), Math.min(range.max(), zoom));
    }

    /** The vertical field of view (degrees) for {@code zoom} when 1× is {@code baseFov}. */
    public static float fov(float baseFov, float zoom) {
        double half = Math.toRadians(baseFov) / 2;
        double fov = Math.toDegrees(2 * Math.atan(Math.tan(half) / zoom));
        return (float) Math.min(MAX_FOV, fov);
    }

    /** Mouse-look sensitivity multiplier: slower when magnified (like a spyglass), never faster when wide. */
    public static double sensitivity(float zoom) {
        return Math.min(1.0, 1.0 / zoom);
    }

    /** Compact indicator text: "0.5×", "1×", "1.4×", "2×". */
    public static String label(float zoom) {
        float rounded = Math.round(zoom * 10) / 10f;
        String s = rounded == Math.round(rounded) ? Integer.toString(Math.round(rounded))
                : String.format(Locale.ROOT, "%.1f", rounded);
        return s + "×";
    }
}

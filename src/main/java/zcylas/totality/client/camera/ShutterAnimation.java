package zcylas.totality.client.camera;

/**
 * The shutter feedback timeline, as pure geometry: a brief flash, the captured frame shrinking slightly over the
 * viewfinder, then flying into the Gallery thumbnail (bottom left), whose square crop it becomes. The Camera is ready
 * for another photograph at every point; a new capture simply starts a new timeline.
 *
 * <p>Reduced motion (vanilla "Distortion Effects" at 0) skips the flight: a short flash and the thumbnail updates at
 * once. "Hide Lightning Flashes" replaces the white flash with a gentle dark blink.
 */
public final class ShutterAnimation {

    static final long FLASH_NANOS = 120_000_000L;
    static final long SHRINK_NANOS = 200_000_000L;
    static final long HOLD_NANOS = 330_000_000L;
    static final long FLY_NANOS = 640_000_000L;
    static final long REDUCED_NANOS = 140_000_000L;
    /** How far the photo shrinks before it leaves (fraction of the screen). */
    static final float SHRUNK = 0.82f;

    private ShutterAnimation() {}

    public record Rect(float x, float y, float w, float h) {}

    /**
     * One frame. {@code flash}: white (or dark, see class doc) overlay alpha. {@code photo}: where the captured image is
     * drawn, or null when it isn't. {@code crop}: 0 = the whole image, 1 = its centred square (the thumbnail's crop).
     * {@code done}: the thumbnail now shows the new photograph.
     */
    public record Frame(float flash, Rect photo, float crop, boolean done) {}

    public static long duration(boolean reduced) {
        return reduced ? REDUCED_NANOS : FLY_NANOS;
    }

    public static Frame frame(long elapsed, Rect screen, Rect thumb, boolean reduced) {
        float flash = elapsed < FLASH_NANOS ? 0.85f * (1 - easeOut(elapsed / (float) FLASH_NANOS)) : 0f;
        if (reduced) {
            return new Frame(flash, null, 1f, elapsed >= REDUCED_NANOS);
        }
        if (elapsed >= FLY_NANOS) return new Frame(0f, null, 1f, true);
        if (elapsed < HOLD_NANOS) {
            float s = 1 - (1 - SHRUNK) * easeOut(Math.min(1f, elapsed / (float) SHRINK_NANOS));
            float w = screen.w() * s, h = screen.h() * s;
            return new Frame(flash, new Rect(screen.x() + (screen.w() - w) / 2, screen.y() + (screen.h() - h) / 2, w, h), 0f, false);
        }
        float t = easeInOut((elapsed - HOLD_NANOS) / (float) (FLY_NANOS - HOLD_NANOS));
        float fw = screen.w() * SHRUNK, fh = screen.h() * SHRUNK;
        Rect from = new Rect(screen.x() + (screen.w() - fw) / 2, screen.y() + (screen.h() - fh) / 2, fw, fh);
        return new Frame(0f, lerp(from, thumb, t), t, false);
    }

    static Rect lerp(Rect a, Rect b, float t) {
        return new Rect(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t,
                a.w() + (b.w() - a.w()) * t, a.h() + (b.h() - a.h()) * t);
    }

    static float easeOut(float t) {
        float u = 1 - Math.max(0, Math.min(1, t));
        return 1 - u * u * u;
    }

    static float easeInOut(float t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    /**
     * The texture coordinates (u0, v0, u1, v1, in 0..1 of a {@code width}x{@code height} image) for crop amount
     * {@code crop}: the whole image at 0, the centred square at 1.
     */
    public static float[] cropUv(int width, int height, float crop) {
        float aspect = width / (float) height;
        if (aspect >= 1) {
            float visible = 1 + (1 / aspect - 1) * crop;
            float u0 = (1 - visible) / 2;
            return new float[] {u0, 0, u0 + visible, 1};
        }
        float visible = 1 + (aspect - 1) * crop;
        float v0 = (1 - visible) / 2;
        return new float[] {0, v0, 1, v0 + visible};
    }
}

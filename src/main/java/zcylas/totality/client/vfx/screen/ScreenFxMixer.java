package zcylas.totality.client.vfx.screen;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The Shared Screen FX resolver: live requests, their expiry, cancellation, and the per-frame merge. Pure logic (no
 * rendering, no Minecraft client state), so every rule is unit-tested.
 *
 * <p>Per channel and frame:
 * <ol>
 *   <li>contribution {@code v = intensity × envelope(t) × falloff(distance)} (flash: × {@link #BEHIND_CAMERA_FLASH}
 *       when the origin is behind the camera); requests for another audience contribute nothing;</li>
 *   <li>only the highest priority with a contribution counts;</li>
 *   <li>diminishing merge {@code v0 + 0.25 v1 + 0.10 v2} of the three strongest, so the result is at most
 *       {@link #MAX_MERGE_FACTOR} × the strongest single contribution, then the channel's hard cap (1.0);</li>
 *   <li>accessibility scale;</li>
 *   <li>flash only: the anti-strobe limits, an energy budget (token bucket: at most {@link #FLASH_BUDGET_SECONDS} of
 *       full-cap flash at once, refilled at {@link #FLASH_REFILL_PER_SECOND} per second) and an onset limiter (a new
 *       rise at most every {@link #MIN_FLASH_ONSET_INTERVAL} seconds).</li>
 * </ol>
 * Resolving twice at the same time returns the same frame, so several readers per frame (camera, HUD) agree.
 */
public final class ScreenFxMixer {

    public static final double SECOND_WEIGHT = 0.25;
    public static final double THIRD_WEIGHT = 0.10;
    public static final double MAX_MERGE_FACTOR = 1.0 + SECOND_WEIGHT + THIRD_WEIGHT;
    public static final double CHANNEL_CAP = 1.0;
    public static final double BEHIND_CAMERA_FLASH = 0.3;
    public static final double FLASH_BUDGET_SECONDS = 0.5;
    public static final double FLASH_REFILL_PER_SECOND = 0.25;
    public static final double MIN_FLASH_ONSET_INTERVAL = 1.0 / 3.0;
    /** Longest frame step used for budget refill and consumption (a hitch or a pause must not refill everything). */
    static final double MAX_STEP = 0.25;
    private static final double EPS = 1.0e-4;

    private record Entry(long handle, ScreenFxRequest request, double start) {}

    private final List<Entry> entries = new ArrayList<>();
    private long nextHandle = 1;
    private double tokens = FLASH_BUDGET_SECONDS;
    private double lastNow = Double.NaN;
    private ScreenFxFrame last = ScreenFxFrame.NONE;
    private double prevFlash;
    private boolean flashRising;
    private double lastOnset = Double.NEGATIVE_INFINITY;
    private int onsets;
    private long requested;

    /** Adds a request starting at {@code now}; returns its handle. */
    public long add(ScreenFxRequest request, double now) {
        long handle = nextHandle++;
        entries.add(new Entry(handle, Objects.requireNonNull(request), now));
        requested++;
        return handle;
    }

    public boolean cancel(long handle) {
        return entries.removeIf(e -> e.handle == handle);
    }

    /** Cancels every request of {@code owner}; returns how many were removed. */
    public int cancelOwner(Object owner) {
        int before = entries.size();
        entries.removeIf(e -> owner.equals(e.request.owner()));
        return before - entries.size();
    }

    public void clear() {
        entries.clear();
        tokens = FLASH_BUDGET_SECONDS;
        lastNow = Double.NaN;
        last = ScreenFxFrame.NONE;
        prevFlash = 0;
        flashRising = false;
        lastOnset = Double.NEGATIVE_INFINITY;
    }

    public int liveRequests() {
        return entries.size();
    }

    /** Requests added since creation. */
    public long requestedTotal() {
        return requested;
    }

    /** Flash rises (onsets) allowed since creation. */
    public int flashOnsets() {
        return onsets;
    }

    public double flashTokens() {
        return tokens;
    }

    public ScreenFxFrame last() {
        return last;
    }

    /**
     * Resolves the frame at {@code now} for a camera at {@code camera} looking along {@code look} (unit), for the local
     * player {@code local}. {@code shakeScale} and {@code flashScale} are the accessibility multipliers (0..1).
     */
    public ScreenFxFrame resolve(double now, Vec3 camera, Vec3 look, @Nullable UUID local, double shakeScale, double flashScale) {
        if (now == lastNow) return last;
        double dt = Double.isNaN(lastNow) ? 0.0 : Math.clamp(now - lastNow, 0.0, MAX_STEP);
        lastNow = now;
        entries.removeIf(e -> now - e.start > e.request.envelope().duration());

        double[] shake = merge(ScreenFxChannel.SHAKE, now, camera, look, local);
        double[] flash = merge(ScreenFxChannel.FLASH, now, camera, look, local);

        double shakeOut = Math.min(shake[0], CHANNEL_CAP) * Math.clamp(shakeScale, 0.0, 1.0);
        double flashDemand = Math.min(flash[0], CHANNEL_CAP) * Math.clamp(flashScale, 0.0, 1.0);

        // Onset limiter: the flash may start a new rise only every MIN_FLASH_ONSET_INTERVAL seconds.
        double flashOut = flashDemand;
        if (flashOut > prevFlash + EPS) {
            if (!flashRising) {
                if (now - lastOnset < MIN_FLASH_ONSET_INTERVAL) {
                    flashOut = prevFlash;
                } else {
                    flashRising = true;
                    lastOnset = now;
                    onsets++;
                }
            }
        } else if (flashOut < prevFlash - EPS) {
            flashRising = false;
        }
        // Energy budget (token bucket, in seconds of full-cap flash).
        tokens = Math.min(FLASH_BUDGET_SECONDS, tokens + FLASH_REFILL_PER_SECOND * dt);
        if (dt > 0.0) {
            double spend = flashOut * dt;
            if (spend > tokens) {
                flashOut = tokens / dt;
                tokens = 0.0;
            } else {
                tokens -= spend;
            }
        }
        prevFlash = flashOut;

        last = new ScreenFxFrame(shakeOut, flashOut, flashDemand, (int) flash[2], shake[1], flash[1],
                (int) shake[3], (int) flash[3]);
        return last;
    }

    /** {merged, strongest, colour, live count} for one channel. */
    private double[] merge(ScreenFxChannel channel, double now, Vec3 camera, Vec3 look, @Nullable UUID local) {
        List<double[]> contributions = new ArrayList<>();
        int live = 0;
        int topPriority = -1;
        for (Entry e : entries) {
            ScreenFxRequest r = e.request;
            if (r.channel() != channel) continue;
            live++;
            double v = contribution(r, now - e.start, camera, look, local);
            if (v <= EPS) continue;
            int p = r.priority().ordinal();
            if (p > topPriority) {
                topPriority = p;
                contributions.clear();
            }
            if (p == topPriority) contributions.add(new double[]{v, r.colour()});
        }
        if (contributions.isEmpty()) return new double[]{0, 0, 0xFFFFFF, live};
        double[][] sorted = contributions.toArray(new double[0][]);
        Arrays.sort(sorted, (a, b) -> Double.compare(b[0], a[0]));
        double merged = sorted[0][0];
        if (sorted.length > 1) merged += SECOND_WEIGHT * sorted[1][0];
        if (sorted.length > 2) merged += THIRD_WEIGHT * sorted[2][0];
        // Flash colour: contributions' colours weighted by strength.
        double wr = 0, wg = 0, wb = 0, w = 0;
        for (double[] c : sorted) {
            int rgb = (int) c[1];
            wr += c[0] * ((rgb >> 16) & 0xFF);
            wg += c[0] * ((rgb >> 8) & 0xFF);
            wb += c[0] * (rgb & 0xFF);
            w += c[0];
        }
        int colour = ((int) Math.round(wr / w) << 16) | ((int) Math.round(wg / w) << 8) | (int) Math.round(wb / w);
        return new double[]{merged, sorted[0][0], colour, live};
    }

    static double contribution(ScreenFxRequest r, double t, Vec3 camera, Vec3 look, @Nullable UUID local) {
        if (r.audience() == ScreenFxAudience.SUBJECT_ONLY && (local == null || !local.equals(r.subject()))) return 0.0;
        double v = r.intensity() * r.envelope().value(t);
        if (r.origin() != null) {
            Vec3 to = r.origin().subtract(camera);
            v *= r.falloff().factor(to.length());
            if (r.channel() == ScreenFxChannel.FLASH && to.lengthSqr() > 1.0e-6 && to.dot(look) < 0.0) v *= BEHIND_CAMERA_FLASH;
        }
        return v;
    }

    /** Live requests per channel (diagnostics). */
    public int live(ScreenFxChannel channel) {
        int n = 0;
        for (Entry e : entries) if (e.request.channel() == channel) n++;
        return n;
    }
}

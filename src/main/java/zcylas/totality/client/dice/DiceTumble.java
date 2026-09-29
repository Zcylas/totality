package zcylas.totality.client.dice;

import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * The motion of one die on the roll screen, as a pure function of time (milliseconds, any clock): an idle hover, a
 * free tumble from the moment the player rolls, and — once the server's natural roll is known ({@link #land}) — a
 * decelerating tumble that ends exactly on that number: the remaining rotation is unwound about a single axis with
 * extra whole turns (so the speed carries over), then the die rocks once onto its face while its bounces die away.
 * The landed orientation is {@link D20Geometry#settle}, so the face shown is always the authoritative number, whatever
 * the seed, the frame rate or the moment the result arrived. The seed only varies the look of the throw.
 */
public final class DiceTumble {

    /** The die always leaves the hand for at least this long before it may start settling. */
    public static final long MIN_FREE_MS = 260;
    /** From the start of the settle to the die at rest. */
    public static final long SETTLE_MS = 1500;
    private static final float SPIN_END = 0.82f;
    private static final float ROCK_START = 0.70f;
    private static final float ROCK_ANGLE = 0.30f;
    private static final float BOUNCE_S = 0.30f;
    private static final float BOUNCE_DECAY_S = 1.1f;

    private final Quaternionf idleBase;
    private final Vector3f axis1, axis2;
    private final float omega1, omega2;
    private final float wanderX, wanderY, freqX, freqY, phaseX, phaseY;
    private final long staggerMs;

    private long rollStart = -1;
    private Quaternionf thrown;
    private int number;
    private long settleStart = -1;
    private Quaternionf landed;
    private Vector3f unwindAxis;
    private float unwindAngle;
    private boolean skipped;

    public DiceTumble(long seed, long staggerMs) {
        Random r = new Random(seed);
        this.idleBase = new Quaternionf().rotateXYZ(r.nextFloat() * 6.28f, r.nextFloat() * 6.28f, r.nextFloat() * 6.28f);
        this.axis1 = randomAxis(r);
        this.axis2 = randomAxis(r);
        this.omega1 = 9.5f + r.nextFloat() * 3.5f;
        this.omega2 = 5.0f + r.nextFloat() * 3.0f;
        this.wanderX = 0.55f + r.nextFloat() * 0.25f;
        this.wanderY = 0.30f + r.nextFloat() * 0.15f;
        this.freqX = 5.5f + r.nextFloat() * 2f;
        this.freqY = 7.5f + r.nextFloat() * 2f;
        this.phaseX = r.nextFloat() * 6.28f;
        this.phaseY = r.nextFloat() * 6.28f;
        this.staggerMs = staggerMs;
    }

    private static Vector3f randomAxis(Random r) {
        Vector3f v = new Vector3f(r.nextFloat() - 0.5f, r.nextFloat() - 0.5f, r.nextFloat() - 0.5f);
        return v.lengthSquared() < 1e-4f ? new Vector3f(0, 1, 0) : v.normalize();
    }

    /** The player rolled: the die leaves its idle pose. */
    public void roll(long now) {
        if (rollStart >= 0) return;
        thrown = idleOrientation(now);
        rollStart = now;
    }

    /** The authoritative natural value arrived at {@code now}: plan the settle onto it. Only the first call counts. */
    public void land(int naturalValue, long now) {
        if (number != 0 || rollStart < 0) return;
        number = naturalValue;
        landed = D20Geometry.settle(naturalValue);
        settleStart = Math.max(now, rollStart + MIN_FREE_MS) + staggerMs;
        Quaternionf remaining = new Quaternionf(landed).conjugate().mul(freeOrientation(settleStart));
        if (remaining.w < 0) remaining.set(-remaining.x, -remaining.y, -remaining.z, -remaining.w);
        AxisAngle4f aa = new AxisAngle4f(remaining);
        unwindAxis = new Vector3f(aa.x, aa.y, aa.z);
        if (unwindAxis.lengthSquared() < 1e-6f) unwindAxis.set(0, 1, 0);
        unwindAxis.normalize();
        float wanted = (omega1 + omega2) * 0.5f * SPIN_END * (SETTLE_MS / 1000f) / 3f;
        int turns = Math.max(0, Math.round((wanted - aa.angle) / 6.2831855f));
        unwindAngle = aa.angle + turns * 6.2831855f;
    }

    /** Jump straight to rest (only meaningful once the number is known). */
    public void skip() {
        skipped = true;
    }

    public int number() {
        return number;
    }

    public boolean rolling() {
        return rollStart >= 0;
    }

    /** When the die comes to rest, or -1 while its number is unknown. */
    public long restAt() {
        return number == 0 ? -1 : settleStart + SETTLE_MS;
    }

    public boolean atRest(long now) {
        return number != 0 && (skipped || now >= restAt());
    }

    public Quaternionf orientation(long now) {
        if (rollStart < 0) return idleOrientation(now);
        if (number != 0 && (skipped || now >= restAt())) return new Quaternionf(landed);
        if (number == 0 || now < settleStart) return freeOrientation(now);
        float u = (now - settleStart) / (float) SETTLE_MS;
        float spin = (float) Math.pow(1 - Math.min(1f, u / SPIN_END), 3);
        Quaternionf q = new Quaternionf().rotationX(rock(u));
        q.mul(landed);
        q.mul(new Quaternionf().rotationAxis(unwindAngle * spin, unwindAxis));
        return q;
    }

    private static float rock(float u) {
        if (u < ROCK_START) return 0;
        float w = (u - ROCK_START) / (1 - ROCK_START);
        return (float) (ROCK_ANGLE * Math.sin(2 * Math.PI * w) * (1 - w) * (1 - w));
    }

    private Quaternionf idleOrientation(long now) {
        float s = now / 1000f;
        return new Quaternionf(idleBase).rotateLocalY(0.9f * s).rotateLocalX(0.22f * (float) Math.sin(1.1f * s));
    }

    private Quaternionf freeOrientation(long now) {
        float t = (now - rollStart) / 1000f;
        return new Quaternionf(thrown)
                .premul(new Quaternionf().rotationAxis(omega1 * t, axis1))
                .premul(new Quaternionf().rotationAxis(omega2 * t, axis2));
    }

    /** 1 while tumbling freely, easing to 0 at rest. */
    private float travel(long now) {
        if (rollStart < 0) return 0;
        if (number != 0 && (skipped || now >= restAt())) return 0;
        float in = Math.min(1f, (now - rollStart) / 120f);
        if (number == 0 || now < settleStart) return in;
        float u = Math.min(1f, (now - settleStart) / (SETTLE_MS * 0.9f));
        return in * (1 - u) * (1 - u);
    }

    /** Horizontal wander around the arena centre, in die radii (x right, y down); 0 when thrown and at rest. */
    public float offsetX(long now) {
        if (rollStart < 0) return 0;
        float t = (now - rollStart) / 1000f;
        return wanderX * (float) (Math.sin(freqX * t + phaseX) - Math.sin(phaseX)) * travel(now);
    }

    public float offsetY(long now) {
        if (rollStart < 0) return 0.06f * (float) Math.sin(now / 330.0);
        float t = (now - rollStart) / 1000f;
        return wanderY * (float) (Math.sin(freqY * t + phaseY) - Math.sin(phaseY)) * travel(now);
    }

    /** Height of the current hop, 0 (on the table) to 1. */
    public float height(long now) {
        if (rollStart < 0) return 0;
        float t = (now - rollStart) / 1000f;
        return Math.abs((float) Math.sin(Math.PI * t / BOUNCE_S)) * (float) Math.exp(-t / BOUNCE_DECAY_S) * Math.min(1f, travel(now) * 1.5f);
    }

    /** Hops that touched down in (from, to] — for the clatter. */
    public int bounces(long from, long to) {
        if (rollStart < 0 || to <= from) return 0;
        int n = 0;
        long first = (long) Math.floor((from - rollStart) / (BOUNCE_S * 1000)) + 1;
        for (long k = Math.max(1, first); rollStart + k * BOUNCE_S * 1000 <= to; k++) {
            long at = rollStart + (long) (k * BOUNCE_S * 1000);
            if (at > from && travel(at) > 0.12f && Math.exp(-(at - rollStart) / 1000f / BOUNCE_DECAY_S) > 0.12) n++;
        }
        return n;
    }

    /** Angular speed in rad/s (numbers blur when fast). */
    public float spinSpeed(long now) {
        if (rollStart < 0) return 0.9f;
        if (number != 0 && (skipped || now >= restAt())) return 0;
        if (number == 0 || now < settleStart) return (float) Math.sqrt(omega1 * omega1 + omega2 * omega2);
        float u = (now - settleStart) / (float) SETTLE_MS;
        float left = 1 - Math.min(1f, u / SPIN_END);
        return unwindAngle * 3f / (SPIN_END * SETTLE_MS / 1000f) * left * left;
    }
}

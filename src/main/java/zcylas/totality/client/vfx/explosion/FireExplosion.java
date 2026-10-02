package zcylas.totality.client.vfx.explosion;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * One layered fire explosion (VFX Experiment 3, Fireball V2 phase B2): a reusable primitive, not Fireball-specific.
 *
 * <p>Instead of one shell, the explosion is a cloud of many independently animated fire volumes laid out in 3D around
 * the true explosion centre, each shaded procedurally on the GPU ({@code core/vfx_fire}):
 * <ul>
 *   <li><b>body volumes</b> in three layers: a hot core (white-hot to gold, near the centre), a middle layer and an
 *       outer flame front (cooler). Each is blasted out from the centre (an exponential decay of its speed: the outer
 *       front fastest, the core slowest; stretched along its outward path while it moves fast), grows, spins (rolling
 *       motion), cools along its own temperature curve, erodes into holes and ragged tongues, and turns into soot. The
 *       middle and outer layers are spread evenly over the sphere (a jittered Fibonacci lattice), so the flame front
 *       is one connected, ragged mass rather than separate clumps;</li>
 *   <li><b>flame tongues</b>: elongated flames shot out of the mass in the first ~0.1 s, then detaching and dying;</li>
 *   <li><b>ground licks</b>: low flames running out across the ground where the sphere meets it (never in an air
 *       burst);</li>
 *   <li>a short <b>ignition core</b> (0–0.12 s).</li>
 * </ul>
 * Directions pointing into the surface that was hit are mirrored out of it (a wall or floor hit bulges out of the
 * surface), and every volume stops short of the first solid block on its way out (fire does not pass through walls).
 * Fire never reaches past {@code radius}: a body volume's visible disc (half its quad size) is kept inside the sphere
 * for its whole fire phase (the renderer's outward stretch keeps the leading edge in place); only cooling soot may
 * drift upward past it (cosmetic). The outer flame front sits right at that limit at full size, so its visible edge
 * marks the damage radius.
 *
 * <p>Pure model: the world is only consulted through {@link Obstruction} and {@link GroundProbe} at creation, so the
 * geometry rules are unit-tested. Time is in seconds on the caller's clock.
 */
public final class FireExplosion {

    /** Farthest visible edge of a body volume as a fraction of its quad size: nothing is drawn outside the quad. */
    public static final double EDGE = 0.5;
    /** Body volumes fade out between 0.88 and FADE_END of their life. */
    public static final double FADE_END = 1.12;
    public static final double MAX_DELAY = 0.15;
    static final double MAX_GROWTH = 1.18;
    static final double LATE_SPREAD = 0.04;
    static final double HOLD_LOSS = 0.22;
    /** Brief shock heating at the start, decaying with this constant: the core white-hot, the flame front barely. */
    static final double SHOCK_SECONDS = 0.05;
    private static final double[] SHOCK_HEAT = {0.35, 0.18, 0.05};
    /** Erosion from the start, per layer: the outer front is ragged and holed, so the hot heart shows through it. */
    private static final double[] BASE_EROSION = {0.0, 0.05, 0.18};
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    /** Outward stretch of a body volume at launch, per layer (core, middle, outer), fading with its speed. */
    private static final double[] STRETCH = {0.4, 1.0, 1.6};

    public enum Kind { BODY, TONGUE, GROUND, CORE }

    /** Distance from {@code from} along unit {@code dir} that is free of solid blocks, up to {@code max}. */
    @FunctionalInterface
    public interface Obstruction {
        double clearDistance(Vec3 from, Vec3 dir, double max);
    }

    /** The top of the ground under (x, y, z) within {@code depth} blocks, or NaN. */
    @FunctionalInterface
    public interface GroundProbe {
        double groundY(double x, double y, double z, int depth);
    }

    /** One evaluated fire element of a frame (world position; reused objects are fine). */
    public static final class Element {
        public Kind kind;
        public double x, y, z;
        /** Unit axis for TONGUE (outwards) and GROUND (up); BODY: its outward direction (stretch axis); unused for CORE. */
        public double ax, ay, az;
        /** BODY / CORE: quad size; TONGUE / GROUND: width. */
        public double width;
        /** TONGUE / GROUND: length along the axis from (x, y, z). */
        public double length;
        public float heat, erosion, seed, opacity, angle;
        /** BODY: elongation along the outward direction while the flame front is moving fast (1 = none). */
        public float stretch;
        public double sortKey;
        public FireExplosion owner;
    }

    private record Body(Vec3 dir, double distance, double size, double delay, double life, double heatBias,
                        double spin, double angle0, double swirl, double tau, float seed, int layer) {
        /** The hot core holds and cools slowest, the outer flame front fastest: distinct temperature regions over time. */
        double coolingScale() {
            return layer == 0 ? 1.3 : layer == 1 ? 1.0 : 0.75;
        }

        /** Hold, then cool: near full heat (losing {@link #HOLD_LOSS}) for the hold, then an exponential decay. */
        double heat(double a, FireExplosionStyle style) {
            double hold = style.holdSeconds() * (layer == 0 ? 1.4 : layer == 1 ? 1.15 : 1.0);
            double shock = 1.0 + SHOCK_HEAT[layer] * Math.exp(-a / SHOCK_SECONDS);
            if (a < hold) return heatBias * shock * (1.0 - HOLD_LOSS * a / hold);
            return heatBias * (1.0 - HOLD_LOSS) * Math.exp(-(a - hold) / (style.coolingSeconds() * coolingScale()));
        }

        /** Late growth (billowing) and outward drift apply to the inner layers; the outer front holds the boundary. */
        double maxGrowth() {
            return layer == 2 ? 1.0 : MAX_GROWTH;
        }

        double lateSpread() {
            return layer == 2 ? 0.0 : LATE_SPREAD;
        }
    }

    private record Tongue(Vec3 dir, double maxLength, double width, double delay, float seed) {}

    private record Lick(double cos, double sin, double distance, double groundY, double width, double height,
                        double delay, double life, float seed) {}

    private final Vec3 centre;
    private final double radius;
    private final double start;
    private final FireExplosionStyle style;
    private final List<Body> bodies = new ArrayList<>();
    private final List<Tongue> tongues = new ArrayList<>();
    private final List<Lick> licks = new ArrayList<>();
    private final boolean groundReached;

    private FireExplosion(Vec3 centre, double radius, double start, FireExplosionStyle style, boolean groundReached) {
        this.centre = centre;
        this.radius = radius;
        this.start = start;
        this.style = style;
        this.groundReached = groundReached;
    }

    /**
     * Lays out an explosion at {@code centre} (the authoritative explosion centre) that started at {@code now}.
     *
     * @param normal      the hit surface's outward normal, or null for a hit in the open
     * @param groundY     the top of the ground under the centre within {@code radius}, or NaN (air burst)
     */
    public static FireExplosion create(Vec3 centre, @Nullable Vec3 normal, double radius, double groundY, long seed,
                                       FireExplosionStyle style, Obstruction obstruction, GroundProbe ground, double now) {
        double h = centre.y - groundY;
        boolean reached = !Double.isNaN(groundY) && h > -0.5 && h < radius * 0.95;
        FireExplosion e = new FireExplosion(centre, radius, now, style, reached);
        SplittableRandom r = new SplittableRandom(seed);
        double scale = radius / 6.0;

        int n = style.bodyVolumes();
        int cores = (int) Math.round(n * 0.18), middles = (int) Math.round(n * 0.34), outers = n - cores - middles;
        Basis basis = Basis.of(normal, r);
        for (int i = 0; i < n; i++) {
            int layer = i < cores ? 0 : i < cores + middles ? 1 : 2;
            Vec3 dir = switch (layer) {
                case 0 -> outward(r, normal);
                case 1 -> basis.lattice(r, i - cores, middles);
                default -> basis.lattice(r, i - cores - middles, outers);
            };
            double size = scale * switch (layer) {
                case 0 -> range(r, 1.6, 2.6);
                case 1 -> range(r, 2.0, 2.9);
                default -> range(r, 1.6, 2.6);
            };
            double frac = switch (layer) {
                case 0 -> range(r, 0.0, 0.32);
                case 1 -> range(r, 0.30, 0.68);
                default -> 1.0;
            };
            double growth = layer == 2 ? 1.0 : MAX_GROWTH, spread = layer == 2 ? 0.0 : LATE_SPREAD;
            double limit = (radius - EDGE * growth * size) / (1.0 + spread);
            // The outer front sits at (or a little inside) its limit: a ragged, not spherical, silhouette.
            double distance = layer == 2 ? limit * range(r, 0.88, 1.0) : Math.min(frac * radius, limit);
            double clear = obstruction.clearDistance(centre, dir, radius);
            distance = Math.max(0.0, Math.min(distance, clear - EDGE * size));
            double heatBias = switch (layer) {
                case 0 -> range(r, 1.00, 1.10);
                case 1 -> range(r, 0.85, 0.95);
                default -> range(r, 0.72, 0.82);
            };
            double spin = (r.nextBoolean() ? 1 : -1) * range(r, 0.5, 1.6);
            double swirl = layer == 2 ? range(r, -0.25, 0.25) : 0.0;
            double tau = style.expandSeconds() * (layer == 0 ? 0.35 : layer == 1 ? 0.22 : 0.15) * range(r, 0.8, 1.2);
            double delay = layer == 2 ? range(r, 0.0, 0.01) : range(r, 0.0, 0.03);
            e.bodies.add(new Body(dir, distance, size, delay, range(r, style.minLife(), style.maxLife()),
                    heatBias, spin, range(r, 0.0, Math.PI * 2), swirl, tau, (float) r.nextDouble(), layer));
        }

        for (int i = 0; i < style.tongues(); i++) {
            Vec3 dir = outward(r, normal);
            double clear = obstruction.clearDistance(centre, dir, radius);
            double max = Math.min(radius * range(r, 0.80, 0.97), clear - 0.3) - 0.4;
            if (max < 0.8) continue;
            e.tongues.add(new Tongue(dir, max, scale * range(r, 0.7, 1.1), range(r, 0.0, 0.02), (float) r.nextDouble()));
        }

        if (reached) {
            double ring = Math.sqrt(radius * radius - Math.max(h, 0.0) * Math.max(h, 0.0));
            Vec3 foot = new Vec3(centre.x, groundY + 0.5, centre.z);
            for (int i = 0; i < style.groundLicks(); i++) {
                boolean outer = i < style.groundLicks() * 0.72;
                double a = outer ? (i + range(r, -0.35, 0.35)) / (style.groundLicks() * 0.72) * Math.PI * 2
                        : range(r, 0.0, Math.PI * 2);
                double cos = Math.cos(a), sin = Math.sin(a);
                double dist = ring * (outer ? range(r, 0.84, 0.96) : range(r, 0.25, 0.7));
                dist = Math.min(dist, obstruction.clearDistance(foot, new Vec3(cos, 0, sin), dist + 1.0) - 0.4);
                if (dist < 0.3) continue;
                double gy = ground.groundY(centre.x + cos * dist, groundY + 1.5, centre.z + sin * dist, 3);
                if (Double.isNaN(gy)) continue;
                double delay = 0.01 + Math.max(h, 0.0) / radius * 0.10 + range(r, 0.0, 0.02);
                // The lick's tip stays inside the sphere.
                double height = Math.min(scale * range(r, 1.1, 1.9), centre.y - gy + Math.sqrt(Math.max(radius * radius - dist * dist, 0.0)));
                if (height < 0.3) continue;
                e.licks.add(new Lick(cos, sin, dist, gy, scale * range(r, 1.5, 2.2), height, delay,
                        range(r, 0.7, 0.95), (float) r.nextDouble()));
            }
        }
        return e;
    }

    private static double range(SplittableRandom r, double lo, double hi) {
        return lo + (hi - lo) * r.nextDouble();
    }

    /** A random unit direction, mirrored out of the hit surface when there is one. */
    private static Vec3 outward(SplittableRandom r, @Nullable Vec3 normal) {
        double z = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
        Vec3 d = new Vec3(s * Math.cos(a), z, s * Math.sin(a));
        if (normal != null) {
            double dn = d.dot(normal);
            if (dn < 0) d = d.subtract(normal.scale(2 * dn));
        }
        return d;
    }

    /**
     * Even directions over the sphere, or over the hemisphere outside the hit surface: a Fibonacci lattice with jitter,
     * turned by a random angle per explosion, so neighbouring volumes of a layer overlap without clumps or gaps.
     */
    private record Basis(Vec3 n, Vec3 t1, Vec3 t2, boolean hemisphere, double turn) {
        static Basis of(@Nullable Vec3 normal, SplittableRandom r) {
            Vec3 n = normal != null ? normal.normalize() : new Vec3(0, 1, 0);
            Vec3 t1 = n.cross(Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
            return new Basis(n, t1, n.cross(t1), normal != null, r.nextDouble() * Math.PI * 2);
        }

        Vec3 lattice(SplittableRandom r, int i, int count) {
            double u = Math.clamp((i + 0.5 + range(r, -0.3, 0.3)) / count, 0.0, 1.0);
            double z = hemisphere ? u : 1.0 - 2.0 * u, s = Math.sqrt(Math.max(0.0, 1.0 - z * z));
            double a = turn + i * GOLDEN_ANGLE + range(r, -0.25, 0.25);
            return n.scale(z).add(t1.scale(s * Math.cos(a))).add(t2.scale(s * Math.sin(a)));
        }
    }

    public Vec3 centre() {
        return centre;
    }

    public double radius() {
        return radius;
    }

    public boolean groundReached() {
        return groundReached;
    }

    public double age(double now) {
        return now - start;
    }

    public boolean finished(double now) {
        return age(now) > style.totalSeconds();
    }

    public FireExplosionStyle style() {
        return style;
    }

    public int bodyCount() {
        return bodies.size();
    }

    public int tongueCount() {
        return tongues.size();
    }

    public int lickCount() {
        return licks.size();
    }

    private static double easeOut3(double x) {
        double c = 1.0 - Math.min(Math.max(x, 0.0), 1.0);
        return 1.0 - c * c * c;
    }

    private static double smooth(double e0, double e1, double x) {
        double t = Math.min(Math.max((x - e0) / (e1 - e0), 0.0), 1.0);
        return t * t * (3 - 2 * t);
    }

    /** Appends this frame's live elements (pooled from {@code pool} when possible); returns how many were added. */
    public int evaluate(double now, List<Element> out, ElementPool pool) {
        double age = age(now);
        if (age < 0) return 0;
        int added = 0;
        double scale = radius / 6.0;

        if (age < 0.12) {
            Element c = pool.next(this, Kind.CORE);
            set(c, centre, 0, 0, 0);
            c.width = scale * (1.0 + 3.0 * (1.0 - Math.exp(-age / 0.03)));
            c.heat = 1.25f;
            c.opacity = (float) (1.0 - smooth(0.02, 0.11, age));
            c.erosion = 0;
            c.seed = 0.5f;
            out.add(c);
            added++;
        }

        for (Body b : bodies) {
            double a = age - b.delay;
            if (a <= 0) continue;
            double tl = a / b.life;
            if (tl >= FADE_END) continue;
            // Blast: the speed decays exponentially (fast flame front, then turbulent burning in place).
            double e = 1.0 - Math.exp(-a / b.tau);
            double slow = Math.min(Math.max((a - style.expandSeconds()) / 0.9, 0.0), 1.0);
            double dist = b.distance * (0.06 + 0.94 * e) * (1.0 + b.lateSpread() * slow);
            Vec3 d = b.dir;
            if (b.swirl != 0.0) {
                double t = b.swirl * slow, c = Math.cos(t), s = Math.sin(t);
                d = new Vec3(d.x * c - d.z * s, d.y, d.x * s + d.z * c);
            }
            double heat = b.heat(a, style);
            // Only cooled soot drifts up (and may leave the sphere: cosmetic); hot fire stays inside the radius.
            double rise = style.buoyancy() * Math.pow(Math.max(0.0, a - 0.35), 2) * Math.clamp((0.3 - heat) / 0.1, 0.0, 1.0);
            Element el = pool.next(this, Kind.BODY);
            el.x = centre.x + d.x * dist;
            el.y = centre.y + d.y * dist + rise;
            el.z = centre.z + d.z * dist;
            el.width = b.size * (0.35 + 0.65 * e) * (1.0 + (b.maxGrowth() - 1.0) * slow);
            el.ax = d.x;
            el.ay = d.y;
            el.az = d.z;
            el.stretch = (float) (1.0 + STRETCH[b.layer] * (1.0 - e));
            el.heat = (float) heat;
            el.erosion = (float) (BASE_EROSION[b.layer] + (0.8 - BASE_EROSION[b.layer]) * smooth(0.35, FADE_END, tl));
            el.opacity = (float) (Math.min(1.0, a / 0.012) * (1.0 - smooth(0.88, FADE_END, tl)));
            el.angle = (float) (b.angle0 + b.spin * a);
            el.seed = b.seed;
            if (b.distance < 0.5) el.stretch = 1;
            out.add(el);
            added++;
        }

        for (Tongue t : tongues) {
            double a = age - t.delay;
            if (a <= 0 || a >= 0.75) continue;
            double et = 1.0 - Math.pow(1.0 - Math.min(a / 0.12, 1.0), 2);
            double base = 0.4, length = t.maxLength * et;
            if (a > 0.35) {
                double k = (a - 0.35) / 0.4;
                base = 0.4 + t.maxLength * 0.6 * k;
                length *= 1.0 - k;
            }
            Element el = pool.next(this, Kind.TONGUE);
            set(el, centre.add(t.dir.scale(base)), t.dir.x, t.dir.y, t.dir.z);
            el.length = length;
            el.width = t.width * (0.6 + 0.4 * et);
            el.heat = (float) (1.05 * Math.exp(-a / 0.35));
            el.erosion = (float) smooth(0.3, 0.75, a);
            el.opacity = (float) (Math.min(1.0, a / 0.03) * (1.0 - smooth(0.6, 0.75, a)));
            el.seed = t.seed;
            out.add(el);
            added++;
        }

        for (Lick l : licks) {
            double a = age - l.delay;
            if (a <= 0) continue;
            double tl = a / l.life;
            if (tl >= 1.05) continue;
            double eg = easeOut3(a / 0.18);
            double r = l.distance * (0.15 + 0.85 * eg);
            Element el = pool.next(this, Kind.GROUND);
            set(el, new Vec3(centre.x + l.cos * r, l.groundY, centre.z + l.sin * r), 0, 1, 0);
            el.width = l.width;
            el.length = l.height * (0.5 + 0.5 * eg) * (1.0 - 0.5 * smooth(0.5, 1.0, tl));
            el.heat = (float) (0.98 * Math.exp(-a / 0.38));
            el.erosion = (float) smooth(0.4, 1.0, tl);
            el.opacity = (float) (Math.min(1.0, a / 0.05) * (1.0 - smooth(0.75, 1.05, tl)));
            el.seed = l.seed;
            out.add(el);
            added++;
        }
        return added;
    }

    /** Elements below this heat do not glow. */
    static final float GLOW_MIN_HEAT = 0.55f;
    /**
     * Summed {@link #glowWeight} of one explosion at its brightest (measured from the model by
     * {@code FireExplosionTest.oneExplosionsGlowDemandPeaksAtOne}); an explosion's emissive budget demand is its sum over
     * this, at most 1.
     */
    public static final float GLOW_PEAK = 58.0f;

    /** How strongly an element glows (before the style's emissive factor), from its kind, heat and opacity. */
    public static float glowWeight(Element el) {
        if (el.kind == Kind.GROUND) return 0.0f;
        if (el.kind == Kind.CORE) return el.opacity * 2.5f;
        if (el.heat < GLOW_MIN_HEAT) return 0.0f;
        return (float) Math.min(1.0, (el.heat - GLOW_MIN_HEAT) / 0.6) * el.opacity;
    }

    private static void set(Element el, Vec3 p, double ax, double ay, double az) {
        el.x = p.x;
        el.y = p.y;
        el.z = p.z;
        el.ax = ax;
        el.ay = ay;
        el.az = az;
        el.angle = 0;
    }

    /** Reuses {@link Element} objects between frames (one pool per renderer). */
    public static final class ElementPool {
        private final List<Element> elements = new ArrayList<>();
        private int used;

        public void reset() {
            used = 0;
        }

        Element next(FireExplosion owner, Kind kind) {
            if (used == elements.size()) elements.add(new Element());
            Element e = elements.get(used++);
            e.owner = owner;
            e.kind = kind;
            e.length = 0;
            e.stretch = 1;
            return e;
        }
    }
}

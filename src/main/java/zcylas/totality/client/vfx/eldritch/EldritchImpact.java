package zcylas.totality.client.vfx.eldritch;

import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * One Eldritch Blast impact (pure, deterministic per seed, unit-tested): a concentrated white-hot flash with sharp spikes
 * and a faint secondary ring ({@link #LIFE}), and a fast spray of needle sparks thrown off the surface (around its normal), slowed by drag and
 * pulled down by gravity. A fizzle (the bolt expired in the air) has no flash and no sparks.
 */
final class EldritchImpact {

    static final double LIFE = 0.26;
    static final int SPARKS = 24;
    static final double DRAG = 3.0;
    static final double GRAVITY = 10.0;

    record Spark(Vec3 velocity, double life) {}

    final Vec3 at;
    final Vec3 normal;
    final double start;
    final float seed;
    final boolean fizzle;
    final Spark[] sparks;

    EldritchImpact(Vec3 at, Vec3 normal, double start, long seed, boolean fizzle) {
        this.at = at;
        this.normal = normal.lengthSqr() < 1.0e-8 ? new Vec3(0, 1, 0) : normal.normalize();
        this.start = start;
        this.seed = (float) ((seed * 0.6180339887) % 1.0 + 1.0) % 1.0f;
        this.fizzle = fizzle;
        Random r = new Random(seed);
        this.sparks = new Spark[fizzle ? 0 : SPARKS];
        for (int i = 0; i < sparks.length; i++) {
            Vec3 u = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
            if (u.lengthSqr() < 1.0e-6) u = new Vec3(0, 1, 0);
            Vec3 d = this.normal.scale(1.1).add(u.normalize().scale(0.9)).normalize();
            sparks[i] = new Spark(d.scale(8.0 + r.nextDouble() * 10.0), 0.18 + r.nextDouble() * 0.24);
        }
    }

    /** Where a spark is {@code t} seconds after the impact. */
    Vec3 sparkPos(Spark s, double t) {
        double travel = (1.0 - Math.exp(-DRAG * t)) / DRAG;
        return at.add(s.velocity().scale(travel)).add(0, -0.5 * GRAVITY * t * t, 0);
    }

    /** Flash/ring progress 0..1 at {@code now} (above 1 once they are over). */
    double progress(double now) {
        return (now - start) / LIFE;
    }

    double duration() {
        double d = fizzle ? 0.0 : LIFE;
        for (Spark s : sparks) d = Math.max(d, s.life());
        return d;
    }

    boolean finished(double now) {
        return now - start >= duration();
    }
}

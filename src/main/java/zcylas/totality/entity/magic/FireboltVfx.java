package zcylas.totality.entity.magic;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.init.ModParticles;

/**
 * Firebolt's particle recipes (after Context/References/Other/firebolt_reference.png): the cast burst, the travel
 * trail, the impact burst and its afterglow, and the fizzle when a bolt expires in the air. Only ever called on the
 * client (from the bolt's client tick and the impact emitter); it only adds particles, so it is safe in common code.
 */
public final class FireboltVfx {

    private FireboltVfx() {}

    /**
     * Released where the bolt appears (1.5 blocks in front of the caster's eyes): a small flash and a short fan of
     * flames and sparks leaning forward and up. Everything moves away from the caster, so first person stays clear.
     */
    public static void castBurst(Level level, Vec3 at, Vec3 dir) {
        RandomSource r = level.getRandom();
        level.addParticle(ModParticles.FIREBOLT_FLASH, at.x, at.y, at.z, 0.45, 0, 0);
        for (int i = 0; i < 7; i++) {
            Vec3 v = cone(r, dir.add(0, 0.9, 0).normalize(), 0.6).scale(0.14 + r.nextDouble() * 0.14);
            level.addParticle(ModParticles.FIREBOLT_SPARK, at.x, at.y, at.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 4; i++) {
            Vec3 p = at.add(dir.scale(0.2)).add(jitter(r, 0.15));
            Vec3 v = dir.scale(0.06).add(jitter(r, 0.02)).add(0, 0.05, 0);
            level.addParticle(ModParticles.FIREBOLT_WISP, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 5; i++) {
            Vec3 v = dir.scale(0.07).add(jitter(r, 0.05)).add(0, 0.04, 0);
            level.addParticle(ModParticles.FIREBOLT_EMBER, at.x, at.y, at.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 3; i++) {
            Vec3 v = cone(r, dir, 0.25).scale(0.35);
            level.addParticle(ModParticles.FIREBOLT_STREAK, at.x, at.y, at.z, v.x, v.y, v.z);
        }
    }

    /**
     * One tick of travel from {@code from} to {@code to} (the bolt moves 2.5 blocks a tick, so the trail is spread
     * along the whole segment): embers and wisps shed behind the core, a spark, and streaks along the path.
     */
    public static void trail(Level level, Vec3 from, Vec3 to, Vec3 dir) {
        RandomSource r = level.getRandom();
        double len = from.distanceTo(to);
        int embers = 2 + (int) Math.ceil(len * 1.6), wisps = 1 + (int) Math.ceil(len * 0.8);
        for (int i = 0; i < embers; i++) {
            Vec3 p = lerp(from, to, r.nextDouble()).add(jitter(r, 0.22));
            Vec3 v = dir.scale(-0.05 - r.nextDouble() * 0.05).add(jitter(r, 0.025));
            level.addParticle(ModParticles.FIREBOLT_EMBER, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < wisps; i++) {
            Vec3 p = lerp(from, to, r.nextDouble()).add(jitter(r, 0.12));
            Vec3 v = dir.scale(-0.06).add(jitter(r, 0.02)).add(0, 0.02, 0);
            level.addParticle(ModParticles.FIREBOLT_WISP, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        if (r.nextFloat() < 0.8F) {
            Vec3 p = lerp(from, to, r.nextDouble());
            Vec3 v = cone(r, dir.scale(-1), 1.1).scale(0.12 + r.nextDouble() * 0.12);
            level.addParticle(ModParticles.FIREBOLT_SPARK, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 2; i++) {
            // motion lines left along the path, bright heads pointing the way the bolt went
            Vec3 p = lerp(from, to, r.nextDouble()).add(jitter(r, 0.2));
            Vec3 v = dir.scale(0.15 + r.nextDouble() * 0.15);
            level.addParticle(ModParticles.FIREBOLT_STREAK, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    /** The hit: a big flash, sparks and streaks flung out around the surface normal, flame wisps and embers. */
    public static void impactBurst(Level level, Vec3 at, Vec3 normal) {
        RandomSource r = level.getRandom();
        Vec3 p0 = at.add(normal.scale(0.12));
        level.addParticle(ModParticles.FIREBOLT_FLASH, p0.x, p0.y, p0.z, 1.7, 0, 0);
        for (int i = 0; i < 18; i++) {
            Vec3 v = cone(r, normal, 1.25).scale(0.22 + r.nextDouble() * 0.3);
            level.addParticle(ModParticles.FIREBOLT_SPARK, p0.x, p0.y, p0.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 8; i++) {
            Vec3 v = cone(r, normal, 1.2).scale(0.35 + r.nextDouble() * 0.25);
            level.addParticle(ModParticles.FIREBOLT_STREAK, p0.x, p0.y, p0.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 12; i++) {
            Vec3 p = p0.add(jitter(r, 0.35));
            Vec3 v = cone(r, normal, 1.3).scale(0.05 + r.nextDouble() * 0.08).add(0, 0.03, 0);
            level.addParticle(ModParticles.FIREBOLT_WISP, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 22; i++) {
            Vec3 v = cone(r, normal, 1.4).scale(0.05 + r.nextDouble() * 0.2);
            level.addParticle(ModParticles.FIREBOLT_EMBER, p0.x, p0.y, p0.z, v.x, v.y, v.z);
        }
    }

    /** A few more cooling embers and wisps rising from the hit for three ticks. */
    public static void afterglow(Level level, Vec3 at, Vec3 normal) {
        RandomSource r = level.getRandom();
        for (int i = 0; i < 3; i++) {
            Vec3 p = at.add(normal.scale(0.2)).add(jitter(r, 0.35));
            Vec3 v = jitter(r, 0.03).add(0, 0.04, 0);
            level.addParticle(i == 0 ? ModParticles.FIREBOLT_WISP : ModParticles.FIREBOLT_EMBER, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    /** An expired bolt gutters out in the air: a small flash and a few embers and wisps. */
    public static void fizzle(Level level, Vec3 at) {
        RandomSource r = level.getRandom();
        level.addParticle(ModParticles.FIREBOLT_FLASH, at.x, at.y, at.z, 0.6, 0, 0);
        for (int i = 0; i < 6; i++) {
            Vec3 v = jitter(r, 0.06).add(0, 0.03, 0);
            level.addParticle(i < 2 ? ModParticles.FIREBOLT_WISP : ModParticles.FIREBOLT_EMBER, at.x, at.y, at.z, v.x, v.y, v.z);
        }
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }

    private static Vec3 jitter(RandomSource r, double s) {
        return new Vec3((r.nextDouble() - 0.5) * 2 * s, (r.nextDouble() - 0.5) * 2 * s, (r.nextDouble() - 0.5) * 2 * s);
    }

    /** A random unit vector within {@code spread} radians of {@code axis}. */
    private static Vec3 cone(RandomSource r, Vec3 axis, double spread) {
        Vec3 a = axis.lengthSqr() < 1.0E-6 ? new Vec3(0, 1, 0) : axis.normalize();
        Vec3 ref = Math.abs(a.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = a.cross(ref).normalize(), w = a.cross(u);
        double theta = r.nextDouble() * spread, phi = r.nextDouble() * Math.PI * 2;
        return a.scale(Math.cos(theta)).add(u.scale(Math.sin(theta) * Math.cos(phi))).add(w.scale(Math.sin(theta) * Math.sin(phi)));
    }
}

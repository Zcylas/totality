// api/client/particles/TotalityParticles.java
package zcylas.totality.api.client.particles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Utility class for spawning particles in complex patterns.
 * Inspired by owo-lib's ClientParticles.
 *
 * Usage:
 *   TotalityParticles.setCount(10);
 *   TotalityParticles.setVelocity(new Vec3(0, 0.1, 0));
 *   TotalityParticles.spawn(ParticleTypes.FLAME, level, pos, 0.5);
 *
 * All state (count, velocity) resets after each spawn call
 * unless persist() is called first.
 */
public final class TotalityParticles {

    private static int count = 1;
    private static boolean persist = false;

    private static Vec3 velocity = Vec3.ZERO;
    private static boolean randomizeVelocity = false;
    private static double randomVelocityScalar = 0;

    private TotalityParticles() {}

    // ── State control ─────────────────────────────────────────────────────────

    /** Marks state as persistent — won't reset after next spawn call. */
    public static void persist() {
        persist = true;
    }

    /** Number of particles to spawn per operation. Volatile unless persist() called. */
    public static void setCount(int count) {
        TotalityParticles.count = count;
    }

    /** Velocity applied to each spawned particle. Volatile unless persist() called. */
    public static void setVelocity(Vec3 velocity) {
        TotalityParticles.velocity = velocity;
        randomizeVelocity = false;
    }

    /** Use a random velocity per particle scaled by {@code scalar}. Volatile unless persist() called. */
    public static void randomizeVelocity(double scalar) {
        randomizeVelocity = true;
        randomVelocityScalar = scalar;
    }

    /** Force reset all state. */
    public static void reset() {
        persist = false;
        clearState();
    }

    // ── Spawn methods ─────────────────────────────────────────────────────────

    /**
     * Spawns particles at {@code pos} with a random offset up to {@code deviation}.
     */
    public static void spawn(ParticleOptions particle, Level level, Vec3 pos, double deviation) {
        for (int i = 0; i < count; i++) {
            addParticle(particle, level, randomOffset(level.getRandom(), pos, deviation));
        }
        clearState();
    }

    /**
     * Spawns particles centered on a block with a random offset up to {@code deviation}.
     */
    public static void spawnCenteredOnBlock(ParticleOptions particle, Level level, BlockPos pos, double deviation) {
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        spawn(particle, level, center, deviation);
    }

    /**
     * Spawns particles along a line from {@code start} to {@code end}.
     */
    public static void spawnLine(ParticleOptions particle, Level level, Vec3 start, Vec3 end, double deviation) {
        spawnLineInner(particle, level, start, end, deviation);
        clearState();
    }

    /**
     * Spawns a ring of particles in the XZ plane at {@code center} with given {@code radius}.
     * Useful for Ground Slam impact rings, AOE indicators etc.
     */
    public static void spawnRing(ParticleOptions particle, Level level, Vec3 center, double radius, double deviation) {
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i) / count;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            Vec3 pos = randomOffset(level.getRandom(), new Vec3(x, center.y, z), deviation);
            addParticle(particle, level, pos);
        }
        clearState();
    }

    /**
     * Spawns particles in a sphere around {@code center} with given {@code radius}.
     * Useful for explosion impacts, magic bursts.
     */
    public static void spawnSphere(ParticleOptions particle, Level level, Vec3 center, double radius, double deviation) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = random.nextDouble() * Math.PI;
            double x = center.x + radius * Math.sin(phi) * Math.cos(theta);
            double y = center.y + radius * Math.cos(phi);
            double z = center.z + radius * Math.sin(phi) * Math.sin(theta);
            addParticle(particle, level, randomOffset(random, new Vec3(x, y, z), deviation));
        }
        clearState();
    }

    /**
     * Spawns particles in a cone pointing in {@code direction} from {@code origin}.
     * Useful for Heat Vision beam impact, breath attacks.
     *
     * @param spreadAngle angle in radians — 0 = tight beam, PI/4 = wide cone
     */
    public static void spawnCone(ParticleOptions particle, Level level, Vec3 origin,
                                 Vec3 direction, double length, double spreadAngle, double deviation) {
        RandomSource random = level.getRandom();
        Vec3 normalized = direction.normalize();

        for (int i = 0; i < count; i++) {
            double dist = random.nextDouble() * length;
            double spread = random.nextDouble() * spreadAngle;
            double spreadAngleRandom = random.nextDouble() * 2 * Math.PI;

            // Perpendicular offset
            Vec3 perp = getPerpendicular(normalized);
            Vec3 perp2 = normalized.cross(perp).normalize();

            double offsetX = Math.cos(spreadAngleRandom) * Math.tan(spread) * dist;
            double offsetY = Math.sin(spreadAngleRandom) * Math.tan(spread) * dist;

            Vec3 pos = origin
                    .add(normalized.scale(dist))
                    .add(perp.scale(offsetX))
                    .add(perp2.scale(offsetY));

            addParticle(particle, level, randomOffset(random, pos, deviation));
        }
        clearState();
    }

    /**
     * Spawns a cube outline with particles.
     * Useful for block-selection effects, AOE indicators.
     */
    public static void spawnCubeOutline(ParticleOptions particle, Level level, Vec3 origin, float size, double deviation) {
        spawnLineInner(particle, level, origin, origin.add(size, 0, 0), deviation);
        spawnLineInner(particle, level, origin.add(size, 0, 0), origin.add(size, 0, size), deviation);
        spawnLineInner(particle, level, origin, origin.add(0, 0, size), deviation);
        spawnLineInner(particle, level, origin.add(0, 0, size), origin.add(size, 0, size), deviation);

        Vec3 top = origin.add(0, size, 0);
        spawnLineInner(particle, level, top, top.add(size, 0, 0), deviation);
        spawnLineInner(particle, level, top.add(size, 0, 0), top.add(size, 0, size), deviation);
        spawnLineInner(particle, level, top, top.add(0, 0, size), deviation);
        spawnLineInner(particle, level, top.add(0, 0, size), top.add(size, 0, size), deviation);

        spawnLineInner(particle, level, origin, top, deviation);
        spawnLineInner(particle, level, origin.add(size, 0, 0), top.add(size, 0, 0), deviation);
        spawnLineInner(particle, level, origin.add(0, 0, size), top.add(0, 0, size), deviation);
        spawnLineInner(particle, level, origin.add(size, 0, size), top.add(size, 0, size), deviation);

        clearState();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static void addParticle(ParticleOptions particle, Level level, Vec3 pos) {
        Vec3 vel = velocity;
        if (randomizeVelocity) {
            RandomSource r = level.getRandom();
            vel = new Vec3(
                    (r.nextDouble() - 0.5) * randomVelocityScalar,
                    (r.nextDouble() - 0.5) * randomVelocityScalar,
                    (r.nextDouble() - 0.5) * randomVelocityScalar
            );
        }

        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // Server — broadcast to nearby clients
            serverLevel.sendParticles(particle,
                    pos.x, pos.y, pos.z,
                    1,
                    vel.x, vel.y, vel.z,
                    0.0);
        } else {
            // Client — add directly
            level.addParticle(particle, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
        }
    }



    private static void spawnLineInner(ParticleOptions particle, Level level, Vec3 start, Vec3 end, double deviation) {
        Vec3 step = end.subtract(start).scale(1.0 / count);
        Vec3 current = start;
        for (int i = 0; i < count; i++) {
            addParticle(particle, level, randomOffset(level.getRandom(), current, deviation));
            current = current.add(step);
        }
    }

    private static Vec3 randomOffset(RandomSource random, Vec3 center, double deviation) {
        if (deviation == 0) return center;
        return new Vec3(
                center.x + (random.nextDouble() - 0.5) * deviation,
                center.y + (random.nextDouble() - 0.5) * deviation,
                center.z + (random.nextDouble() - 0.5) * deviation
        );
    }

    private static Vec3 getPerpendicular(Vec3 v) {
        Vec3 arbitrary = Math.abs(v.x) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return v.cross(arbitrary).normalize();
    }

    private static void clearState() {
        if (persist) return;
        count = 1;
        velocity = Vec3.ZERO;
        randomizeVelocity = false;
        randomVelocityScalar = 0;
        persist = false;
    }
}
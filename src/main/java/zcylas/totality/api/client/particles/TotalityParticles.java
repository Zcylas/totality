// api/client/particles/TotalityParticles.java
package zcylas.totality.api.client.particles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.particles.TotalityVectorUtils;

/**
 * Utility class for spawning particles in complex patterns.
 * Inspired by owo-lib's ClientParticles.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   TotalityParticles.setCount(40);
 *   TotalityParticles.setForced(true);
 *   TotalityParticles.spawnRing(ParticleTypes.CRIT, level, pos, 2.0, 0.05);
 * }</pre>
 *
 * All state resets after each spawn call unless {@link #persist()} is called first.
 * Call {@link #reset()} to force-clear state at any time.
 *
 * <h3>Server vs client</h3>
 * All methods accept {@link Level} and dispatch correctly on both sides.
 * {@link #setForced(boolean)} only has an effect on the server — it extends
 * particle visibility from 32 to 512 blocks and overrides client particle
 * settings.
 */
public final class TotalityParticles {

    // ── Static state ──────────────────────────────────────────────────────────

    private static int     count               = 1;
    private static boolean persist             = false;
    private static boolean forced              = false;

    private static Vec3    velocity            = Vec3.ZERO;
    private static boolean randomizeVelocity   = false;
    private static double  randomVelocityScalar = 0;

    private TotalityParticles() {}

    // ── State control ─────────────────────────────────────────────────────────

    /** Marks state as persistent — won't reset after the next spawn call. */
    public static void persist() {
        persist = true;
    }

    /**
     * Number of particles spawned per operation.
     * For lines and outlines this controls segment density.
     * Volatile unless {@link #persist()} was called.
     */
    public static void setCount(int count) {
        TotalityParticles.count = count;
    }

    /**
     * Constant velocity applied to every spawned particle.
     * Clears any active {@link #randomizeVelocity} setting.
     * Volatile unless {@link #persist()} was called.
     */
    public static void setVelocity(Vec3 velocity) {
        TotalityParticles.velocity = velocity;
        randomizeVelocity = false;
    }

    /**
     * Assigns a random velocity to each particle, scaled by {@code scalar}.
     * Overrides any constant velocity set via {@link #setVelocity}.
     * Volatile unless {@link #persist()} was called.
     */
    public static void randomizeVelocity(double scalar) {
        randomizeVelocity = true;
        randomVelocityScalar = scalar;
    }

    /**
     * If {@code true}, particles are sent with {@code overrideLimiter} and
     * {@code alwaysShow} flags on the server, extending visibility from 32
     * to 512 blocks and bypassing the client's particle reduction setting.
     * No effect when called from the client side.
     * Volatile unless {@link #persist()} was called.
     */
    public static void setForced(boolean forced) {
        TotalityParticles.forced = forced;
    }

    /** Force-resets all state regardless of {@link #persist()}. */
    public static void reset() {
        persist = false;
        clearState();
    }

    // ── Spawn methods ─────────────────────────────────────────────────────────

    /**
     * Spawns {@link #count} particles at {@code pos} with a random offset
     * up to {@code deviation} in each axis.
     */
    public static void spawn(ParticleOptions particle, Level level, Vec3 pos, double deviation) {
        for (int i = 0; i < count; i++) {
            addParticle(particle, level, randomOffset(level.getRandom(), pos, deviation));
        }
        clearState();
    }

    /**
     * Spawns particles centered on a block (0.5 offset on each axis).
     */
    public static void spawnCenteredOnBlock(ParticleOptions particle, Level level, BlockPos pos, double deviation) {
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        spawn(particle, level, center, deviation);
    }

    /**
     * Spawns {@link #count} particles evenly along a line from {@code start}
     * to {@code end}. The endpoint is not included (half-open range).
     */
    public static void spawnLine(ParticleOptions particle, Level level, Vec3 start, Vec3 end, double deviation) {
        spawnLineInner(particle, level, start, end, deviation);
        clearState();
    }

    /**
     * Spawns a flat ring of particles in the XZ plane at {@code center}.
     * Good for Ground Slam impact rings, AOE floor indicators.
     */
    public static void spawnRing(ParticleOptions particle, Level level, Vec3 center,
                                 double radius, double deviation) {
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i) / count;
            Vec3 pos = randomOffset(level.getRandom(),
                    new Vec3(center.x + Math.cos(angle) * radius,
                            center.y,
                            center.z + Math.sin(angle) * radius),
                    deviation);
            addParticle(particle, level, pos);
        }
        clearState();
    }

    /**
     * Spawns a ring of particles whose plane normal is oriented by {@code yaw}
     * and {@code pitch} (degrees). At {@code yaw=0, pitch=0} the ring is flat
     * in the XZ plane, identical to the no-orientation overload.
     * Pass {@code entity.getYaw()} / {@code entity.getPitch()} to align the
     * ring with a look direction.
     */
    public static void spawnRing(ParticleOptions particle, Level level, Vec3 center,
                                 double radius, double deviation, float yaw, float pitch) {
        for (int i = 0; i < count; i++) {
            double angle = (2 * Math.PI * i) / count;
            Vec3 v = new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            v = TotalityVectorUtils.rotateByYawPitch(v, yaw, pitch);
            addParticle(particle, level, randomOffset(level.getRandom(), center.add(v), deviation));
        }
        clearState();
    }

    /**
     * Spawns {@link #count} particles randomly distributed across the surface
     * of a sphere with the given {@code radius}.
     * Uses uniform surface distribution (no pole clustering).
     */
    public static void spawnSphere(ParticleOptions particle, Level level, Vec3 center,
                                   double radius, double deviation) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;
            // acos(1 - 2u) gives uniform distribution over the sphere surface
            double phi   = Math.acos(1.0 - 2.0 * random.nextDouble());
            double x = center.x + radius * Math.sin(phi) * Math.cos(theta);
            double y = center.y + radius * Math.cos(phi);
            double z = center.z + radius * Math.sin(phi) * Math.sin(theta);
            addParticle(particle, level, randomOffset(random, new Vec3(x, y, z), deviation));
        }
        clearState();
    }

    /**
     * Spawns {@link #count} particles randomly distributed across a filled
     * horizontal disc (XZ plane) at {@code center}.
     * Uses uniform area distribution (no center clustering).
     * Good for ground-impact dust clouds, ritual activation bursts.
     */
    public static void spawnDisk(ParticleOptions particle, Level level, Vec3 center,
                                 double radius, double deviation) {
        RandomSource random = level.getRandom();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            // sqrt for uniform area distribution — avoids centre clustering
            double r = radius * Math.sqrt(random.nextDouble());
            Vec3 pos = randomOffset(random,
                    new Vec3(center.x + Math.cos(angle) * r,
                            center.y,
                            center.z + Math.sin(angle) * r),
                    deviation);
            addParticle(particle, level, pos);
        }
        clearState();
    }

    /**
     * Spawns particles in a cone pointing in {@code direction} from {@code origin}.
     *
     * @param spreadAngle spread half-angle in radians — 0 = tight beam, PI/4 = wide cone
     */
    public static void spawnCone(ParticleOptions particle, Level level, Vec3 origin,
                                 Vec3 direction, double length, double spreadAngle, double deviation) {
        RandomSource random = level.getRandom();
        Vec3 normalized = direction.normalize();

        // Orthonormal frame — computed once, not per-particle
        Vec3 perp  = getPerpendicular(normalized);
        Vec3 perp2 = normalized.cross(perp).normalize();

        for (int i = 0; i < count; i++) {
            double dist             = random.nextDouble() * length;
            double spread           = random.nextDouble() * spreadAngle;
            double spreadAngleRandom = random.nextDouble() * 2 * Math.PI;

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
     * Spawns a box outline with particles. {@code origin} is the
     * <b>corner</b> of the box — use {@link #spawnCubeOutlineCentered} if
     * you want to pass the center instead.
     */
    public static void spawnCubeOutline(ParticleOptions particle, Level level,
                                        Vec3 origin, float size, double deviation) {
        // Bottom face
        spawnLineInner(particle, level, origin,                      origin.add(size, 0, 0),    deviation);
        spawnLineInner(particle, level, origin.add(size, 0, 0),     origin.add(size, 0, size), deviation);
        spawnLineInner(particle, level, origin,                      origin.add(0, 0, size),    deviation);
        spawnLineInner(particle, level, origin.add(0, 0, size),     origin.add(size, 0, size), deviation);

        // Top face
        Vec3 top = origin.add(0, size, 0);
        spawnLineInner(particle, level, top,                        top.add(size, 0, 0),       deviation);
        spawnLineInner(particle, level, top.add(size, 0, 0),       top.add(size, 0, size),    deviation);
        spawnLineInner(particle, level, top,                        top.add(0, 0, size),       deviation);
        spawnLineInner(particle, level, top.add(0, 0, size),       top.add(size, 0, size),    deviation);

        // Vertical edges
        spawnLineInner(particle, level, origin,                     top,                       deviation);
        spawnLineInner(particle, level, origin.add(size, 0, 0),    top.add(size, 0, 0),       deviation);
        spawnLineInner(particle, level, origin.add(0, 0, size),    top.add(0, 0, size),       deviation);
        spawnLineInner(particle, level, origin.add(size, 0, size), top.add(size, 0, size),    deviation);

        clearState();
    }

    /**
     * Spawns a box outline centered on {@code center}.
     * Equivalent to {@link #spawnCubeOutline} with the corner shifted by
     * {@code -size/2} on each axis.
     */
    public static void spawnCubeOutlineCentered(ParticleOptions particle, Level level,
                                                Vec3 center, float size, double deviation) {
        Vec3 corner = center.subtract(size / 2.0, size / 2.0, size / 2.0);
        spawnCubeOutline(particle, level, corner, size, deviation);
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

        if (level instanceof ServerLevel serverLevel) {
            if (forced) {
                // overrideLimiter=true → 512 block range; alwaysShow=true → ignores client particle settings
                serverLevel.sendParticles(particle, true, true,
                        pos.x, pos.y, pos.z, 1, vel.x, vel.y, vel.z, 0.0);
            } else {
                serverLevel.sendParticles(particle,
                        pos.x, pos.y, pos.z, 1, vel.x, vel.y, vel.z, 0.0);
            }
        } else {
            level.addParticle(particle, pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
        }
    }

    private static void spawnLineInner(ParticleOptions particle, Level level,
                                       Vec3 start, Vec3 end, double deviation) {
        Vec3 step    = end.subtract(start).scale(1.0 / count);
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

    /** Returns any unit vector perpendicular to {@code v}. */
    private static Vec3 getPerpendicular(Vec3 v) {
        Vec3 arbitrary = Math.abs(v.x) < 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return v.cross(arbitrary).normalize();
    }

    private static void clearState() {
        if (persist) return;
        count                = 1;
        forced               = false;
        velocity             = Vec3.ZERO;
        randomizeVelocity    = false;
        randomVelocityScalar = 0;
        persist              = false;
    }
}
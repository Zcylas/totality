package zcylas.totality.client.particles;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Animated 2D particle ring in the XZ plane (horizontal by default), with
 * support for radius expansion, partial arcs, and continuous spin.
 *
 * <h3>Orientation</h3>
 * With {@code yaw = 0} and {@code pitch = 0} the ring lies flat in the XZ plane
 * (horizontal, like a Ground Slam shockwave). Pass entity yaw/pitch to tilt the
 * ring's normal toward any direction.
 *
 * <h3>Modes</h3>
 * <ul>
 *   <li><b>Whole ring</b> ({@code progressive = false}, default): the full arc
 *       up to {@link #maxAngle} is redrawn every tick. Use {@link #radiusGrow}
 *       to make it expand outward. <em>Classic Ground Slam ring.</em></li>
 *   <li><b>Progressive</b> ({@code progressive = true}): the visible arc grows
 *       from 0 to {@link #maxAngle} over the full animation duration.
 *       <em>Classic channeling / charge indicator.</em></li>
 * </ul>
 *
 * <h3>Good for</h3>
 * <ul>
 *   <li>Ground Slam expanding shockwave ring</li>
 *   <li>Channeling / charge progress indicator (arc fills over time)</li>
 *   <li>AOE radius preview (static full ring)</li>
 *   <li>Spinning halo / aura ring above entity head</li>
 *   <li>Blood Moon or eclipse ritual border</li>
 * </ul>
 *
 * <h3>Ground Slam example</h3>
 * <pre>{@code
 * new AnimatedRingAnimation(level, ParticleTypes.CRIT, impactPos)
 *     .setRadius(0.3f)
 *     .setRadiusGrow(0.35f)
 *     .setParticles(48)
 *     .setForced(true)
 *     .runFor(0.8);
 * }</pre>
 *
 * <h3>Heat Vision charge indicator example</h3>
 * <pre>{@code
 * new AnimatedRingAnimation(level, ParticleTypes.END_ROD, player.getEyePosition())
 *     .setRadius(0.4f)
 *     .setProgressive(true)
 *     .setEntityOrigin(player)
 *     .setUseEyePos(true)
 *     .runFor(chargeSeconds);
 * }</pre>
 */
public class AnimatedRingAnimation extends AbstractParticleAnimation<AnimatedRingAnimation> {

    // ── Shape ─────────────────────────────────────────────────────────────────

    /** Starting radius of the ring (blocks). */
    public float radius = 1.0f;

    /**
     * Radius added to the ring after each tick (blocks per tick).
     * {@code 0} = static radius. Positive = expanding ring.
     * Default {@code 0}.
     */
    public float radiusGrow = 0.0f;

    /**
     * Number of particles spread around the full ring ({@code maxAngle = 2π}).
     * Partial rings use fewer particles proportionally.
     */
    public int particles = 40;

    /**
     * Maximum angle of the arc, in radians.
     * {@code 2π} (default) = full circle.
     * {@code π} = semicircle.
     * Combine with {@link #progressive} to draw a filling arc.
     */
    public double maxAngle = Math.PI * 2.0;

    /**
     * If {@code false} (default): the full arc up to {@link #maxAngle} is
     * redrawn every tick — use {@link #radiusGrow} to make it expand.
     * <p>
     * If {@code true}: the visible arc grows from 0 to {@link #maxAngle}
     * linearly over the animation's {@link #iterations} — looks like a loading
     * ring or charge indicator filling up.
     */
    public boolean progressive = false;

    // ── Orientation ───────────────────────────────────────────────────────────

    /**
     * Yaw of the ring's normal direction (degrees).
     * With {@code pitch = 0}, the ring is always horizontal (XZ plane)
     * regardless of yaw — yaw only matters when pitch ≠ 0 and the ring is tilted.
     */
    public float yaw = 0;

    /**
     * Pitch of the ring's normal direction (degrees).
     * {@code 0} = ring lies flat in XZ plane (horizontal).
     * {@code -90} = ring is vertical, normal points upward.
     */
    public float pitch = 0;

    // ── Spin ──────────────────────────────────────────────────────────────────

    /** World-space angular velocity around the X axis (radians per tick). */
    public double angVelX = 0.0;

    /** World-space angular velocity around the Y axis (radians per tick). */
    public double angVelY = 0.0;

    /** World-space angular velocity around the Z axis (radians per tick). */
    public double angVelZ = 0.0;

    // ── Internal ──────────────────────────────────────────────────────────────

    private double rotX = 0.0;
    private double rotY = 0.0;
    private double rotZ = 0.0;
    private float currentRadius;

    // ── Constructor ───────────────────────────────────────────────────────────

    public AnimatedRingAnimation(ServerLevel level, ParticleOptions particle, Vec3 origin) {
        super(level, particle, origin);
        this.currentRadius = radius;
    }

    // ── Core ──────────────────────────────────────────────────────────────────

    @Override
    public void run() {
        // Snapshot the user-set radius when the animation starts, so that
        // repeated run() calls (which should be avoided but are guarded against)
        // don't carry over growth from a previous run.
        this.currentRadius = this.radius;
        super.run();
    }

    @Override
    protected void onRun() {
        Vec3 pos = origin;
        if (pos == null) return;

        // Advance spin before drawing
        rotX += angVelX;
        rotY += angVelY;
        rotZ += angVelZ;

        // Determine visible arc angle this tick
        double visibleAngle;
        if (progressive) {
            // ticks=0 on the first immediate call → arc starts at 0
            visibleAngle = maxAngle * ((double) ticks / Math.max(1, iterations));
        } else {
            visibleAngle = maxAngle;
        }

        // Particle count proportional to the visible fraction of the ring,
        // so density stays constant as the arc fills.
        int count = (int) Math.ceil(particles * (visibleAngle / maxAngle));
        if (count < 1) {
            // Apply growth even on empty frame so radius stays in sync
            currentRadius += radiusGrow;
            return;
        }

        for (int i = 0; i < count; i++) {
            double angle = visibleAngle * i / count;

            // Build point on the ring in local XZ space (y=0)
            Vec3 v = new Vec3(Math.cos(angle) * currentRadius, 0, Math.sin(angle) * currentRadius);

            // Tilt the ring's normal toward (yaw, pitch). At pitch=0 the ring
            // stays horizontal (XZ plane) regardless of yaw.
            v = TotalityVectorUtils.rotateByYawPitch(v, yaw, pitch);

            // Apply world-space spin on top of base orientation
            v = TotalityVectorUtils.rotateX(v, rotX);
            v = TotalityVectorUtils.rotateY(v, rotY);
            v = TotalityVectorUtils.rotateZ(v, rotZ);

            spawnParticle(pos.add(v));
        }

        // Apply radius growth after drawing so the first frame uses the initial radius
        currentRadius += radiusGrow;
    }

    // ── Fluent setters ────────────────────────────────────────────────────────

    public AnimatedRingAnimation setRadius(float radius)          { this.radius      = radius;      return this; }
    public AnimatedRingAnimation setRadiusGrow(float grow)        { this.radiusGrow  = grow;        return this; }
    public AnimatedRingAnimation setParticles(int particles)      { this.particles   = particles;   return this; }
    public AnimatedRingAnimation setMaxAngle(double angle)        { this.maxAngle    = angle;       return this; }
    public AnimatedRingAnimation setProgressive(boolean value)    { this.progressive = value;       return this; }
    public AnimatedRingAnimation setYaw(float yaw)                { this.yaw         = yaw;         return this; }
    public AnimatedRingAnimation setPitch(float pitch)            { this.pitch       = pitch;       return this; }

    /**
     * Sets world-space angular velocity on all three axes (radians per tick).
     */
    public AnimatedRingAnimation setAngularVelocity(double x, double y, double z) {
        this.angVelX = x;
        this.angVelY = y;
        this.angVelZ = z;
        return this;
    }

    public AnimatedRingAnimation setAngVelX(double v) { this.angVelX = v; return this; }
    public AnimatedRingAnimation setAngVelY(double v) { this.angVelY = v; return this; }
    public AnimatedRingAnimation setAngVelZ(double v) { this.angVelZ = v; return this; }
}

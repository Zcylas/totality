package zcylas.totality.client.particles;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Animated particle torus (donut shape) centered on the origin.
 * <p>
 * Parameterised by two radii — {@link #radiusMajor} (distance from the center
 * of the tube to the center of the ring) and {@link #radiusMinor} (radius of
 * the tube cross-section) — and drawn using a standard parametric double-loop
 * over {@code theta} (outer angle) and {@code phi} (tube angle).
 *
 * <h3>Orientation</h3>
 * With {@code yaw = 0} and {@code pitch = 0}, the hole faces along the player's
 * forward direction (the default Mojang convention when both are zero is south,
 * +Z). Pass {@code entity.getYaw()} / {@code entity.getPitch()} to orient the
 * torus hole toward the entity's look direction.
 *
 * <h3>Spin animation</h3>
 * Set any of {@link #angVelX}, {@link #angVelY}, {@link #angVelZ} (radians per
 * tick) to make the torus spin in world space on top of its base orientation.
 *
 * <h3>Growth</h3>
 * {@link #majorGrowth} / {@link #minorGrowth} are added to the respective
 * radii after each {@code onRun()} call, letting the torus expand or collapse
 * over time.
 *
 * <h3>Good for</h3>
 * <ul>
 *   <li>Ritual / altar activation rings (hole facing up, yaw=0 pitch=90)</li>
 *   <li>Kryptonian Solar Flare windup</li>
 *   <li>Bankai release shockwave ring</li>
 *   <li>Portal / dimensional rift frame</li>
 *   <li>Chakra gate opening animation</li>
 * </ul>
 *
 * <h3>Minimal usage — spinning ritual ring</h3>
 * <pre>{@code
 * new TorusAnimation(level, ParticleTypes.END_ROD, altarPos.above())
 *     .setRadiusMajor(1.5f)
 *     .setRadiusMinor(0.15f)
 *     .setResolution(48, 8)
 *     .setAngularVelocity(0, Math.PI / 40, 0)   // slow Y spin
 *     .setForced(true)
 *     .runFor(5.0);
 * }</pre>
 */
public class TorusAnimation extends AbstractParticleAnimation<TorusAnimation> {

    // ── Shape ─────────────────────────────────────────────────────────────────

    /** Distance from the torus center to the center of the tube. */
    public float radiusMajor = 2.0f;

    /** Radius of the tube (the cross-sectional circle). */
    public float radiusMinor = 0.5f;

    /**
     * Number of circles along the outer ring.
     * Higher = more defined torus shape. Default 36 (one per 10°).
     */
    public int circles = 36;

    /**
     * Number of particles per tube cross-section.
     * Higher = thicker, rounder tube. Default 10.
     */
    public int particlesPerCircle = 10;

    // ── Orientation ───────────────────────────────────────────────────────────

    /**
     * Yaw of the torus hole direction (degrees). Pass {@code entity.getYaw()}
     * to have the hole face along the entity's look direction.
     */
    public float yaw = 0;

    /**
     * Pitch of the torus hole direction (degrees).
     * {@code 90} = hole faces down (ring is horizontal, like a ritual circle).
     */
    public float pitch = 0;

    // ── Growth ────────────────────────────────────────────────────────────────

    /** Added to {@link #radiusMajor} after each tick. Negative collapses inward. */
    public float majorGrowth = 0.0f;

    /** Added to {@link #radiusMinor} after each tick. Negative thins the tube. */
    public float minorGrowth = 0.0f;

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

    // ── Constructor ───────────────────────────────────────────────────────────

    public TorusAnimation(ServerLevel level, ParticleOptions particle, Vec3 origin) {
        super(level, particle, origin);
    }

    // ── Core ──────────────────────────────────────────────────────────────────

    @Override
    protected void onRun() {
        Vec3 pos = origin;
        if (pos == null) return;

        // Accumulate spin before drawing so the first immediate call already rotates
        rotX += angVelX;
        rotY += angVelY;
        rotZ += angVelZ;

        for (int i = 0; i < circles; i++) {
            double theta = 2.0 * Math.PI * i / circles;

            for (int j = 0; j < particlesPerCircle; j++) {
                double phi    = 2.0 * Math.PI * j / particlesPerCircle;
                double cosPhi = Math.cos(phi);

                // Parametric torus:
                //   x = (R + r·cos φ) · cos θ
                //   y = (R + r·cos φ) · sin θ
                //   z = r · sin φ
                // In local space the hole faces along the Z axis.
                Vec3 v = new Vec3(
                        (radiusMajor + radiusMinor * cosPhi) * Math.cos(theta),
                        (radiusMajor + radiusMinor * cosPhi) * Math.sin(theta),
                        radiusMinor * Math.sin(phi)
                );

                // Orient the torus hole along (yaw, pitch). +90 on both axes
                // aligns the default local Z hole with the forward direction.
                v = TotalityVectorUtils.rotateByYawPitch(v, yaw + 90, pitch + 90);

                // Apply accumulated world-space spin
                v = TotalityVectorUtils.rotateX(v, rotX);
                v = TotalityVectorUtils.rotateY(v, rotY);
                v = TotalityVectorUtils.rotateZ(v, rotZ);

                spawnParticle(pos.add(v));
            }
        }

        // Apply growth after drawing so the first frame uses the initial radii
        radiusMajor += majorGrowth;
        radiusMinor += minorGrowth;
    }

    // ── Fluent setters ────────────────────────────────────────────────────────

    public TorusAnimation setRadiusMajor(float r)    { this.radiusMajor = r;    return this; }
    public TorusAnimation setRadiusMinor(float r)    { this.radiusMinor = r;    return this; }

    /**
     * Convenience: sets both resolutions at once.
     *
     * @param outerCircles     segments along the outer ring (default 36)
     * @param innerParticles   particles per tube cross-section (default 10)
     */
    public TorusAnimation setResolution(int outerCircles, int innerParticles) {
        this.circles          = outerCircles;
        this.particlesPerCircle = innerParticles;
        return this;
    }

    public TorusAnimation setCircles(int circles)               { this.circles           = circles;    return this; }
    public TorusAnimation setParticlesPerCircle(int p)          { this.particlesPerCircle = p;         return this; }
    public TorusAnimation setYaw(float yaw)                     { this.yaw               = yaw;       return this; }
    public TorusAnimation setPitch(float pitch)                  { this.pitch             = pitch;     return this; }
    public TorusAnimation setMajorGrowth(float growth)          { this.majorGrowth       = growth;    return this; }
    public TorusAnimation setMinorGrowth(float growth)          { this.minorGrowth       = growth;    return this; }

    /**
     * Sets world-space angular velocity on all three axes (radians per tick).
     * Equivalent to calling {@code setAngVelX/Y/Z} individually.
     */
    public TorusAnimation setAngularVelocity(double x, double y, double z) {
        this.angVelX = x;
        this.angVelY = y;
        this.angVelZ = z;
        return this;
    }

    public TorusAnimation setAngVelX(double v) { this.angVelX = v; return this; }
    public TorusAnimation setAngVelY(double v) { this.angVelY = v; return this; }
    public TorusAnimation setAngVelZ(double v) { this.angVelZ = v; return this; }
}

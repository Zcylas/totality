package zcylas.totality.client.particles;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Animated particle arc — draws a parabolic curve between two world positions.
 * The arc peaks at {@link #height} blocks above the midpoint and passes exactly
 * through both endpoints.
 *
 * <h3>Modes</h3>
 * <ul>
 *   <li><b>Static</b> (default, {@code progressive = false}): the full arc is
 *       redrawn every tick. Good for aiming indicators, held energy links,
 *       Reiatsu tethers.</li>
 *   <li><b>Progressive</b> ({@code progressive = true}): the arc grows from
 *       origin outward over the full {@link #iterations} duration — one particle
 *       advances per tick. Looks like lightning shooting toward a target, or a
 *       shuriken arc path extending in real-time.</li>
 * </ul>
 *
 * <h3>Entity target tracking</h3>
 * Call {@link #setEntityTarget(Entity)} to keep the arc aimed at a moving entity.
 * The target position is refreshed every tick inside {@link #onRun()} (not via
 * the base-class entity origin mechanism).
 *
 * <h3>Good for</h3>
 * <ul>
 *   <li>Shuriken throw arc path indicator</li>
 *   <li>Lightning / Byakugan energy arc between player and mob</li>
 *   <li>Gravity Slam parabola</li>
 *   <li>Ability range preview arcs</li>
 * </ul>
 *
 * <h3>Minimal usage</h3>
 * <pre>{@code
 * new ArcAnimation(level, ParticleTypes.ELECTRIC_SPARK, playerPos, targetPos)
 *     .setHeight(3.0f)
 *     .setParticles(60)
 *     .runFor(1.0);
 * }</pre>
 */
public class ArcAnimation extends AbstractParticleAnimation<ArcAnimation> {

    // ── Target ────────────────────────────────────────────────────────────────

    /** Fixed world position of the arc's end point. */
    protected Vec3 targetPos;

    /**
     * Optional moving entity target. If set, {@link #targetPos} is refreshed
     * every tick to the entity's position (feet, not eyes).
     */
    protected Entity entityTarget;

    // ── Shape ─────────────────────────────────────────────────────────────────

    /**
     * Peak height (blocks) of the arc above the origin-target midpoint.
     * The arc passes through exactly 0 height at both endpoints.
     * Negative values produce a downward arc (useful for cave-style gravity).
     */
    public float height = 2.0f;

    /**
     * Number of particles that make up the full arc.
     * Higher = smoother / denser curve. Default 80.
     */
    public int particles = 80;

    /**
     * If true, the arc is drawn progressively — growing from origin to target
     * over the full {@link #iterations} duration. If false (default), the
     * full arc is redrawn every tick.
     */
    public boolean progressive = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ArcAnimation(ServerLevel level, ParticleOptions particle, Vec3 origin, Vec3 target) {
        super(level, particle, origin);
        this.targetPos = target;
    }

    public ArcAnimation(ServerLevel level, ParticleOptions particle, Vec3 origin, Entity target) {
        super(level, particle, origin);
        this.entityTarget = target;
        this.targetPos = target.position();
    }

    // ── Core ──────────────────────────────────────────────────────────────────

    @Override
    protected void onRun() {
        // Refresh entity target position each tick
        if (entityTarget != null) {
            targetPos = entityTarget.position();
        }

        if (targetPos == null) return;

        Vec3 link   = targetPos.subtract(origin);
        double length = link.length();

        // Guard: skip degenerate arcs
        if (length < 0.01) return;

        // Parabola coefficient derived from endpoint constraints:
        //   y = -arcCoeff * x² + height
        //   at x = ±length/2, y must = 0  →  arcCoeff = 4h / L²
        double arcCoeff = 4.0 * height / (length * length);

        // In progressive mode: draw only up to ticks/iterations fraction of the arc
        int count = progressive
                ? Math.max(1, (int) (particles * (double) ticks / Math.max(1, iterations)))
                : particles;

        Vec3 dir = link.normalize();

        for (int i = 0; i < count; i++) {
            // Walk along the straight line from origin to target
            Vec3 along = dir.scale(length * i / (double) particles);

            // x = position along arc, centered at 0 (midpoint = 0, endpoints = ±L/2)
            double x = ((double) i / particles) * length - length * 0.5;
            double y = -arcCoeff * x * x + height;

            spawnParticle(origin.add(along).add(0, y, 0));
        }
    }

    // ── Fluent setters ────────────────────────────────────────────────────────

    /**
     * Sets a fixed world-position target. Replaces any entity target.
     */
    public ArcAnimation setTarget(Vec3 target) {
        this.targetPos    = target;
        this.entityTarget = null;
        return this;
    }

    /**
     * Tracks a moving entity as the arc's target. Updated every tick.
     * Replaces any fixed target.
     */
    public ArcAnimation setEntityTarget(Entity target) {
        this.entityTarget = target;
        this.targetPos    = target.position();
        return this;
    }

    /**
     * Peak height of the arc above the midpoint (blocks).
     * Negative = downward arc.
     */
    public ArcAnimation setHeight(float height)       { this.height      = height;      return this; }

    /** Particle density of the arc. Higher = smoother. */
    public ArcAnimation setParticles(int particles)   { this.particles   = particles;   return this; }

    /**
     * If true, the arc extends progressively from origin to target over the
     * animation duration. Looks like lightning or a projectile leaving a trail.
     */
    public ArcAnimation setProgressive(boolean value) { this.progressive = value;       return this; }
}

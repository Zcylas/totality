package zcylas.totality.client.particles;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Animated multi-strand particle vortex — a helix spiral that extends
 * outward from the origin along the configured look direction, advancing
 * further every tick to create a "shooting" animation.
 *
 * <h3>How it works</h3>
 * Each call to {@code onRun()} draws {@link #circlesPerTick} steps of the
 * helix, starting from an internal {@code step} counter that never resets.
 * Because the Y position of each step is {@code step * lengthGrow}, the
 * visible section of the spiral moves further from the origin every tick —
 * giving the illusion of a vortex shooting outward.
 *
 * <h3>Good for</h3>
 * <ul>
 *   <li>Kryptonian Freeze Breath windup / discharge</li>
 *   <li>Summoning columns / portal openings</li>
 *   <li>Ninjutsu chakra wind-up or Reiatsu burst trails</li>
 *   <li>Abyssal Engine activation sequence</li>
 * </ul>
 *
 * <h3>Minimal usage</h3>
 * <pre>{@code
 * new VortexAnimation(level, ParticleTypes.SOUL_FIRE_FLAME, player.position())
 *     .setYaw(player.getYaw())
 *     .setPitch(player.getPitch())
 *     .setHelixes(3)
 *     .setForced(true)
 *     .runFor(1.5);
 * }</pre>
 */
public class VortexAnimation extends AbstractParticleAnimation<VortexAnimation> {

    // ── Shape parameters ──────────────────────────────────────────────────────

    /** Base radius of the helix at step 0. */
    public float radius = 0.5f;

    /**
     * Radius added per step — makes the helix flare outward as it extends.
     * {@code 0} = constant radius cylinder. Negative = tapers inward.
     */
    public float radiusGrow = 0.0f;

    /**
     * Y offset at step 0 — shifts where along the axis the vortex begins.
     * Useful when the origin is the player's position but you want the
     * vortex to start slightly above them.
     */
    public float startRange = 0.0f;

    /**
     * Y distance the vortex advances per step. Controls how quickly the
     * spiral extends along the look axis. A good starting point is {@code 0.05}.
     */
    public float lengthGrow = 0.05f;

    /**
     * Angle (radians) advanced per step — controls how tightly wound the
     * helix is. Smaller = more tightly wound. Default {@code π/16 ≈ 11°}.
     */
    public double radials = Math.PI / 16;

    /**
     * Number of helix steps drawn per tick. More steps = more particles per
     * frame and a longer visible vortex tail. Default 3.
     */
    public int circlesPerTick = 3;

    /**
     * Number of helix strands. {@code 1} = single spiral.
     * {@code 3–4} = dense multi-strand vortex.
     */
    public int helixes = 4;

    // ── Orientation ───────────────────────────────────────────────────────────

    /**
     * Yaw of the vortex axis in degrees — typically {@code entity.getYaw()}.
     * {@code 0} = along +Z axis (south).
     */
    public float yaw = 0;

    /**
     * Pitch of the vortex axis in degrees — typically {@code entity.getPitch()}.
     * {@code 0} = horizontal, {@code -90} = straight up, {@code 90} = straight down.
     */
    public float pitch = 0;

    /**
     * If true, the vortex shoots backward (negative axis direction) from origin.
     * Useful when the origin is the player's body and you want the vortex
     * trail to extend behind them.
     */
    public boolean flipped = false;

    // ── Internal ──────────────────────────────────────────────────────────────

    /** Continuously incrementing step counter — drives the scroll animation. */
    private int step = 0;

    // ── Constructor ───────────────────────────────────────────────────────────

    public VortexAnimation(ServerLevel level, ParticleOptions particle, Vec3 origin) {
        super(level, particle, origin);
    }

    // ── Core ──────────────────────────────────────────────────────────────────

    @Override
    protected void onRun() {
        Vec3 pos = origin;
        if (pos == null) return;

        for (int c = 0; c < circlesPerTick; c++) {
            for (int i = 0; i < helixes; i++) {
                double angle = step * radials + (2.0 * Math.PI * i / helixes);
                float  r     = radius + step * radiusGrow;
                float  y     = startRange + step * lengthGrow;

                // Build position in local vortex space (Y is the forward axis)
                Vec3 v = new Vec3(Math.cos(angle) * r, y, Math.sin(angle) * r);

                // Orient along look direction — +90 on pitch aligns Y with forward
                v = TotalityVectorUtils.rotateByYawPitch(v, yaw, pitch + 90);

                if (flipped) v = v.scale(-1.0);

                spawnParticle(pos.add(v));
            }
            step++;
        }
    }

    // ── Fluent setters ────────────────────────────────────────────────────────

    public VortexAnimation setRadius(float radius)               { this.radius         = radius;         return this; }
    public VortexAnimation setRadiusGrow(float grow)             { this.radiusGrow     = grow;           return this; }
    public VortexAnimation setStartRange(float startRange)       { this.startRange     = startRange;     return this; }
    public VortexAnimation setLengthGrow(float grow)             { this.lengthGrow     = grow;           return this; }
    public VortexAnimation setRadials(double radials)            { this.radials        = radials;        return this; }
    public VortexAnimation setCirclesPerTick(int circles)        { this.circlesPerTick = circles;        return this; }
    public VortexAnimation setHelixes(int helixes)               { this.helixes        = helixes;        return this; }
    public VortexAnimation setYaw(float yaw)                     { this.yaw            = yaw;            return this; }
    public VortexAnimation setPitch(float pitch)                 { this.pitch          = pitch;          return this; }
    public VortexAnimation setFlipped(boolean flipped)           { this.flipped        = flipped;        return this; }
}

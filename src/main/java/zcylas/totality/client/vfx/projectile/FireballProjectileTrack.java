package zcylas.totality.client.vfx.projectile;

import net.minecraft.world.phys.Vec3;
import zcylas.totality.entity.magic.FireballProjectileEntity;

/**
 * One Fireball V2 projectile as drawn in a frame, and for {@link #IMPACT_SECONDS} after its removal (pure state and
 * timing of {@link FireballProjectileVfx}, unit-tested): growth out of the cast point, the cast flare, and the impact
 * collapse.
 */
final class FireballProjectileTrack {

    static final double STREAK_LENGTH = 3.2;
    /** Blocks from the cast point before anything is drawn (clear of a first-person view), then the growth distance. */
    static final double SHOW_AFTER = 0.6;
    static final double GROW = 2.0;
    static final double IMPACT_SECONDS = 0.12;
    /** Over its first blocks after {@link #SHOW_AFTER} a freshly cast bead flares (the cast flash), fading out. */
    static final double CAST_FLARE_DISTANCE = 2.0;

    FireballProjectileEntity entity;
    Vec3 pos = Vec3.ZERO;
    Vec3 dir = new Vec3(0, 0, 1);
    /** Blocks from the cast point; {@code Double.MAX_VALUE} for a fireball first seen mid-flight. */
    double traveled;
    boolean castSeen;
    double removedAt = Double.NaN;
    float seed;
    long frame;
    // evaluated per frame
    double streak;
    float strength;
    float size;
    float flare;

    /** Growth, cast flare, impact collapse and strength for this frame; false when nothing is drawn yet. */
    boolean evaluate(double now) {
        boolean removed = !Double.isNaN(removedAt);
        if (castSeen && traveled < SHOW_AFTER && !removed) return false;
        double grow = castSeen ? Math.clamp((traveled - SHOW_AFTER) / GROW, 0.0, 1.0) : 1.0;
        double g = grow * grow * (3.0 - 2.0 * grow);
        double reach = castSeen ? Math.max(0.0, traveled - SHOW_AFTER) : STREAK_LENGTH;
        streak = Math.min(STREAK_LENGTH * (0.35 + 0.65 * g), reach);
        double castFlare = castSeen ? Math.clamp(1.0 - (traveled - SHOW_AFTER) / CAST_FLARE_DISTANCE, 0.0, 1.0) : 0.0;
        size = (float) ((0.5 + 0.5 * g) * (1.0 + 0.5 * castFlare));
        strength = (float) Math.min(1.0, 0.35 + 0.65 * g + castFlare);
        flare = (float) castFlare;
        if (removed) {
            double k = Math.clamp((now - removedAt) / IMPACT_SECONDS, 0.0, 1.0);
            streak *= 1.0 - k;
            flare = (float) Math.max(flare, k);
            size *= (float) (1.0 + 0.4 * k);
            strength *= (float) (1.0 - k * k);
        }
        return strength > 0.003f;
    }
}

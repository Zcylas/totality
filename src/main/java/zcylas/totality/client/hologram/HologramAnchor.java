package zcylas.totality.client.hologram;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where the hologram floats: a direction from the eye (yaw/pitch, world space) and a slightly lagging
 * eye point, so the projection feels placed in the world rather than painted on the lens.
 *
 * <ul>
 *   <li><b>Tag-along rotation.</b> The panel holds its world direction while the view stays inside a
 *       dead zone (so the player can glance at a button or look around it), and eases back in front of
 *       the view once they turn further away. It never leaves the field of view for long.</li>
 *   <li><b>Positional inertia.</b> The eye point trails the camera by a short, clamped lag, giving
 *       real parallax when walking or jumping without the panel ever falling behind.</li>
 *   <li><b>Rest position.</b> Above the crosshair ({@code restPitchUp}), so the aimed-at world stays
 *       visible for combat and building.</li>
 * </ul>
 */
final class HologramAnchor {

    static final double DISTANCE = 1.5;
    private static final float DEAD_YAW = 24;
    private static final float DEAD_PITCH = 15;
    private static final float SETTLE = 1.5f;
    private static final float FOLLOW_RATE = 5.5f;
    private static final double LAG_SECONDS = 0.07;
    private static final double LAG_MAX = 0.12;
    private static final double SNAP_DISTANCE = 3;

    private boolean placed;
    private boolean following;
    private float yaw;
    private float pitch;
    private Vec3 eye = Vec3.ZERO;
    private long lastNanos;

    /** Re-centre in front of the view on the next {@link #update} (a new hologram, a new world). */
    void reset() {
        placed = false;
    }

    /**
     * @param deadYaw   how far the view may turn sideways before the panel follows (at least {@link #DEAD_YAW})
     * @param deadPitch how far the view may tilt before the panel follows (at least {@link #DEAD_PITCH})
     */
    void update(Vec3 cameraPos, float viewYaw, float viewPitch, float restPitchUp, float deadYaw, float deadPitch,
                long now) {
        float targetYaw = viewYaw;
        float targetPitch = Mth.clamp(viewPitch - restPitchUp, -85, 85);
        double dt = placed ? Math.min(0.1, (now - lastNanos) / 1e9) : 0;
        lastNanos = now;
        if (!placed || cameraPos.distanceToSqr(eye) > SNAP_DISTANCE * SNAP_DISTANCE) {
            placed = true;
            following = false;
            yaw = targetYaw;
            pitch = targetPitch;
            eye = cameraPos;
            return;
        }
        float dYaw = Mth.wrapDegrees(targetYaw - yaw);
        float dPitch = targetPitch - pitch;
        if (Math.abs(dYaw) > Math.max(DEAD_YAW, deadYaw) || Math.abs(dPitch) > Math.max(DEAD_PITCH, deadPitch)) {
            following = true;
        }
        if (following) {
            float k = (float) (1 - Math.exp(-dt * FOLLOW_RATE));
            yaw += dYaw * k;
            pitch += dPitch * k;
            if (Math.abs(dYaw) < SETTLE && Math.abs(dPitch) < SETTLE) following = false;
        }
        double k = 1 - Math.exp(-dt / LAG_SECONDS);
        Vec3 lagged = eye.add(cameraPos.subtract(eye).scale(k));
        Vec3 offset = lagged.subtract(cameraPos);
        eye = offset.lengthSqr() > LAG_MAX * LAG_MAX ? cameraPos.add(offset.normalize().scale(LAG_MAX)) : lagged;
    }

    /** True while the panel is travelling back in front of the view. */
    boolean following() { return following; }

    float yaw() { return yaw; }
    float pitch() { return pitch; }

    /** Panel centre relative to the camera, for a direction offset (degrees) from the anchor. */
    Vec3 panelOffset(Vec3 cameraPos, float yawOffset, float pitchOffset) {
        return eye.subtract(cameraPos).add(direction(yaw + yawOffset, pitch + pitchOffset).scale(DISTANCE));
    }

    /**
     * Panel centre relative to the camera, {@code right}/{@code up} blocks across the view plane at the
     * panel distance (third person: beside the player while staying parallel to the view).
     */
    Vec3 panelOffsetInView(Vec3 cameraPos, double right, double up) {
        Vec3 forward = direction(yaw, pitch);
        Vec3 rightVec = forward.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 upVec = rightVec.cross(forward).normalize();
        return eye.subtract(cameraPos).add(forward.scale(DISTANCE)).add(rightVec.scale(right)).add(upVec.scale(up));
    }

    static Vec3 direction(float yawDeg, float pitchDeg) {
        float y = yawDeg * Mth.DEG_TO_RAD, p = pitchDeg * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(y) * Mth.cos(p), -Mth.sin(p), Mth.cos(y) * Mth.cos(p));
    }
}

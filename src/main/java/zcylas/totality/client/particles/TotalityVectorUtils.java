package zcylas.totality.client.particles;

import net.minecraft.world.phys.Vec3;

/**
 * Rotation utilities for {@link Vec3} (Mojang mappings).
 * <p>
 * Mojang's {@code Vec3} only exposes {@code xRot}/{@code yRot} — no Z axis,
 * and the naming is inconsistent with what we need for 3-axis torus/vortex
 * math. All methods here take radians unless noted.
 */
public final class TotalityVectorUtils {

    private TotalityVectorUtils() {}

    /** Rotates {@code v} around the X axis by {@code angle} radians. */
    public static Vec3 rotateX(Vec3 v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(v.x, v.y * cos - v.z * sin, v.y * sin + v.z * cos);
    }

    /** Rotates {@code v} around the Y axis by {@code angle} radians. */
    public static Vec3 rotateY(Vec3 v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
    }

    /** Rotates {@code v} around the Z axis by {@code angle} radians. */
    public static Vec3 rotateZ(Vec3 v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new Vec3(v.x * cos - v.y * sin, v.x * sin + v.y * cos, v.z);
    }

    /**
     * Rotates a vector using entity yaw and pitch (both in degrees), matching
     * the MC convention where yaw=0 faces south (+Z) and pitch=0 is horizontal.
     * <p>
     * Passing {@code entity.getYaw()} / {@code entity.getPitch()} orients a
     * local-space particle shape along the entity's look direction.
     * <p>
     * For a ring that should stay flat (XZ plane), pass {@code yaw=0, pitch=0}.
     */
    public static Vec3 rotateByYawPitch(Vec3 v, float yawDegrees, float pitchDegrees) {
        double yaw   = Math.toRadians(-(yawDegrees + 90));
        double pitch = Math.toRadians(-pitchDegrees);

        double cosYaw   = Math.cos(yaw);
        double sinYaw   = Math.sin(yaw);
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);

        // Pitch: Z-axis rotation
        double x = v.x * cosPitch - v.y * sinPitch;
        double y = v.x * sinPitch + v.y * cosPitch;

        // Yaw: Y-axis rotation (using updated x)
        double z = v.z * cosYaw - x * sinYaw;
        x = v.z * sinYaw + x * cosYaw;

        return new Vec3(x, y, z);
    }
}

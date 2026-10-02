package zcylas.totality.client.vfx.ribbon;

import net.minecraft.world.phys.Vec3;

/**
 * A camera-facing segmented ribbon between two points (shared by Heat Vision V2's beams and Fireball V2's streak; pure
 * geometry, unit-testable). Positions are emitted camera-relative ({@code world - camera}), four vertices per quad.
 *
 * <p>The ribbon turns around its own axis to face the camera and is split into segments so its width can follow the
 * distance and a {@link Profile} along its length: the half-width at a point is
 * {@code clamp(profile.halfWidth(t), distance * minHalfAngle, distance * maxHalfAngle) * widthScale}, so a far ribbon
 * stays readable and the part next to the camera cannot fill the screen. Vertices closer to the camera than
 * {@code nearFadeEnd} blocks fade out (alpha). U runs -1..1 across, V is the distance from {@code start} in blocks.
 */
public final class RibbonGeometry {

    /** Receives one vertex: camera-relative position, UV and colour (rgb tint, a strength). */
    public interface VertexSink {
        void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a);
    }

    /** The ribbon's shape along its length, {@code t} running 0 (start) .. 1 (end). */
    public interface Profile {
        double halfWidth(double t);

        default float alpha(double t) {
            return 1.0f;
        }
    }

    /**
     * On-screen width limits (radians of half-width) and the near-camera fade (alpha 0 at {@code nearFadeStart}
     * blocks, 1 from {@code nearFadeEnd} on).
     */
    public record Limits(double minHalfAngle, double maxHalfAngle, double nearFadeStart, double nearFadeEnd) {}

    private RibbonGeometry() {}

    /** A ribbon of constant half-width {@code halfWidth}. */
    public static Profile constant(double halfWidth) {
        return t -> halfWidth;
    }

    /** Emits the ribbon from {@code start} to {@code end} as {@code segments} quads (nothing for a degenerate one). */
    public static void ribbon(Vec3 start, Vec3 end, Vec3 camera, int segments, Profile profile, Limits limits,
                              double widthScale, float r, float g, float b, float strength, VertexSink sink) {
        Vec3 axis = end.subtract(start);
        double length = axis.length();
        if (length < 1e-4 || segments < 1) return;
        Vec3 dir = axis.scale(1.0 / length);
        float[] prev = null;
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            Vec3 rel = start.add(axis.scale(t)).subtract(camera);
            double dist = Math.max(rel.length(), 1e-4);
            Vec3 toCamera = rel.scale(-1.0 / dist);
            Vec3 side = dir.cross(toCamera);
            if (side.lengthSqr() < 1e-10) side = perpendicular(dir);
            side = side.normalize();
            double half = Math.min(Math.max(profile.halfWidth(t), dist * limits.minHalfAngle()), dist * limits.maxHalfAngle()) * widthScale;
            float v = (float) (t * length);
            float alpha = strength * profile.alpha(t) * nearFade(dist, limits);
            float[] cur = {
                    (float) (rel.x + side.x * half), (float) (rel.y + side.y * half), (float) (rel.z + side.z * half),
                    (float) (rel.x - side.x * half), (float) (rel.y - side.y * half), (float) (rel.z - side.z * half),
                    v, alpha};
            if (prev != null) {
                sink.vertex(prev[0], prev[1], prev[2], -1, prev[6], r, g, b, prev[7]);
                sink.vertex(prev[3], prev[4], prev[5], 1, prev[6], r, g, b, prev[7]);
                sink.vertex(cur[3], cur[4], cur[5], 1, cur[6], r, g, b, cur[7]);
                sink.vertex(cur[0], cur[1], cur[2], -1, cur[6], r, g, b, cur[7]);
            }
            prev = cur;
        }
    }

    /** 0 at {@code nearFadeStart} blocks from the camera, 1 from {@code nearFadeEnd} on (smoothstep). */
    public static float nearFade(double dist, Limits limits) {
        double x = Math.clamp((dist - limits.nearFadeStart()) / (limits.nearFadeEnd() - limits.nearFadeStart()), 0.0, 1.0);
        return (float) (x * x * (3.0 - 2.0 * x));
    }

    public static Vec3 perpendicular(Vec3 dir) {
        Vec3 p = dir.cross(new Vec3(0, 1, 0));
        return p.lengthSqr() < 1e-10 ? dir.cross(new Vec3(1, 0, 0)) : p;
    }
}

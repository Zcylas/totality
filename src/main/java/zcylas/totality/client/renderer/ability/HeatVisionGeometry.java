package zcylas.totality.client.renderer.ability;

import net.minecraft.world.phys.Vec3;

/**
 * Pure geometry of Heat Vision V2 (no rendering state, unit-testable). Positions are emitted camera-relative
 * ({@code world - camera}), four vertices per quad.
 *
 * <ul>
 *   <li><b>Ribbon</b>: the beam is a strip of quads that always faces the camera (it turns around its own axis), split
 *       into segments so the width can follow the distance: never thinner than {@code minHalfAngle} radians on screen,
 *       so a far beam stays readable instead of shrinking to a sub-pixel line, and never wider than
 *       {@link #MAX_HALF_ANGLE}, so the part next to the camera cannot fill the screen. Vertices closer to the camera
 *       than {@link #NEAR_FADE_END} blocks fade out (alpha), so beams emerge softly from the eyes and a beam passing
 *       through the camera cannot flood the view.</li>
 *   <li><b>Hotspot</b>: a camera-facing square at the impact point, pulled slightly towards the camera so the surface
 *       it hits does not cut it in half.</li>
 * </ul>
 */
public final class HeatVisionGeometry {

    /** Receives one vertex: camera-relative position, UV and colour (rgb tint, a strength). */
    public interface VertexSink {
        void vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a);
    }

    static final double HOTSPOT_PULL = 0.2;
    /** Maximum on-screen half-width of a ribbon, in radians (about 13 px at 1080p and a 70 degree field of view). */
    static final double MAX_HALF_ANGLE = 0.018;
    static final double NEAR_FADE_START = 0.25;
    static final double NEAR_FADE_END = 1.2;

    private HeatVisionGeometry() {}

    /**
     * Emits the camera-facing ribbon of {@code beam}: {@code segments} quads, half-width
     * {@code clamp(baseHalfWidth, distance * minHalfAngle, distance * MAX_HALF_ANGLE) * widthScale}. U runs -1..1 across,
     * V is the distance from the eye in blocks; alpha is the beam strength times the near-camera fade.
     */
    public static void ribbon(HeatVisionBeam beam, Vec3 camera, int segments, double baseHalfWidth, double minHalfAngle,
                              double widthScale, float r, float g, float b, VertexSink sink) {
        Vec3 start = beam.start();
        Vec3 end = beam.visibleEnd();
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
            double half = Math.min(Math.max(baseHalfWidth, dist * minHalfAngle), dist * MAX_HALF_ANGLE) * widthScale;
            float v = (float) (t * length);
            float alpha = beam.strength() * nearFade(dist);
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

    /** Emits the impact hotspot quad (UV -1..1 in both directions) if the beam reaches its impact point. */
    public static void hotspot(HeatVisionBeam beam, Vec3 camera, double baseHalfSize, double minHalfAngle,
                               float r, float g, float b, VertexSink sink) {
        if (!beam.reachesImpact()) return;
        Vec3 rel = beam.end().subtract(camera);
        double dist = Math.max(rel.length(), 1e-4);
        Vec3 toCamera = rel.scale(-1.0 / dist);
        Vec3 centre = rel.add(toCamera.scale(Math.min(HOTSPOT_PULL, dist * 0.5)));
        Vec3 right = toCamera.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-10) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(toCamera).normalize();
        double h = Math.max(baseHalfSize, dist * minHalfAngle);
        float a = beam.strength();
        corner(sink, centre, right, up, -h, -h, -1, -1, r, g, b, a);
        corner(sink, centre, right, up, h, -h, 1, -1, r, g, b, a);
        corner(sink, centre, right, up, h, h, 1, 1, r, g, b, a);
        corner(sink, centre, right, up, -h, h, -1, 1, r, g, b, a);
    }

    private static void corner(VertexSink sink, Vec3 c, Vec3 right, Vec3 up, double x, double y, float u, float v,
                               float r, float g, float b, float a) {
        sink.vertex((float) (c.x + right.x * x + up.x * y), (float) (c.y + right.y * x + up.y * y),
                (float) (c.z + right.z * x + up.z * y), u, v, r, g, b, a);
    }

    /** 0 at {@link #NEAR_FADE_START} blocks from the camera, 1 from {@link #NEAR_FADE_END} on (smoothstep). */
    static float nearFade(double dist) {
        double x = Math.clamp((dist - NEAR_FADE_START) / (NEAR_FADE_END - NEAR_FADE_START), 0.0, 1.0);
        return (float) (x * x * (3.0 - 2.0 * x));
    }

    static Vec3 perpendicular(Vec3 dir) {
        Vec3 p = dir.cross(new Vec3(0, 1, 0));
        return p.lengthSqr() < 1e-10 ? dir.cross(new Vec3(1, 0, 0)) : p;
    }
}

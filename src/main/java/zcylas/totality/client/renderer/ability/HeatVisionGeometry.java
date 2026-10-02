package zcylas.totality.client.renderer.ability;

import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.vfx.ribbon.RibbonGeometry;

/**
 * Pure geometry of Heat Vision V2 (no rendering state, unit-testable). Positions are emitted camera-relative
 * ({@code world - camera}), four vertices per quad.
 *
 * <ul>
 *   <li><b>Ribbon</b> (the shared {@link RibbonGeometry}, constant width): the beam is a strip of quads that always
 *       faces the camera (it turns around its own axis), split into segments so the width can follow the distance:
 *       never thinner than {@code minHalfAngle} radians on screen,
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
    public interface VertexSink extends RibbonGeometry.VertexSink {}

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
        RibbonGeometry.ribbon(beam.start(), beam.visibleEnd(), camera, segments, RibbonGeometry.constant(baseHalfWidth),
                new RibbonGeometry.Limits(minHalfAngle, MAX_HALF_ANGLE, NEAR_FADE_START, NEAR_FADE_END), widthScale,
                r, g, b, beam.strength(), sink);
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
        return RibbonGeometry.nearFade(dist, new RibbonGeometry.Limits(0, MAX_HALF_ANGLE, NEAR_FADE_START, NEAR_FADE_END));
    }
}

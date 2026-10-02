package zcylas.totality.client.renderer.ability;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class HeatVisionGeometryTest {

    private record V(float x, float y, float z, float u, float v, float a) {
        Vec3 pos() {
            return new Vec3(x, y, z);
        }
    }

    private static List<V> collect(Consumer<HeatVisionGeometry.VertexSink> emit) {
        List<V> out = new ArrayList<>();
        emit.accept((x, y, z, u, v, r, g, b, a) -> out.add(new V(x, y, z, u, v, a)));
        return out;
    }

    private static final Vec3 CAMERA = new Vec3(0.0, 64.0, 0.0);

    @Test
    void ribbonHasFourVerticesPerSegmentAndRunsFromTheEye() {
        HeatVisionBeam beam = new HeatVisionBeam(new Vec3(-3, 64, 5), new Vec3(3, 64, 5), false, 1.0f, 1.0f);
        List<V> vs = collect(s -> HeatVisionGeometry.ribbon(beam, CAMERA, 8, 0.05, 0.0018, 1.0, 1, 1, 1, s));
        assertEquals(8 * 4, vs.size());
        assertEquals(0.0f, vs.get(0).v(), 1e-6, "V starts at the eye");
        assertEquals(6.0f, vs.get(vs.size() - 2).v(), 1e-4, "V ends at the beam length in blocks");
        assertEquals(-1.0f, vs.get(0).u());
        assertEquals(1.0f, vs.get(1).u());
    }

    @Test
    void ribbonFacesTheCamera() {
        HeatVisionBeam beam = new HeatVisionBeam(new Vec3(-3, 65, 6), new Vec3(4, 63, 9), false, 1.0f, 1.0f);
        List<V> vs = collect(s -> HeatVisionGeometry.ribbon(beam, CAMERA, 4, 0.05, 0.0018, 1.0, 1, 1, 1, s));
        for (int q = 0; q < vs.size(); q += 4) {
            Vec3 left = vs.get(q).pos(), right = vs.get(q + 1).pos();
            Vec3 mid = left.add(right).scale(0.5);
            Vec3 across = right.subtract(left).normalize();
            assertEquals(0.0, across.dot(mid.normalize()), 1e-3, "the ribbon's width is perpendicular to the view ray");
        }
    }

    @Test
    void farBeamsKeepAMinimumScreenWidth() {
        double minAngle = 0.0018;
        HeatVisionBeam far = new HeatVisionBeam(new Vec3(-5, 64, 200), new Vec3(5, 64, 200), false, 1.0f, 1.0f);
        List<V> vs = collect(s -> HeatVisionGeometry.ribbon(far, CAMERA, 2, 0.05, minAngle, 1.0, 1, 1, 1, s));
        V a = vs.get(0), b = vs.get(1);
        double width = a.pos().distanceTo(b.pos());
        double dist = a.pos().add(b.pos()).scale(0.5).length();
        assertEquals(2.0 * minAngle, width / dist, 1e-4, "angular width = 2 x minimum half-angle");
        assertTrue(width > 2 * 0.05, "wider than the base width at 200 blocks");
    }

    @Test
    void ignitingBeamGrowsFromTheEye() {
        HeatVisionBeam half = new HeatVisionBeam(new Vec3(0, 64, 5), new Vec3(0, 64, 15), true, 0.5f, 0.5f);
        assertEquals(new Vec3(0, 64, 10), half.visibleEnd());
        assertFalse(half.reachesImpact());
        assertTrue(collect(s -> HeatVisionGeometry.hotspot(half, CAMERA, 0.3, 0.007, 1, 1, 1, s)).isEmpty(),
                "no hotspot before the beam arrives");
        List<V> vs = collect(s -> HeatVisionGeometry.ribbon(half, CAMERA, 5, 0.05, 0.0018, 1.0, 1, 1, 1, s));
        assertEquals(5.0f, vs.get(vs.size() - 2).v(), 1e-4);
        assertEquals(0.5f, vs.get(0).a(), 1e-6, "strength travels in vertex alpha (5 blocks away: no near fade)");
    }

    @Test
    void nearCameraPartsAreNarrowAndFaded() {
        // A beam passing right next to the camera: width capped at MAX_HALF_ANGLE, alpha fading towards the camera.
        HeatVisionBeam through = new HeatVisionBeam(new Vec3(-0.3, 64.1, 0.2), new Vec3(0.3, 64.1, 8), false, 1.0f, 1.0f);
        List<V> vs = collect(s -> HeatVisionGeometry.ribbon(through, CAMERA, 16, 0.05, 0.0018, 1.0, 1, 1, 1, s));
        V a = vs.get(0), b = vs.get(1);
        double dist = a.pos().add(b.pos()).scale(0.5).length();
        assertTrue(a.pos().distanceTo(b.pos()) / dist <= 2 * HeatVisionGeometry.MAX_HALF_ANGLE + 1e-4, "angular width capped");
        assertTrue(a.a() < 0.2f, "faded next to the camera");
        assertEquals(1.0f, vs.get(vs.size() - 1).a(), 1e-6, "full strength far away");
        assertEquals(0.0f, HeatVisionGeometry.nearFade(0.1), 1e-6);
        assertEquals(1.0f, HeatVisionGeometry.nearFade(HeatVisionGeometry.NEAR_FADE_END), 1e-6);
    }

    @Test
    void hotspotSitsAtTheImpactPulledTowardsTheCamera() {
        HeatVisionBeam hit = new HeatVisionBeam(new Vec3(0, 64, 1), new Vec3(0, 64, 10), true, 1.0f, 1.0f);
        List<V> vs = collect(s -> HeatVisionGeometry.hotspot(hit, CAMERA, 0.3, 0.007, 1, 1, 1, s));
        assertEquals(4, vs.size());
        Vec3 centre = Vec3.ZERO;
        for (V v : vs) centre = centre.add(v.pos().scale(0.25));
        assertEquals(10.0 - HeatVisionGeometry.HOTSPOT_PULL, centre.z, 1e-4);
        assertTrue(collect(s -> HeatVisionGeometry.hotspot(
                new HeatVisionBeam(new Vec3(0, 64, 1), new Vec3(0, 64, 10), false, 1, 1), CAMERA, 0.3, 0.007, 1, 1, 1, s)).isEmpty(),
                "a miss has no hotspot");
    }

    @Test
    void degenerateBeamsEmitNothing() {
        HeatVisionBeam zero = new HeatVisionBeam(new Vec3(1, 64, 1), new Vec3(1, 64, 1), true, 1, 1);
        assertTrue(collect(s -> HeatVisionGeometry.ribbon(zero, CAMERA, 8, 0.05, 0.0018, 1.0, 1, 1, 1, s)).isEmpty());
    }
}

package zcylas.totality.client.vfx.ribbon;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shared camera-facing ribbon: width profile, angular limits, near fade (Heat Vision's own tests cover its use). */
class RibbonGeometryTest {

    private static final RibbonGeometry.Limits LIMITS = new RibbonGeometry.Limits(0.0025, 0.03, 0.6, 2.0);

    private record V(Vec3 pos, float u, float v, float a) {}

    private static List<V> ribbon(Vec3 start, Vec3 end, RibbonGeometry.Profile profile) {
        List<V> out = new ArrayList<>();
        RibbonGeometry.ribbon(start, end, Vec3.ZERO, 8, profile, LIMITS, 1.0, 1, 1, 1, 1.0f,
                (x, y, z, u, v, r, g, b, a) -> out.add(new V(new Vec3(x, y, z), u, v, a)));
        return out;
    }

    private static double width(List<V> vs, int quad, boolean far) {
        V a = vs.get(quad * 4 + (far ? 3 : 0)), b = vs.get(quad * 4 + (far ? 2 : 1));
        return a.pos().distanceTo(b.pos());
    }

    @Test
    void aTaperNarrowsAlongTheRibbon() {
        RibbonGeometry.Profile taper = t -> 0.17 * (0.15 + 0.85 * Math.pow(1.0 - t, 0.7));
        List<V> vs = ribbon(new Vec3(-1.6, 0, 10), new Vec3(1.6, 0, 10), taper);
        assertEquals(32, vs.size());
        assertTrue(width(vs, 0, false) > 3 * width(vs, 7, true), "wide at the head, thin at the tail");
        assertEquals(3.2f, vs.get(31).v(), 1e-4f, "V is the distance from the start");
    }

    @Test
    void widthStaysWithinItsOnScreenLimits() {
        List<V> far = ribbon(new Vec3(0, 0, 200), new Vec3(1, 0, 200), RibbonGeometry.constant(0.01));
        assertTrue(width(far, 0, false) / 200 >= 2 * 0.0025 - 1e-6, "never thinner than the minimum angle");
        List<V> near = ribbon(new Vec3(-0.5, 0, 1), new Vec3(0.5, 0, 1), RibbonGeometry.constant(1.0));
        assertTrue(width(near, 4, false) / 1.0 <= 2 * 0.03 + 1e-3, "never wider than the maximum angle");
    }

    @Test
    void fadesNextToTheCameraAndDegenerateRibbonsEmitNothing() {
        assertEquals(0.0f, RibbonGeometry.nearFade(0.3, LIMITS));
        assertEquals(1.0f, RibbonGeometry.nearFade(5.0, LIMITS));
        assertTrue(ribbon(new Vec3(0, 0, 3), new Vec3(0, 0, 3), RibbonGeometry.constant(0.1)).isEmpty());
    }
}

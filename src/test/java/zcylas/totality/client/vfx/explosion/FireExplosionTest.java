package zcylas.totality.client.vfx.explosion;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Geometry and timing rules of the layered fire explosion (VFX Experiment 3, Fireball V2 phase B2). */
class FireExplosionTest {

    private static final FireExplosionStyle STYLE = new FireExplosionStyle(0.30, 0.40, 0.42, 1.10, 1.28, 80, 12, 30, 10.0, 0.8, 0.25);
    private static final double R = 6.0;
    private static final FireExplosion.Obstruction OPEN = (from, dir, max) -> max;
    private static final FireExplosion.GroundProbe FLAT = (x, y, z, depth) -> 0.0;

    private static List<FireExplosion.Element> at(FireExplosion e, double t) {
        List<FireExplosion.Element> out = new ArrayList<>();
        e.evaluate(t, out, new FireExplosion.ElementPool());
        return out;
    }

    private static double reach(FireExplosion.Element el, Vec3 c) {
        if (el.kind == FireExplosion.Kind.BODY || el.kind == FireExplosion.Kind.CORE) {
            return new Vec3(el.x, el.y, el.z).distanceTo(c) + FireExplosion.EDGE * el.width;
        }
        return new Vec3(el.x + el.ax * el.length, el.y + el.ay * el.length, el.z + el.az * el.length).distanceTo(c);
    }

    @Test
    void fireNeverReachesPastTheDamageRadius() {
        Vec3 c = new Vec3(0, 8, 0);
        for (long seed = 1; seed <= 20; seed++) {
            FireExplosion e = FireExplosion.create(c, null, R, Double.NaN, seed, STYLE, OPEN, FLAT, 0);
            for (double t = 0; t <= 1.6; t += 0.02) {
                for (FireExplosion.Element el : at(e, t)) {
                    if (el.heat < 0.3f) continue;          // only cooling soot may drift past (cosmetic)
                    assertTrue(reach(el, c) <= R + 1e-6, "seed " + seed + " t " + t + " " + el.kind + " reaches " + reach(el, c));
                }
            }
        }
    }

    @Test
    void timingFollowsTheStoryboard() {
        FireExplosion e = FireExplosion.create(new Vec3(0, 8, 0), null, R, Double.NaN, 7, STYLE, OPEN, FLAT, 0);
        assertTrue(at(e, 0.05).stream().anyMatch(el -> el.kind == FireExplosion.Kind.CORE), "ignition core early");
        assertTrue(at(e, 0.2).stream().noneMatch(el -> el.kind == FireExplosion.Kind.CORE), "core gone by 0.2 s");
        float early = (float) at(e, 0.05).stream().filter(el -> el.kind == FireExplosion.Kind.BODY).mapToDouble(el -> el.heat).max().orElse(0);
        float peak = (float) at(e, 0.45).stream().filter(el -> el.kind == FireExplosion.Kind.BODY).mapToDouble(el -> el.heat).min().orElse(0);
        float late = (float) at(e, 1.0).stream().filter(el -> el.kind == FireExplosion.Kind.BODY).mapToDouble(el -> el.heat).max().orElse(0);
        float end = (float) at(e, 1.3).stream().filter(el -> el.kind == FireExplosion.Kind.BODY).mapToDouble(el -> el.heat).max().orElse(0);
        assertTrue(early > 0.85f, "white-hot/gold at the start");
        assertTrue(peak > 0.45f, "still orange or hotter everywhere at the 0.45 s peak: " + peak);
        assertTrue(late < 0.5f, "cooling to red by 1 s: " + late);
        assertTrue(end < 0.3f, "red/soot by 1.3 s: " + end);
        assertFalse(at(e, 1.5).stream().anyMatch(el -> el.opacity > 0.01f), "shell gone by 1.5 s");
        assertTrue(e.finished(STYLE.totalSeconds() + 0.01));
        assertTrue(STYLE.totalSeconds() <= 1.6);
    }

    @Test
    void theFlameFrontBlastsOutWithinATenthOfASecond() {
        Vec3 c = new Vec3(0, 8, 0);
        FireExplosion e = FireExplosion.create(c, null, R, Double.NaN, 3, STYLE, OPEN, FLAT, 0);
        double start = at(e, 0.01).stream().mapToDouble(el -> reach(el, c)).max().orElse(0);
        double front = at(e, 0.10).stream().filter(el -> el.kind == FireExplosion.Kind.BODY).mapToDouble(el -> reach(el, c)).max().orElse(0);
        double peak = at(e, 0.32).stream().mapToDouble(el -> reach(el, c)).max().orElse(0);
        assertTrue(start < 0.5 * peak, "starts compact at the centre: " + start);
        assertTrue(front > 0.85 * peak, "the flame front is near full size by 0.10 s: " + front);
        assertTrue(peak > 0.9 * R, "fills the sphere at peak: " + peak);
    }

    @Test
    void fastVolumesStretchOutwardThenSettle() {
        FireExplosion e = FireExplosion.create(new Vec3(0, 8, 0), null, R, Double.NaN, 3, STYLE, OPEN, FLAT, 0);
        assertTrue(at(e, 0.02).stream().anyMatch(el -> el.kind == FireExplosion.Kind.BODY && el.stretch > 1.8f), "streaking at launch");
        assertTrue(at(e, 0.40).stream().allMatch(el -> el.stretch < 1.03f), "round again once burning in place");
    }

    @Test
    void aSurfaceHitBulgesOutOfTheSurface() {
        Vec3 c = new Vec3(0, 1, 0);
        Vec3 wallNormal = new Vec3(0, 0, -1);
        FireExplosion e = FireExplosion.create(c, wallNormal, R, 0.0, 11, STYLE, OPEN, FLAT, 0);
        for (FireExplosion.Element el : at(e, 0.4)) {
            if (el.kind == FireExplosion.Kind.BODY) assertTrue(el.z - c.z <= 1e-9, "no body volume behind the wall");
            if (el.kind == FireExplosion.Kind.TONGUE) assertTrue(el.az <= 1e-9, "tongues point away from the wall");
        }
    }

    @Test
    void wallsStopTheFire() {
        Vec3 c = new Vec3(0, 3, 0);
        FireExplosion.Obstruction box = (from, dir, max) -> Math.min(max, 2.0);   // solid 2 blocks away everywhere
        FireExplosion e = FireExplosion.create(c, null, R, Double.NaN, 5, STYLE, box, FLAT, 0);
        for (double t = 0; t < 1.0; t += 0.05) {
            for (FireExplosion.Element el : at(e, t)) {
                if (el.kind == FireExplosion.Kind.BODY) {
                    assertTrue(new Vec3(el.x, el.y, el.z).distanceTo(c) <= 2.0 + 0.1, "body centres stay inside the room");
                }
                if (el.kind == FireExplosion.Kind.TONGUE) {
                    assertTrue(reach(el, c) <= 2.0 - 0.3 + 1e-6, "tongue tips stop short of the walls");
                }
            }
        }
    }

    @Test
    void groundLicksOnlyWhereTheSphereReachesTheGround() {
        FireExplosion air = FireExplosion.create(new Vec3(0, 9, 0), null, R, 0.0, 1, STYLE, OPEN, FLAT, 0);
        assertEquals(0, air.lickCount(), "9 blocks up: the 6-block sphere does not reach the ground");
        assertFalse(air.groundReached());
        FireExplosion none = FireExplosion.create(new Vec3(0, 9, 0), null, R, Double.NaN, 1, STYLE, OPEN, FLAT, 0);
        assertEquals(0, none.lickCount(), "no ground found");
        FireExplosion floor = FireExplosion.create(new Vec3(0, 0.25, 0), new Vec3(0, 1, 0), R, 0.0, 1, STYLE, OPEN, FLAT, 0);
        assertTrue(floor.lickCount() > 20);
        double ring = Math.sqrt(R * R - 0.25 * 0.25);
        for (FireExplosion.Element el : at(floor, 0.5)) {
            if (el.kind == FireExplosion.Kind.GROUND) {
                assertTrue(Math.hypot(el.x, el.z) <= ring + 1e-6, "licks stay within the sphere's ground footprint");
                assertEquals(0.0, el.y, 1e-9, "licks stand on the ground");
            }
        }
    }

    @Test
    void elementCountIsBoundedPerExplosion() {
        FireExplosion e = FireExplosion.create(new Vec3(0, 0.25, 0), new Vec3(0, 1, 0), R, 0.0, 1, STYLE, OPEN, FLAT, 0);
        int max = 0;
        for (double t = 0; t < 1.6; t += 0.02) max = Math.max(max, at(e, t).size());
        assertTrue(max <= 1 + STYLE.bodyVolumes() + STYLE.tongues() + STYLE.groundLicks(), "max " + max);
    }

    @Test
    void oneExplosionsGlowDemandPeaksAtOne() {
        double peak = 0;
        for (long seed = 1; seed <= 12; seed++) {
            FireExplosion e = FireExplosion.create(new Vec3(0, 0.25, 0), new Vec3(0, 1, 0), R, 0.0, seed, STYLE, OPEN, FLAT, 0);
            for (double t = 0; t <= 1.6; t += 0.01) {
                double sum = at(e, t).stream().mapToDouble(FireExplosion::glowWeight).sum();
                peak = Math.max(peak, sum);
            }
        }
        System.out.println("peak glow weight of one explosion: " + peak);
        assertTrue(peak > FireExplosion.GLOW_PEAK * 0.8 && peak < FireExplosion.GLOW_PEAK * 1.25,
                "GLOW_PEAK matches the model's brightest moment: " + peak);
    }
}

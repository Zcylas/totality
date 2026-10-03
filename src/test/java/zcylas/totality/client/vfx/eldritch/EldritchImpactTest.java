package zcylas.totality.client.vfx.eldritch;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Eldritch Blast V2 impact: deterministic sparks thrown off the surface, flash timing, fizzle, cleanup. */
class EldritchImpactTest {

    @Test
    void sparksAreDeterministicAndLeaveTheSurface() {
        Vec3 normal = new Vec3(-1, 0, 0);
        EldritchImpact a = new EldritchImpact(Vec3.ZERO, normal, 0.0, 42L, false);
        EldritchImpact b = new EldritchImpact(Vec3.ZERO, normal, 0.0, 42L, false);
        assertEquals(EldritchImpact.SPARKS, a.sparks.length);
        for (int i = 0; i < a.sparks.length; i++) {
            assertEquals(a.sparks[i], b.sparks[i], "same seed, same sparks");
            assertTrue(a.sparks[i].velocity().dot(normal) > 0.0, "every spark leaves the surface");
            assertTrue(a.sparkPos(a.sparks[i], 0.05).subtract(a.at).dot(normal) > 0.0);
        }
    }

    @Test
    void sparksSlowDownAndFall() {
        EldritchImpact i = new EldritchImpact(Vec3.ZERO, new Vec3(0, 1, 0), 0.0, 7L, false);
        EldritchImpact.Spark s = i.sparks[0];
        double first = i.sparkPos(s, 0.05).distanceTo(i.sparkPos(s, 0.0));
        double later = i.sparkPos(s, 0.5).distanceTo(i.sparkPos(s, 0.45));
        assertTrue(later < first, "drag slows the spark");
        Vec3 noGravity = s.velocity().scale((1.0 - Math.exp(-EldritchImpact.DRAG * 0.5)) / EldritchImpact.DRAG);
        assertEquals(noGravity.y - 0.5 * EldritchImpact.GRAVITY * 0.25, i.sparkPos(s, 0.5).y, 1e-9);
    }

    @Test
    void theImpactEndsAfterItsLongestSpark() {
        EldritchImpact i = new EldritchImpact(Vec3.ZERO, new Vec3(0, 0, -1), 10.0, 3L, false);
        assertTrue(i.duration() >= EldritchImpact.LIFE);
        assertFalse(i.finished(10.0 + EldritchImpact.LIFE / 2));
        assertTrue(i.finished(10.0 + i.duration() + 1e-9));
        assertEquals(0.5, i.progress(10.0 + EldritchImpact.LIFE / 2), 1e-9);
    }

    @Test
    void aFizzleHasNoSparksAndAZeroNormalIsSafe() {
        EldritchImpact i = new EldritchImpact(Vec3.ZERO, Vec3.ZERO, 0.0, 1L, true);
        assertEquals(0, i.sparks.length);
        assertEquals(1.0, i.normal.length(), 1e-9);
        assertTrue(i.finished(0.0));
        assertTrue(i.seed >= 0.0f && i.seed < 1.0f);
    }
}

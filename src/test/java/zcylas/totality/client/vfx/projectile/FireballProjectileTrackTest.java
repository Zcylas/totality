package zcylas.totality.client.vfx.projectile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fireball V2 projectile timing (B3): growth out of the cast point, the cast flare, mid-flight sighting, impact collapse. */
class FireballProjectileTrackTest {

    private static FireballProjectileTrack track(boolean castSeen, double traveled) {
        FireballProjectileTrack t = new FireballProjectileTrack();
        t.castSeen = castSeen;
        t.traveled = castSeen ? traveled : Double.MAX_VALUE;
        return t;
    }

    @Test
    void aSeenCastGrowsOutOfTheCastPointWithAFlare() {
        assertFalse(track(true, 0.3).evaluate(0), "hidden right in front of the caster's eyes");
        FireballProjectileTrack early = track(true, 1.0);
        assertTrue(early.evaluate(0));
        assertTrue(early.streak <= 1.0 - FireballProjectileTrack.SHOW_AFTER + 1e-9, "the streak never reaches back past the cast point");
        assertTrue(early.flare > 0.7f, "the cast flash: the bead flares as it emerges");
        FireballProjectileTrack full = track(true, 20.0);
        assertTrue(full.evaluate(0));
        assertEquals(FireballProjectileTrack.STREAK_LENGTH, full.streak, 1e-9);
        assertEquals(0.0f, full.flare);
        assertEquals(1.0f, full.size, 1e-6f);
    }

    @Test
    void aFireballFirstSeenMidFlightIsCompleteAtOnceWithoutTheFlare() {
        FireballProjectileTrack t = track(false, 0);
        assertTrue(t.evaluate(0));
        assertEquals(FireballProjectileTrack.STREAK_LENGTH, t.streak, 1e-9);
        assertEquals(0.0f, t.flare);
        assertEquals(1.0f, t.strength, 1e-6f);
    }

    @Test
    void onRemovalTheStreakCollapsesIntoTheBeadAndFades() {
        FireballProjectileTrack t = track(true, 20.0);
        t.removedAt = 5.0;
        assertTrue(t.evaluate(5.0 + FireballProjectileTrack.IMPACT_SECONDS * 0.5));
        assertTrue(t.streak < FireballProjectileTrack.STREAK_LENGTH * 0.6 && t.flare > 0.4f);
        assertFalse(t.evaluate(5.0 + FireballProjectileTrack.IMPACT_SECONDS), "gone after the collapse");
        assertEquals(0.0, t.streak, 1e-9);
    }
}

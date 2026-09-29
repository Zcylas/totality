package zcylas.totality.entity.animal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.entity.animal.ForestBoarRules.*;

/** The Forest Boar's decisions: detection, the single charge, how a charge ends, and when an escape is allowed. */
class ForestBoarRulesTest {

    @Test
    void sensesNearbyPlayersAndSpotsVisibleOnesInFront() {
        assertTrue(detects(PROXIMITY_RADIUS, 180.0, false), "sensed close by, even behind it and unseen");
        assertTrue(detects(SIGHT_RADIUS, 0.0, true), "spotted ahead with line of sight");
        assertFalse(detects(SIGHT_RADIUS, 0.0, false), "not through walls beyond sensing range");
        assertFalse(detects(SIGHT_RADIUS - 1, SIGHT_HALF_ANGLE_DEGREES + 5, true), "not behind it beyond sensing range");
        assertFalse(detects(SIGHT_RADIUS + 0.5, 0.0, true), "not beyond sight range");
    }

    @Test
    void chargesAtMostOnceAndOnlyAtAValidTarget() {
        assertTrue(chooseCharge(false, true, 0.0F, CHARGE_CHANCE));
        assertFalse(chooseCharge(false, true, CHARGE_CHANCE, CHARGE_CHANCE), "the roll decides");
        assertFalse(chooseCharge(true, true, 0.0F, 1.0F), "never a second charge");
        assertFalse(chooseCharge(false, false, 0.0F, 1.0F), "no valid target: flee");
        assertTrue(CHARGE_CHANCE > 0 && CHARGE_CHANCE < 1 && HURT_CHARGE_CHANCE > 0 && HURT_CHARGE_CHANCE < 1, "both reactions happen");
    }

    @Test
    void chargeableOnlyWithinReachOnLevelGroundAndInSight() {
        assertTrue(chargeable(6, 0, true));
        assertFalse(chargeable(6, 0, false));
        assertFalse(chargeable(CHARGE_MIN_START_DISTANCE - 0.1, 0, true));
        assertFalse(chargeable(CHARGE_MAX_START_DISTANCE + 0.1, 0, true));
        assertFalse(chargeable(6, CHARGE_MAX_HEIGHT_DIFFERENCE + 0.5, true));
    }

    @Test
    void aRushIsBriefAndAlwaysEnds() {
        assertEquals(6 + CHARGE_OVERSHOOT, chargeDistance(6), 1e-9, "through the player's spot, a little beyond");
        assertEquals(CHARGE_MAX_DISTANCE, chargeDistance(100), 1e-9);
        assertEquals("hit", chargeEnd(true, 1, 8, 5, 0));
        assertEquals("missed", chargeEnd(false, 8, 8, 20, 0));
        assertEquals("blocked", chargeEnd(false, 2, 8, 12, CHARGE_BLOCKED_TICKS));
        assertEquals("timed out", chargeEnd(false, 2, 8, CHARGE_MAX_TICKS, 0));
        assertNull(chargeEnd(false, 2, 8, 10, 0));
        assertTrue(CHARGE_MAX_TICKS <= 60, "a brief rush, not a chase");
    }

    @Test
    void escapesOnlyAfterFleeingLongEnoughFarFromEveryoneAndUnseen() {
        double far = ESCAPE_MIN_PLAYER_DISTANCE;
        assertTrue(mayEscape(true, ESCAPE_MIN_FLEE_TICKS, far, false));
        assertTrue(mayEscape(true, ESCAPE_MIN_FLEE_TICKS, Double.POSITIVE_INFINITY, false), "no players at all");
        assertFalse(mayEscape(false, 1000, far, false), "only while fleeing");
        assertFalse(mayEscape(true, ESCAPE_MIN_FLEE_TICKS - 1, far, false), "not before 5 s");
        assertFalse(mayEscape(true, 1000, far - 0.1, false), "not while any player is within 48 blocks");
        assertFalse(mayEscape(true, 1000, 500, true), "never while any player can see it");
        assertEquals(100, ESCAPE_MIN_FLEE_TICKS);
        assertEquals(48.0, ESCAPE_MIN_PLAYER_DISTANCE);
    }
}

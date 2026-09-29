package zcylas.totality.entity.vehicle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The skateboard's ride physics, tick by tick (SkateboardMotion, used by SkateboardEntity on its authoritative side). */
class SkateboardMotionTest {

    private static final float GROUND = 0.6F;
    private static final float ICE = 0.98F;

    private static double ticks(double speed, float forward, int n, float slipperiness) {
        for (int i = 0; i < n; i++) speed = SkateboardMotion.speed(speed, forward, true, slipperiness);
        return speed;
    }

    @Test
    void pushingAcceleratesTowardsTopSpeedAndNoFurther() {
        double one = ticks(0.0, 1.0F, 1, GROUND);
        assertTrue(one > 0.0 && one <= SkateboardMotion.PUSH_ACCELERATION, "the first push is a gentle start");
        double second = ticks(0.0, 1.0F, 2, GROUND);
        assertTrue(second > one, "pushing keeps adding speed");
        double longPush = ticks(0.0, 1.0F, 400, GROUND);
        assertTrue(longPush <= SkateboardMotion.MAX_PUSH_SPEED + 1e-9, "never beyond top speed by pushing");
        assertTrue(longPush > SkateboardMotion.MAX_PUSH_SPEED * 0.9, "reaches near top speed");
        int toTop = 0;
        for (double s = 0.0; s < SkateboardMotion.MAX_PUSH_SPEED * 0.9; toTop++) s = SkateboardMotion.speed(s, 1.0F, true, GROUND);
        assertTrue(toTop > 25 && toTop < 120, "90% of top speed takes a noticeable while: " + toTop + " ticks");
    }

    @Test
    void pushingNeverSlowsAFasterBoard() {
        double fast = SkateboardMotion.MAX_PUSH_SPEED * 1.5;
        assertTrue(SkateboardMotion.speed(fast, 1.0F, true, GROUND) > SkateboardMotion.MAX_PUSH_SPEED, "only rolling resistance");
    }

    @Test
    void brakingStopsGraduallyWithoutReversing() {
        double s = SkateboardMotion.MAX_PUSH_SPEED;
        int n = 0;
        while (s > 0.0) {
            double next = SkateboardMotion.speed(s, -1.0F, true, GROUND);
            assertTrue(next < s && next >= 0.0);
            s = next;
            n++;
        }
        assertTrue(n >= 10 && n <= 30, "a gradual stop from top speed: " + n + " ticks");
        assertEquals(0.0, SkateboardMotion.speed(0.0, -1.0F, true, GROUND), "braking at rest does not reverse");
    }

    @Test
    void coastingKeepsMomentumAndSlowsGradually() {
        double s = SkateboardMotion.MAX_PUSH_SPEED;
        double afterTwoSeconds = ticks(s, 0.0F, 40, GROUND);
        assertTrue(afterTwoSeconds < s && afterTwoSeconds > s * 0.6, "momentum: " + afterTwoSeconds);
        assertTrue(ticks(s, 0.0F, 40, ICE) > afterTwoSeconds, "ice rolls on further");
    }

    @Test
    void steeringTurnsBothWaysAndKickTurnsWhenStopped() {
        float left = 0.0F;
        float right = 0.0F;
        float stopped = 0.0F;
        for (int i = 0; i < 30; i++) {
            left = SkateboardMotion.turnRate(left, 1.0F, 0.2, true);
            right = SkateboardMotion.turnRate(right, -1.0F, 0.2, true);
            stopped = SkateboardMotion.turnRate(stopped, 1.0F, 0.0, true);
        }
        assertEquals(-SkateboardMotion.MAX_TURN_RATE, left, 0.01, "left input turns left (yaw decreasing), as boats");
        assertEquals(SkateboardMotion.MAX_TURN_RATE, right, 0.01);
        assertEquals(-SkateboardMotion.KICK_TURN_RATE, stopped, 0.01, "a slower kick-turn when stopped");
        float eased = SkateboardMotion.turnRate(0.0F, 1.0F, 0.2, true);
        assertTrue(eased < 0.0F && eased > -SkateboardMotion.MAX_TURN_RATE, "the turn eases in");
    }

    @Test
    void noPushBrakeOrSteerInTheAir() {
        assertEquals(0.2 * SkateboardMotion.AIR_DRAG, SkateboardMotion.speed(0.2, 1.0F, false, GROUND), 1e-9);
        assertEquals(0.2 * SkateboardMotion.AIR_DRAG, SkateboardMotion.speed(0.2, -1.0F, false, GROUND), 1e-9);
        assertEquals(0.0F, SkateboardMotion.turnRate(0.0F, 1.0F, 0.2, false));
    }

    @Test
    void wheelsGripSideways() {
        assertEquals(0.1 * SkateboardMotion.SIDE_GRIP, SkateboardMotion.sideSpeed(0.1, true), 1e-9);
        assertTrue(SkateboardMotion.sideSpeed(0.1, false) > SkateboardMotion.sideSpeed(0.1, true), "no grip in the air");
    }

    @Test
    void leanFollowsTheTurnWithinLimits() {
        assertEquals(0.0F, SkateboardMotion.lean(0.0F));
        assertTrue(SkateboardMotion.lean(3.0F) > 0.0F && SkateboardMotion.lean(-3.0F) < 0.0F);
        assertEquals(12.0F, SkateboardMotion.lean(50.0F));
        assertEquals(-12.0F, SkateboardMotion.lean(-50.0F));
    }
}

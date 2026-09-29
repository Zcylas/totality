package zcylas.totality.client.entity.forestboar;

import org.junit.jupiter.api.Test;
import zcylas.totality.entity.animal.ForestBoarEntity.Behavior;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Forest Boar's animation bookkeeping: the gait advances with the ground actually covered (so the planted hooves
 * do not slide or run backwards), and each behaviour selects its layer without the generic idle/walk overriding it.
 */
class ForestBoarMotionTest {

    private static final float WALK = 0.5557F;
    private static final float RUN = 1.8501F;

    /** Moves along +z (yaw 0 faces +z) at {@code perTick} blocks per tick for {@code ticks} ticks. */
    private static float walk(ForestBoarMotion m, Behavior b, double[] z, float[] age, double perTick, float yaw, int ticks) {
        for (int i = 0; i < ticks; i++) {
            z[0] += perTick;
            age[0] += 1.0F;
            m.update(0.0, z[0], yaw, age[0], b);
        }
        return m.phase;
    }

    @Test
    void theGaitAdvancesByDistanceOverStrideForwardAndBackward() {
        ForestBoarMotion m = new ForestBoarMotion(WALK, RUN);
        double[] z = {0};
        float[] age = {0};
        m.update(0, 0, 0, 0, Behavior.WANDER);
        walk(m, Behavior.WANDER, z, age, 0.05, 0.0F, 5);              // 0.25 blocks forward
        assertEquals(0.25 / WALK, m.phase, 1e-4, "cycles = distance / stride");
        float before = m.phase;
        walk(m, Behavior.WANDER, z, age, -0.05, 0.0F, 2);             // pushed back 0.1 blocks
        assertEquals(before - 0.1 / WALK, m.phase, 1e-4, "moving backwards runs the gait backwards (no moonwalk)");
    }

    @Test
    void walkingOffTheFacingDirectionDoesNotAdvanceTheGait() {
        ForestBoarMotion m = new ForestBoarMotion(WALK, RUN);
        double[] z = {0};
        float[] age = {0};
        m.update(0, 0, 90.0F, 0, Behavior.WANDER);                     // yaw 90 faces -x; moving along z is sideways
        walk(m, Behavior.WANDER, z, age, 0.05, 90.0F, 5);
        assertEquals(0.0, m.phase, 1e-4);
    }

    @Test
    void fleeAndChargeRunEverythingElseWalks() {
        ForestBoarMotion m = new ForestBoarMotion(WALK, RUN);
        double[] z = {0};
        float[] age = {0};
        m.update(0, 0, 0, 0, Behavior.WANDER);
        walk(m, Behavior.WANDER, z, age, 0.06, 0.0F, 20);
        assertTrue(m.walkWeight() > 0.95F && m.runWeight() < 0.05F, "walking");
        walk(m, Behavior.FLEE, z, age, 0.2, 0.0F, 30);
        assertTrue(m.runWeight() > 0.95F && m.walkWeight() < 0.05F, "fleeing runs");
        walk(m, Behavior.CHARGE, z, age, 0.25, 0.0F, 10);
        assertTrue(m.runWeight() > 0.95F && m.charge > 0.9F, "charging runs with the charge posture");
        float p = m.phase;
        walk(m, Behavior.FLEE, z, age, 0.2, 0.0F, 1);
        assertEquals(p + 0.2 / RUN, m.phase, 0.02, "the run stride once running");
    }

    @Test
    void standingStillFadesTheGaitAndPeacefulStandingPlaysTheIdle() {
        ForestBoarMotion m = new ForestBoarMotion(WALK, RUN);
        double[] z = {0};
        float[] age = {0};
        m.update(0, 0, 0, 0, Behavior.WANDER);
        walk(m, Behavior.WANDER, z, age, 0.06, 0.0F, 20);
        walk(m, Behavior.WANDER, z, age, 0.0, 0.0F, 20);
        assertTrue(m.walkWeight() < 0.01F && m.idleWeight() > 0.95F, "walk -> idle");
    }

    @Test
    void stateAnimationsAreNotOverriddenByTheGenericIdle() {
        ForestBoarMotion m = new ForestBoarMotion(WALK, RUN);
        double[] z = {0};
        float[] age = {0};
        m.update(0, 0, 0, 0, Behavior.WANDER);
        walk(m, Behavior.WANDER, z, age, 0.0, 0.0F, 30);
        walk(m, Behavior.GRAZE, z, age, 0.0, 0.0F, 40);
        assertTrue(m.graze > 0.95F && m.idleWeight() < 0.05F && m.lookWeight() < 0.05F, "grazing owns the head");
        walk(m, Behavior.ALERT, z, age, 0.0, 0.0F, 4);
        assertTrue(m.alert > 0.95F, "the startle is immediate");
        assertTrue(m.idleWeight() < 0.05F);
        assertEquals(0, ForestBoarMotion.millisSince(m.alertSince, m.alertSince));
        assertEquals(150, ForestBoarMotion.millisSince(m.alertSince, age[0]), "the alert plays from its start (3 ticks)");
        walk(m, Behavior.CHARGE_WINDUP, z, age, 0.0, 0.0F, 10);
        assertTrue(m.windup > 0.95F && m.alert < 0.3F, "alert hands over to the wind-up");
    }

    @Test
    void aTeleportResetsInsteadOfSpinningTheLegs() {
        ForestBoarMotion m = new ForestBoarMotion(WALK, RUN);
        m.update(0, 0, 0, 0, Behavior.WANDER);
        m.update(0, 50, 0, 1, Behavior.WANDER);
        assertEquals(0.0, m.phase, 1e-6);
    }
}

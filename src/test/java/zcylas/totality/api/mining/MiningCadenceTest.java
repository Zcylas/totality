package zcylas.totality.api.mining;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Normal-swing cycle arithmetic and the stale-target cadence correction (Block Breaking V2, Pass 1). The session
 * steps are reproduced exactly as {@code PlayerMiningManager} performs them: schedule from the swing-start target,
 * then, at contact, re-derive the cycle from the carry saved before the swing and the ACTUAL target's speed.
 */
class MiningCadenceTest {

    /** {@code MiningTuning.windUpTicks(6, false, 1f)}: a 6-tick swing animation contacts at tick 3. */
    private static final int BASELINE_WINDUP = 3;

    /** The pre-extraction {@code scheduleProfiledCycle} body, verbatim, as the reference. */
    private static int[] legacyInline(float[] carry, float idealTicks, int baselineWindup) {
        carry[0] += idealTicks;
        int cycleTicks = Math.max(2, Math.round(carry[0]));
        carry[0] -= cycleTicks;
        int baselineTotal = baselineWindup + MiningTuning.RECOVERY_TICKS;
        int windUp = Math.round(cycleTicks * ((float) baselineWindup / baselineTotal));
        windUp = Math.min(Math.max(MiningTuning.MIN_WINDUP_TICKS, windUp), cycleTicks - MiningTuning.MIN_RECOVERY_TICKS);
        int ticksLeft = Math.max(1, windUp);
        int recovery = Math.max(MiningTuning.MIN_RECOVERY_TICKS, cycleTicks - ticksLeft);
        return new int[]{ticksLeft, recovery};
    }

    /** One swing: scheduled from {@code startSpeed}, struck at {@code actualSpeed}. Returns {windUp, recovery}; updates carry. */
    private static int[] swing(float[] carry, float startSpeed, float actualSpeed) {
        float before = carry[0];
        MiningCadence.Cycle scheduled = MiningCadence.profiled(before, MiningCadence.idealTicks(startSpeed), BASELINE_WINDUP);
        MiningCadence.Cycle actual = MiningCadence.profiled(before, MiningCadence.idealTicks(actualSpeed), BASELINE_WINDUP);
        carry[0] = actual.carry();
        return new int[]{scheduled.windUpTicks(), MiningCadence.correctedRecovery(scheduled.windUpTicks(), actual.cycleTicks())};
    }

    @Test
    void extractedProfiledCycleIsIdenticalToThePreviousInlineScheduler() {
        float[] speeds = {0.3f, 1.0f, 1.25f, 1.5f, 2.0f, 2.1f, 2.25f, 2.4f, 2.5f, 3.7f, 5f, 10f};
        for (int baseline : new int[]{2, 3, 4, 5}) {
            for (float speed : speeds) {
                float[] ref = {0f};
                float carry = 0f;
                for (int i = 0; i < 40; i++) {
                    int[] expected = legacyInline(ref, MiningCadence.idealTicks(speed), baseline);
                    MiningCadence.Cycle c = MiningCadence.profiled(carry, MiningCadence.idealTicks(speed), baseline);
                    carry = c.carry();
                    assertEquals(expected[0], c.windUpTicks(), "windUp speed=" + speed + " baseline=" + baseline + " i=" + i);
                    assertEquals(expected[1], c.recoveryTicks(), "recovery speed=" + speed + " baseline=" + baseline + " i=" + i);
                    assertEquals(ref[0], carry, "carry");
                }
            }
        }
    }

    @Test
    void slowToFastTargetKeepsTheContactMomentAndShortensRecovery() {
        // Netherite Pickaxe (2.5/s) scheduled on Grass (wrong tool x0.5 = 1.25/s: 16 ticks), strikes Stone (8 ticks).
        float[] carry = {0f};
        int[] s = swing(carry, 1.25f, 2.5f);
        assertEquals(5, s[0], "wind-up (contact moment) is the originally scheduled one");
        assertEquals(3, s[1], "recovery re-derived: 8-tick Stone cycle - 5 ticks already spent");
        assertEquals(8, s[0] + s[1], "the swing lasts the actual target's cycle, not the stale 16");
    }

    @Test
    void fastToSlowTargetKeepsTheContactMomentAndLengthensRecovery() {
        float[] carry = {0f};
        int[] s = swing(carry, 2.5f, 1.25f);
        assertEquals(2, s[0], "Stone-scheduled wind-up");
        assertEquals(14, s[1], "16-tick Grass cycle - 2 ticks spent");
        assertEquals(16, s[0] + s[1], "no fast wrong-tool contact slips through on the stale Stone cycle");
    }

    @Test
    void unchangedTargetProducesExactlyTheScheduledRecovery() {
        for (float speed : new float[]{1.25f, 2.25f, 2.4f, 2.5f, 10f}) {
            float carry = 0f;
            for (int i = 0; i < 20; i++) {
                MiningCadence.Cycle c = MiningCadence.profiled(carry, MiningCadence.idealTicks(speed), BASELINE_WINDUP);
                float[] c2 = {carry};
                int[] corrected = swing(c2, speed, speed);
                assertEquals(c.recoveryTicks(), corrected[1], "speed " + speed);
                assertEquals(c.carry(), c2[0], "carry identical");
                carry = c.carry();
            }
        }
    }

    @Test
    void minimumRecoveryIsPreservedWhenTheWindUpAlreadyExceedsTheActualCycle() {
        float[] carry = {0f};
        int[] s = swing(carry, 0.5f, 10f);          // 40-tick schedule (wind-up 12), actual 2-tick cap
        assertEquals(12, s[0]);
        assertEquals(MiningTuning.MIN_RECOVERY_TICKS, s[1], "never below the minimum recovery");
        assertEquals(1, MiningCadence.correctedRecovery(5, 3));
    }

    @Test
    void fractionalCadenceStaysExactAcrossRetargetsBecauseTheCarryIsRewound() {
        // Iron Pickaxe 2.25/s alternating between a matching target and a wrong-tool one (x0.5 = 1.125/s),
        // with the swing ALWAYS scheduled from the other target (worst case for staleness).
        float[] carry = {0f};
        double ideal = 0;
        int actualTicks = 0;
        for (int i = 0; i < 60; i++) {
            boolean matching = i % 2 == 0;
            float actual = matching ? 2.25f : 1.125f;
            float stale = matching ? 1.125f : 2.25f;
            int[] s = swing(carry, stale, actual);
            actualTicks += s[0] + s[1];
            ideal += 20.0 / actual;
        }
        assertEquals(ideal, actualTicks, 1.0, "total swing time tracks the actual targets' ideal time within one tick");
    }
}

package zcylas.totality.api.mining;

/**
 * Pure normal-swing cycle arithmetic, shared by swing start and the contact-frame correction in
 * {@link PlayerMiningManager} (the {@code MiningTuning} constants used here are compile-time constants, so this
 * class stays unit-testable).
 */
public final class MiningCadence {

    private MiningCadence() {}

    /**
     * One profiled cycle.
     *
     * @param carry fractional cycle-length remainder to keep on the session afterwards
     */
    public record Cycle(int cycleTicks, int windUpTicks, int recoveryTicks, float carry) {}

    /** Ideal (fractional) cycle length in ticks for a usable Mining Speed in impacts per second. */
    public static float idealTicks(float miningSpeed) {
        return 20f / Math.max(0.0001f, miningSpeed);
    }

    /**
     * Profiled normal-swing timing: the carry accumulator keeps fractional speeds (2.25/s, 2.4/s...) averaging
     * correctly over many cycles, and the windUp/recovery split keeps the swing-duration-based proportion.
     *
     * @param baselineWindUp {@code MiningTuning.windUpTicks(duration, false, 1f)} of the held item
     */
    public static Cycle profiled(float carry, float idealTicks, int baselineWindUp) {
        carry += idealTicks;
        int cycleTicks = Math.max(2, Math.round(carry));
        carry -= cycleTicks;

        int baselineTotal = baselineWindUp + MiningTuning.RECOVERY_TICKS;
        int windUp = Math.round(cycleTicks * ((float) baselineWindUp / baselineTotal));
        windUp = Math.min(Math.max(MiningTuning.MIN_WINDUP_TICKS, windUp), cycleTicks - MiningTuning.MIN_RECOVERY_TICKS);
        windUp = Math.max(1, windUp);
        return new Cycle(cycleTicks, windUp, correctedRecovery(windUp, cycleTicks), carry);
    }

    /**
     * Recovery that makes a swing whose wind-up already ran for {@code windUpSpent} ticks last {@code cycleTicks}
     * in total, never below the minimum recovery.
     */
    public static int correctedRecovery(int windUpSpent, int cycleTicks) {
        return Math.max(MiningTuning.MIN_RECOVERY_TICKS, cycleTicks - windUpSpent);
    }
}

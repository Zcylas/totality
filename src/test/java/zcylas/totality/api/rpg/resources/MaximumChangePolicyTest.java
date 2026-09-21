package zcylas.totality.api.rpg.resources;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-formula coverage for {@link MaximumChangePolicy#reconcileCurrent} — the exact numeric
 * examples from the Phase 6 Standard Spell Slot level-up regression report (2026-09-16 correction
 * pass), independent of any resource/player wiring. Real-production-path coverage of the same
 * scenarios, through the actual {@code ClassChangeReconciler}/{@code totality:spell_slots} pipeline,
 * lives in {@code StandardSpellSlotMigrationVerification} (dev-server, real {@code ServerPlayer}).
 */
class MaximumChangePolicyTest {

    private static final long FLOOR = 0L;

    @Test
    void preserveDeficitGrantsANewlyUnlockedTierImmediately() {
        // old max=0, current=0, new max=1 -> expected current=1.
        long reconciled = MaximumChangePolicy.PRESERVE_DEFICIT.reconcileCurrent(0, 0, 1, FLOOR);
        assertEquals(1, reconciled);
    }

    @Test
    void preserveDeficitGrantsTheGainedDifferenceWhilePartiallySpent() {
        // old max=3, current=1 (2 spent), new max=4 -> expected current=2, still 2 spent.
        long reconciled = MaximumChangePolicy.PRESERVE_DEFICIT.reconcileCurrent(1, 3, 4, FLOOR);
        assertEquals(2, reconciled);
    }

    @Test
    void preserveDeficitClampsAShrinkingMaximumWhilePreservingSpentCapacity() {
        // old max=4, current=2 (2 spent), new max=3 -> expected current=1, still 2 spent (clamped).
        long reconciled = MaximumChangePolicy.PRESERVE_DEFICIT.reconcileCurrent(2, 4, 3, FLOOR);
        assertEquals(1, reconciled);
    }

    @Test
    void preserveDeficitGrantsOnlyTheNewlyGainedCapacityWhenFullySpent() {
        // old max=3, current=0 (fully spent), new max=4 -> expected current=1 (only the +1 gained is available).
        long reconciled = MaximumChangePolicy.PRESERVE_DEFICIT.reconcileCurrent(0, 3, 4, FLOOR);
        assertEquals(1, reconciled);
    }

    @Test
    void preserveDeficitNeverGoesBelowFloorOrAboveNewMaximum() {
        assertEquals(FLOOR, MaximumChangePolicy.PRESERVE_DEFICIT.reconcileCurrent(0, 100, 5, FLOOR));
        assertEquals(5, MaximumChangePolicy.PRESERVE_DEFICIT.reconcileCurrent(50, 0, 5, FLOOR));
    }

    @Test
    void clampCurrentIgnoresPreviousMaximumAndNeverGrowsOnItsOwn() {
        // The pre-correction-pass default behavior: growth never auto-grants, shrink always clamps.
        assertEquals(2, MaximumChangePolicy.CLAMP_CURRENT.reconcileCurrent(2, 3, 4, FLOOR)); // grew: current untouched
        assertEquals(3, MaximumChangePolicy.CLAMP_CURRENT.reconcileCurrent(4, 4, 3, FLOOR)); // shrank: clamped down
    }

    @Test
    void degeneratePreviousMaxEqualToNewMaxIsANoOpForEveryPolicyUnlessAlreadyInvalid() {
        // "No real before captured" (ClassChangeReconciler's no-arg reconcile default) sets
        // previousMaximumUnits == newMaximumUnits — every policy must leave a valid current
        // untouched, and only clamp down a current that is already above the maximum.
        for (MaximumChangePolicy policy : MaximumChangePolicy.values()) {
            if (policy == MaximumChangePolicy.ALLOW_OVERFLOW) continue; // current is a caller responsibility for this policy
            assertEquals(2, policy.reconcileCurrent(2, 5, 5, FLOOR), policy + " must not move an already-valid current");
            assertEquals(5, policy.reconcileCurrent(9, 5, 5, FLOOR), policy + " must still clamp an over-maximum current");
        }
    }

    @Test
    void allowOverflowLeavesCurrentUntouched() {
        assertEquals(9, MaximumChangePolicy.ALLOW_OVERFLOW.reconcileCurrent(9, 5, 5, FLOOR));
    }

    // ── Cleanup pass (2026-09-16) — maximum-only sync edge ──────────────────────────────────
    // These prove the formula-level precondition that makes ClassChangeReconciler.reconcilePartitioned's
    // maximum-only sync fix reachable: a genuine maximum change (previousMaximumUnits != newMaximumUnits)
    // that nonetheless reconciles to the SAME current value, meaning delta == 0 and no drain/restore
    // call would run — the exact edge that used to leave nothing marking the resource dirty. The
    // sync-marking fix itself is proven by ClassChangeReconcilerMaximumSyncSourceRegressionTest and
    // StandardSpellSlotMigrationVerification, since MaximumChangePolicy has no knowledge of sync at all.

    @Test
    void clampCurrentLeavesCurrentUnchangedWhenAlreadyBelowBothMaxima() {
        // The task's own example: old max=4, current=2, new max=5, CLAMP_CURRENT -> current stays 2.
        long reconciled = MaximumChangePolicy.CLAMP_CURRENT.reconcileCurrent(2, 4, 5, FLOOR);
        assertEquals(2, reconciled, "current must not move merely because the maximum grew under CLAMP_CURRENT");
    }

    @Test
    void preserveDeficitLeavesAnAlreadyEmptyPartitionAtZeroWhenTheMaximumShrinksFurther() {
        // A fully-spent partition (current=0) whose maximum shrinks so much that the deficit-preserving
        // target would go negative clamps to the floor (0) — the same value current already was, so
        // this is a genuine maximum change (5 -> 3) with zero reconciled delta.
        long reconciled = MaximumChangePolicy.PRESERVE_DEFICIT.reconcileCurrent(0, 5, 3, FLOOR);
        assertEquals(0, reconciled, "an already-empty partition must stay at the floor, not go negative or refill");
    }
}

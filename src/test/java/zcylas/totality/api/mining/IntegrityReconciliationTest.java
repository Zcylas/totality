package zcylas.totality.api.mining;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.mining.BlockProfile.Classification;
import zcylas.totality.api.mining.IntegrityReconciliation.Action;

import static org.junit.jupiter.api.Assertions.*;

/** Saved-record reconciliation: transitions, classification boundary, old-save migration (Block Breaking V2, Pass 1). */
class IntegrityReconciliationTest {

    private static final Classification ORDINARY = Classification.ORDINARY;

    private static IntegrityReconciliation.Decision sameBlock(float integrity, float savedMax, float currentMax) {
        return IntegrityReconciliation.reconcile(integrity, savedMax, true, ORDINARY, currentMax);
    }

    // ── preservation ─────────────────────────────────────────────────────────────────────────

    @Test
    void ordinaryStateChangeOfTheSameBlockKeepsTheRecordUntouched() {
        assertEquals(Action.KEEP, sameBlock(40f, 100f, 100f).action());
        assertEquals(Action.KEEP, sameBlock(0.001f, 100f, 100f).action(), "a denied-break record survives as saved");
    }

    // ── percentage-preserving migration ─────────────────────────────────────────────────────

    @Test
    void changedMaximumRescalesByPercentageWithTheSpecifiedFormula() {
        var d = sameBlock(40f, 100f, 250f);                  // 40% -> round(250 x 40 / 100) = 100
        assertEquals(Action.RESCALE, d.action());
        assertEquals(100f, d.integrity());
        assertEquals(250f, d.max());

        var down = sameBlock(40f, 100f, 50f);                // round(50 x 0.4) = 20
        assertEquals(20f, down.integrity());
        assertEquals(50f, down.max());

        assertEquals(Math.round(333f * 57f / 100f), sameBlock(57f, 100f, 333f).integrity(), "rounded");
    }

    @Test
    void rescaleClampsSoADamagedBlockNeverBecomesAStandingZeroIntegrityBlock() {
        assertEquals(1f, IntegrityReconciliation.rescale(0.001f, 100f, 300f), "tiny remainder rounds to 0 -> clamped to 1");
        assertEquals(0.5f, IntegrityReconciliation.rescale(10f, 100f, 0.5f), "new max below 1: clamped to the new max");
        assertEquals(80f, IntegrityReconciliation.rescale(120f, 100f, 80f), "never above the new max");
    }

    @Test
    void rescaleThatRoundsBackToFullDeletesTheRecord() {
        assertEquals(Action.DELETE, sameBlock(99.9f, 100f, 10f).action(), "round(10 x .999) = 10 = full");
    }

    @Test
    void authoredTransformationOfTheLiveBlockMigratesByPercentage() {
        var d = IntegrityReconciliation.transform(25f, 100f, true, true, ORDINARY, 200f);
        assertEquals(Action.RESCALE, d.action());
        assertEquals(50f, d.integrity());
        assertEquals(200f, d.max());
        var sameMax = IntegrityReconciliation.transform(25f, 100f, true, true, ORDINARY, 100f);
        assertEquals(Action.RESCALE, sameMax.action(), "a transformation always re-identifies the record");
        assertEquals(25f, sameMax.integrity());
    }

    @Test
    void unrelatedReplacementNeverInheritsTheRecord() {
        assertEquals(Action.DELETE, IntegrityReconciliation.reconcile(25f, 100f, false, ORDINARY, 100f).action());
        assertEquals(Action.DELETE, IntegrityReconciliation.transform(25f, 100f, true, false, ORDINARY, 100f).action(),
                "a transformation call for a pair that is not authored is a replacement");
    }

    // ── follow-up: removal followed by replacement with an authored pair ────────────────────

    @Test
    void lazyReadNeverTransfersAcrossBlockIdsEvenForAnAuthoredPair() {
        // Stone (damaged) was destroyed and Deepslate placed later; Stone -> Deepslate IS authored. The lazy read has
        // no way to tell this from a real transformation, so it has no transformation input at all and deletes.
        var d = IntegrityReconciliation.reconcile(60f, 100f, false, ORDINARY, 200f);
        assertEquals(Action.DELETE, d.action());
    }

    @Test
    void transformationCallAfterARemovalDoesNotMigrateAStaleRecord() {
        // The record is still Stone's, but the block standing there now is not Stone (it was removed, possibly
        // replaced); a transformation call on whatever stands there must not revive Stone's damage.
        var d = IntegrityReconciliation.transform(60f, 100f, false, true, ORDINARY, 200f);
        assertEquals(Action.DELETE, d.action());
    }

    @Test
    void transformationIntoANonOrdinaryOrInvalidBlockDeletes() {
        assertEquals(Action.DELETE, IntegrityReconciliation.transform(60f, 100f, true, true, Classification.SPECIAL, 200f).action());
        assertEquals(Action.DELETE, IntegrityReconciliation.transform(60f, 0f, true, true, ORDINARY, 200f).action());
        assertEquals(Action.DELETE, IntegrityReconciliation.transform(60f, 100f, true, true, ORDINARY, 0f).action());
    }

    // ── classification boundary ─────────────────────────────────────────────────────────────

    @Test
    void becomingSpecialUnbreakableOrNotApplicableDeletesTheFiniteRecord() {
        for (Classification c : new Classification[]{Classification.SPECIAL, Classification.UNBREAKABLE, Classification.NOT_APPLICABLE}) {
            assertEquals(Action.DELETE, IntegrityReconciliation.reconcile(40f, 100f, true, c, 100f).action(), c.name());
            assertEquals(Action.DELETE, IntegrityReconciliation.transform(40f, 100f, true, true, c, 100f).action(),
                    c + " even through an authored transformation");
        }
    }

    @Test
    void noDormantDamageSpecialThenOrdinaryAgainStartsAtFull() {
        // Ordinary(40/100) -> SPECIAL: the record is deleted, not parked...
        assertEquals(Action.DELETE, IntegrityReconciliation.reconcile(40f, 100f, true, Classification.SPECIAL, 100f).action());
        // ...so when the block is ordinary again there is no record left: it reads as a fresh, full block.
    }

    // ── invalid saved data ──────────────────────────────────────────────────────────────────

    @Test
    void invalidOrZeroOldMaximumIsHandledSafelyByResettingToFull() {
        for (float bad : new float[]{0f, -1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertEquals(Action.DELETE, sameBlock(40f, bad, 100f).action(), "old max " + bad);
        }
        assertEquals(Action.DELETE, sameBlock(Float.NaN, 100f, 100f).action(), "corrupt integrity");
    }

    @Test
    void zeroOrInvalidCurrentMaximumHoldsNoRecord() {
        assertEquals(Action.DELETE, sameBlock(40f, 100f, 0f).action());
        assertEquals(Action.DELETE, sameBlock(40f, 100f, -1f).action());
    }

    // ── shared-owner fold (old saves) ───────────────────────────────────────────────────────

    @Test
    void legacyPartnerRecordFoldsIntoTheOwnerMoreDamagedWins() {
        // Confirmed rule: the more damaged RECOVERED percentage wins; records are never summed.
        assertFalse(IntegrityReconciliation.memberWinsFold(0.6f, 0.7f), "owner 40% damaged vs member 30%: owner kept, not 70% summed");
        assertTrue(IntegrityReconciliation.memberWinsFold(Float.NaN, 0.4f), "owner had no record: take the member's");
        assertTrue(IntegrityReconciliation.memberWinsFold(0.9f, 0.4f), "member more damaged");
        assertFalse(IntegrityReconciliation.memberWinsFold(0.3f, 0.4f), "owner more damaged: keep it");
        assertFalse(IntegrityReconciliation.memberWinsFold(0.5f, Float.NaN), "corrupt member never wins");
    }
}

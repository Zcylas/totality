package zcylas.totality.api.rpg.resources.client.parity;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Covers test-plan items 19-25: the Rage absence/expectation policy, plus the external-review
 *  correction 1 exact sparse-0/0 representation tests. */
class ClientRageParityPolicyTest {

    private static final ClientResourceParitySummary.Unavailable NOT_AVAILABLE =
            new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.NOT_AVAILABLE_TO_PLAYER);
    private static final ClientResourceParitySummary.Unavailable NOT_SYNCHRONIZED =
            new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET);

    private static ClientResourceParitySummary.Scalar scalar(long current, long maximum) {
        return new ClientResourceParitySummary.Scalar(current, maximum, 0, 1);
    }

    @Test
    void nonBarbarianAbsentRageVersusLegacyZeroZeroIsExpectedSemanticDifference() {
        var outcome = ClientRageParityPolicy.compare(false, NOT_AVAILABLE, scalar(0, 0));
        assertEquals(ClientResourceParityOutcome.EXPECTED_SEMANTIC_DIFFERENCE, outcome);
    }

    @Test
    void nonBarbarianAbsentRageVersusLegacyNonzeroIsMismatch() {
        var outcome = ClientRageParityPolicy.compare(false, NOT_AVAILABLE, scalar(1, 2));
        assertEquals(ClientResourceParityOutcome.MISMATCH, outcome);
    }

    @Test
    void expectedRageAbsentVersusLegacyZeroZeroIsMismatch() {
        // Rage IS expected for this player (e.g. a Barbarian), yet the generic side reports none
        // granted — always a real gap, regardless of the legacy value.
        var outcome = ClientRageParityPolicy.compare(true, NOT_AVAILABLE, scalar(0, 0));
        assertEquals(ClientResourceParityOutcome.MISMATCH, outcome);
    }

    @Test
    void expectedRageAbsentVersusLegacyNonzeroIsMismatch() {
        var outcome = ClientRageParityPolicy.compare(true, NOT_AVAILABLE, scalar(2, 2));
        assertEquals(ClientResourceParityOutcome.MISMATCH, outcome);
    }

    @Test
    void presentRageZeroZeroVersusLegacyZeroZeroIsExactMatch() {
        var outcome = ClientRageParityPolicy.compare(true, scalar(0, 0), scalar(0, 0));
        assertEquals(ClientResourceParityOutcome.MATCH, outcome);
    }

    @Test
    void presentRageNonzeroExactMatch() {
        var outcome = ClientRageParityPolicy.compare(true, scalar(1, 2), scalar(1, 2));
        assertEquals(ClientResourceParityOutcome.MATCH, outcome);
    }

    @Test
    void presentRageCurrentMismatch() {
        var outcome = ClientRageParityPolicy.compare(true, scalar(2, 2), scalar(1, 2));
        assertEquals(ClientResourceParityOutcome.MISMATCH, outcome);
    }

    @Test
    void presentRageMaximumMismatch() {
        var outcome = ClientRageParityPolicy.compare(true, scalar(1, 3), scalar(1, 2));
        assertEquals(ClientResourceParityOutcome.MISMATCH, outcome);
    }

    @Test
    void presentRageNonzeroVersusLegacyZeroZeroIsMismatch() {
        var outcome = ClientRageParityPolicy.compare(true, scalar(1, 2), scalar(0, 0));
        assertEquals(ClientResourceParityOutcome.MISMATCH, outcome);
    }

    @Test
    void genericNotSynchronizedYetBecomesGenericNotReadyRegardlessOfExpectation() {
        assertEquals(ClientResourceParityOutcome.GENERIC_NOT_READY,
                ClientRageParityPolicy.compare(true, NOT_SYNCHRONIZED, scalar(0, 0)));
        assertEquals(ClientResourceParityOutcome.GENERIC_NOT_READY,
                ClientRageParityPolicy.compare(false, NOT_SYNCHRONIZED, scalar(0, 0)));
    }

    @Test
    void otherUnavailableReasonBecomesModelMismatch() {
        var malformed = new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE);
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH,
                ClientRageParityPolicy.compare(true, malformed, scalar(0, 0)));
    }

    // ── External-review correction 1: exact sparse Rage 0/0 representation requirements ────────

    @Test
    void nonBarbarianAbsentRageWithLegacyWrongScaleIsNotExpected() {
        // Legacy reads 0/0 numerically, but at a non-canonical unit scale — must not be suppressed
        // as the expected sparse-map representation; it is a real, ordinary mismatch instead.
        var legacyWrongScale = new ClientResourceParitySummary.Scalar(0, 0, 0, 1000);
        var outcome = ClientRageParityPolicy.compare(false, NOT_AVAILABLE, legacyWrongScale);
        assertEquals(ClientResourceParityOutcome.MISMATCH, outcome);
    }

    @Test
    void nonBarbarianAbsentRageWithLegacyOverflowIsNotExpected() {
        var legacyWithOverflow = new ClientResourceParitySummary.Scalar(0, 0, 3, 1);
        var outcome = ClientRageParityPolicy.compare(false, NOT_AVAILABLE, legacyWithOverflow);
        assertEquals(ClientResourceParityOutcome.MISMATCH, outcome);
    }
}

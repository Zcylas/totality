package zcylas.totality.api.rpg.resources.client.parity;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers test-plan items 1-4, 6-7 (scalar exact match, current/maximum mismatch, overflow-unsupported
 * MODEL_MISMATCH, generic MODEL_MISMATCH pass-through, scalar/partition shape contradiction) plus the
 * external-review correction 1 unit-scale/overflow validation tests.
 */
class ClientResourceScalarParityComparatorTest {

    private static ClientResourceParitySummary.Scalar scalar(long current, long maximum) {
        return new ClientResourceParitySummary.Scalar(current, maximum, 0, 1);
    }

    @Test
    void exactMatchWhenCurrentAndMaximumEqual() {
        var outcome = ClientResourceScalarParityComparator.compare(scalar(42, 100), scalar(42, 100));
        assertEquals(ClientResourceParityOutcome.MATCH, outcome);
    }

    @Test
    void currentMismatchIsOrdinaryMismatch() {
        var outcome = ClientResourceScalarParityComparator.compare(scalar(40, 100), scalar(42, 100));
        assertEquals(ClientResourceParityOutcome.MISMATCH, outcome);
    }

    @Test
    void maximumMismatchIsOrdinaryMismatch() {
        var outcome = ClientResourceScalarParityComparator.compare(scalar(40, 150), scalar(40, 100));
        assertEquals(ClientResourceParityOutcome.MISMATCH, outcome);
    }

    @Test
    void genericOverflowUnsupportedByLegacyBecomesModelMismatch() {
        var generic = new ClientResourceParitySummary.Scalar(120, 100, 20, 1);
        var outcome = ClientResourceScalarParityComparator.compare(generic, scalar(100, 100));
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH, outcome);
    }

    @Test
    void genericNotSynchronizedYetBecomesGenericNotReady() {
        var generic = new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET);
        var outcome = ClientResourceScalarParityComparator.compare(generic, scalar(100, 100));
        assertEquals(ClientResourceParityOutcome.GENERIC_NOT_READY, outcome);
    }

    @Test
    void genericModelMismatchReasonRemainsModelMismatch() {
        var generic = new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.MODEL_MISMATCH);
        var outcome = ClientResourceScalarParityComparator.compare(generic, scalar(100, 100));
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH, outcome);
    }

    @Test
    void otherUnexpectedUnavailableReasonsBecomeModelMismatch() {
        for (var reason : List.of(
                ClientResourceUnavailableReason.RESOURCE_UNREGISTERED,
                ClientResourceUnavailableReason.CLIENT_SOURCE_NOT_CONFIGURED,
                ClientResourceUnavailableReason.NOT_AVAILABLE_TO_PLAYER,
                ClientResourceUnavailableReason.NO_LOCAL_PLAYER,
                ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE)) {
            var generic = new ClientResourceParitySummary.Unavailable(reason);
            assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH,
                    ClientResourceScalarParityComparator.compare(generic, scalar(100, 100)),
                    "reason=" + reason);
        }
    }

    @Test
    void genericScalarPairedWithLegacyPartitionedIsModelMismatch() {
        var legacyPartitioned = ClientResourceParitySummary.Partitioned.of(
                List.of(new ClientResourceParitySummary.Partitioned.Partition(1, 0, 1, 0)), 1);
        var outcome = ClientResourceScalarParityComparator.compare(scalar(1, 1), legacyPartitioned);
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH, outcome);
    }

    @Test
    void genericPartitionedPairedWithLegacyScalarIsModelMismatch() {
        var genericPartitioned = ClientResourceParitySummary.Partitioned.of(
                List.of(new ClientResourceParitySummary.Partitioned.Partition(1, 0, 1, 0)), 1);
        var outcome = ClientResourceScalarParityComparator.compare(genericPartitioned, scalar(1, 1));
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH, outcome);
    }

    // ── External-review correction 1: unit-scale validation ─────────────────────────────────────

    @Test
    void differentUnitScalesBecomeModelMismatchEvenWhenNumbersAgree() {
        var generic = new ClientResourceParitySummary.Scalar(100, 100, 0, 1);
        var legacy = new ClientResourceParitySummary.Scalar(100, 100, 0, 1000);
        var outcome = ClientResourceScalarParityComparator.compare(generic, legacy);
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH, outcome);
    }

    @Test
    void bothSidesSharingTheSameNonCanonicalScaleStillBecomesModelMismatch() {
        var generic = new ClientResourceParitySummary.Scalar(100, 100, 0, 1000);
        var legacy = new ClientResourceParitySummary.Scalar(100, 100, 0, 1000);
        var outcome = ClientResourceScalarParityComparator.compare(generic, legacy);
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH, outcome);
    }

    @Test
    void legacyOverflowAloneBecomesModelMismatch() {
        var generic = scalar(100, 100);
        var legacy = new ClientResourceParitySummary.Scalar(100, 100, 5, 1);
        var outcome = ClientResourceScalarParityComparator.compare(generic, legacy);
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH, outcome);
    }

    @Test
    void canonicalUnitScaleConstantIsOne() {
        assertEquals(1L, ClientResourceScalarParityComparator.CANONICAL_UNIT_SCALE);
    }
}

package zcylas.totality.api.rpg.resources.client.parity;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.client.ClientResourceTrust;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3B-3 external-review correction: {@link ClientResourceParityReportInput} is the pure
 * Minecraft-independent conversion boundary vocabulary. Covers its own bounding invariants
 * (resource-id length, {@code AccessError} token length) and the {@code Observed} mismatch-tick
 * contract it borrows from {@code ClientResourceParityObservation}'s own established invariant.
 */
class ClientResourceParityReportInputTest {

    @Test
    void overlongResourceIdIsTruncatedToTheBound() {
        String overlong = "x".repeat(ClientResourceParityReportInput.MAX_RESOURCE_ID_LENGTH * 3);
        var input = new ClientResourceParityReportInput.Native(
                overlong, new ClientResourceParityReportInput.Native.Status.Unavailable(
                        zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason.NO_LOCAL_PLAYER));

        assertTrue(input.resourceId().length() <= ClientResourceParityReportInput.MAX_RESOURCE_ID_LENGTH);
    }

    @Test
    void blankResourceIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityReportInput.Native(
                "   ", new ClientResourceParityReportInput.Native.Status.Unavailable(
                        zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason.NO_LOCAL_PLAYER)));
    }

    @Test
    void overlongAccessErrorTokenIsTruncatedToTheBound() {
        String overlongToken = "X".repeat(ClientResourceParityReportInput.MAX_TOKEN_LENGTH * 3);
        var status = new ClientResourceParityReportInput.Native.Status.AccessError(overlongToken);
        assertTrue(status.exceptionSimpleName().length() <= ClientResourceParityReportInput.MAX_TOKEN_LENGTH);
    }

    @Test
    void shadowAccessErrorTokenIsAlsoBounded() {
        String overlongToken = "Y".repeat(ClientResourceParityReportInput.MAX_TOKEN_LENGTH * 5);
        var status = new ClientResourceParityReportInput.ShadowParity.Status.AccessError(overlongToken);
        assertTrue(status.exceptionSimpleName().length() <= ClientResourceParityReportInput.MAX_TOKEN_LENGTH);
    }

    @Test
    void nativeAvailableRejectsNegativeUnits() {
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityReportInput.Native.Status.Available(
                -1, 100, 0, 1, ClientResourceTrust.FRESH));
    }

    @Test
    void observedRequiresFirstMismatchTickNonNegativeWhenPresent() {
        var summary = new ClientResourceParitySummary.Scalar(1, 1, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                ClientResourceParityClassification.PERSISTENT_MISMATCH, summary, summary, -1L, 5));
    }

    @Test
    void observedRejectsNegativeLastObservedTick() {
        var summary = new ClientResourceParitySummary.Scalar(1, 1, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                ClientResourceParityClassification.EXACT_MATCH, summary, summary, null, -1));
    }

    @Test
    void observedWithNullFirstMismatchTickIsValidForExactMatch() {
        var summary = new ClientResourceParitySummary.Scalar(1, 1, 0, 1);
        var observed = new ClientResourceParityReportInput.ShadowParity.Status.Observed(
                ClientResourceParityClassification.EXACT_MATCH, summary, summary, null, 5);
        assertEquals(5, observed.lastObservedTick());
    }
}

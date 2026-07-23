package zcylas.totality.api.rpg.resources.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests 32-33 of the Phase 3B-1 task, at the layer that is actually pure-Java-testable: the bounded
 * diagnostic message {@code ClientResourceSyncManager} logs for a rejected {@code MALFORMED}/
 * {@code INCOMPATIBLE_SCHEMA} full or delta. {@code ClientResourceSyncManager} itself is not directly
 * unit-tested in this codebase (see {@code ClientResyncRequestGateTest}'s own Javadoc), so this
 * verifies the pure formatter in isolation: the message carries only bounded metadata (payload type,
 * schema version, revisions, apply result), never full payload contents such as resource ids or
 * scalar/partition values — and, since this formatter is a pure string-building function with no
 * reference to any {@code ClientResourceSyncState}/{@code ClientResyncRequestGate} instance, calling
 * it can never mutate synchronization state by construction.
 */
class ClientResourceSyncRejectionDiagnosticsTest {

    @Test
    void describeFullContainsOnlyBoundedMetadata() {
        String message = ClientResourceSyncRejectionDiagnostics.describeFull(
                ResourceSyncProtocol.PROTOCOL_VERSION + 1, 5L, 3L, ClientResourceSyncState.ApplyResult.INCOMPATIBLE_SCHEMA);

        assertTrue(message.contains("full snapshot"));
        assertTrue(message.contains("INCOMPATIBLE_SCHEMA"));
        assertTrue(message.contains("schemaVersion=" + (ResourceSyncProtocol.PROTOCOL_VERSION + 1)));
        assertTrue(message.contains("payloadRevision=5"));
        assertTrue(message.contains("currentRevision=3"));
        // No resource id, scalar, or partition content of any kind belongs in this message.
        assertFalse(message.contains("totality:"));
    }

    @Test
    void describeDeltaContainsOnlyBoundedMetadata() {
        String message = ClientResourceSyncRejectionDiagnostics.describeDelta(
                ResourceSyncProtocol.PROTOCOL_VERSION, 4L, 6L, 4L, ClientResourceSyncState.ApplyResult.MALFORMED);

        assertTrue(message.contains("delta"));
        assertTrue(message.contains("MALFORMED"));
        assertTrue(message.contains("baseRevision=4"));
        assertTrue(message.contains("payloadRevision=6"));
        assertTrue(message.contains("currentRevision=4"));
        assertFalse(message.contains("totality:"));
    }

    @Test
    void describeFullIsAPureFunctionOfItsArgumentsOnly() {
        // Calling the formatter twice with identical arguments must be side-effect-free and
        // deterministic — it takes no ClientResourceSyncState/ClientResyncRequestGate reference at
        // all, so it structurally cannot mutate any synchronization state.
        String first = ClientResourceSyncRejectionDiagnostics.describeFull(1, 1L, 0L, ClientResourceSyncState.ApplyResult.MALFORMED);
        String second = ClientResourceSyncRejectionDiagnostics.describeFull(1, 1L, 0L, ClientResourceSyncState.ApplyResult.MALFORMED);
        assertEquals(first, second);
    }
}

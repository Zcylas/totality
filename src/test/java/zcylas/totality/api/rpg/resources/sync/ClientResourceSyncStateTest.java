package zcylas.totality.api.rpg.resources.sync;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.networking.resource.ResourceDeltaSyncPayload;
import zcylas.totality.networking.resource.ResourceFullSyncPayload;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientResourceSyncStateTest {

    private static final Identifier MANA = Identifier.fromNamespaceAndPath("totality", "mana");
    private static final Identifier STAMINA = Identifier.fromNamespaceAndPath("totality", "stamina");

    private static ResourceScalarWireSnapshot scalar(Identifier id, long current, long max) {
        return new ResourceScalarWireSnapshot(id, 1L, current, max, 0L);
    }

    @Test
    void fullSnapshotReplacesThePreviousView() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        state.applyFull(new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(scalar(MANA, 50, 100), scalar(STAMINA, 10, 100)), List.of()));

        // A second full snapshot that omits Stamina must remove it, not merely leave it untouched.
        ClientResourceSyncState.ApplyResult result = state.applyFull(new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 2L, List.of(scalar(MANA, 60, 100)), List.of()));

        assertEquals(ClientResourceSyncState.ApplyResult.APPLIED_FULL, result);
        assertEquals(60L, state.scalar(MANA).orElseThrow().currentUnits());
        assertTrue(state.scalar(STAMINA).isEmpty(), "an entry omitted from a full replacement must become absent");
        assertEquals(2L, state.revision());
    }

    @Test
    void correctNextRevisionDeltaApplies() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 5L, List.of(scalar(MANA, 50, 100)), List.of()));

        ClientResourceSyncState.ApplyResult result = state.applyDelta(new ResourceDeltaSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 5L, 6L, List.of(scalar(MANA, 40, 100)), List.of(), List.of()));

        assertEquals(ClientResourceSyncState.ApplyResult.APPLIED_DELTA, result);
        assertEquals(40L, state.scalar(MANA).orElseThrow().currentUnits());
        assertEquals(6L, state.revision());
    }

    @Test
    void staleDeltaIsIgnoredWithoutMutatingState() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 5L, List.of(scalar(MANA, 50, 100)), List.of()));
        state.applyDelta(new ResourceDeltaSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 5L, 6L, List.of(scalar(MANA, 40, 100)), List.of(), List.of()));

        // A stale delta claiming base revision 5 again (already advanced past to 6). Its own
        // revision must still satisfy exact succession (5 + 1 = 6) to be constructible at all —
        // staleness here comes from the client already being at revision 6, not from this payload's
        // own base/revision pair being malformed.
        ClientResourceSyncState.ApplyResult result = state.applyDelta(new ResourceDeltaSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 5L, 6L, List.of(scalar(MANA, 999, 1000)), List.of(), List.of()));

        assertEquals(ClientResourceSyncState.ApplyResult.STALE_IGNORED, result);
        assertEquals(40L, state.scalar(MANA).orElseThrow().currentUnits(), "a stale delta must not mutate state");
        assertEquals(6L, state.revision());
    }

    @Test
    void revisionGapIsReportedAndNotApplied() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 5L, List.of(scalar(MANA, 50, 100)), List.of()));

        // Claims base revision 6, but the client is still at 5 — a gap (a delta was missed).
        ClientResourceSyncState.ApplyResult result = state.applyDelta(new ResourceDeltaSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 6L, 7L, List.of(scalar(MANA, 999, 1000)), List.of(), List.of()));

        assertEquals(ClientResourceSyncState.ApplyResult.REVISION_GAP, result);
        assertEquals(50L, state.scalar(MANA).orElseThrow().currentUnits(), "a revision-gap delta must not be applied");
    }

    @Test
    void deltaBeforeAnyFullSnapshotIsARevisionGap() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        ClientResourceSyncState.ApplyResult result = state.applyDelta(new ResourceDeltaSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 0L, 1L, List.of(scalar(MANA, 1, 1)), List.of(), List.of()));

        assertEquals(ClientResourceSyncState.ApplyResult.REVISION_GAP, result);
        assertFalse(state.hasSynced());
    }

    @Test
    void incompatibleSchemaFullSnapshotFailsSafelyWithoutMutation() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        ClientResourceSyncState.ApplyResult result = state.applyFull(
                new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION + 1, 1L, List.of(scalar(MANA, 1, 1)), List.of()));

        assertEquals(ClientResourceSyncState.ApplyResult.INCOMPATIBLE_SCHEMA, result);
        assertFalse(state.hasSynced());
    }

    @Test
    void incompatibleSchemaDeltaFailsSafelyWithoutMutation() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(scalar(MANA, 50, 100)), List.of()));

        ClientResourceSyncState.ApplyResult result = state.applyDelta(new ResourceDeltaSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION + 1, 1L, 2L, List.of(scalar(MANA, 1, 1)), List.of(), List.of()));

        assertEquals(ClientResourceSyncState.ApplyResult.INCOMPATIBLE_SCHEMA, result);
        assertEquals(50L, state.scalar(MANA).orElseThrow().currentUnits());
        assertEquals(1L, state.revision());
    }

    @Test
    void deltaWithRevisionNotGreaterThanBaseIsMalformedAndNotApplied() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(scalar(MANA, 50, 100)), List.of()));

        // ResourceDeltaSyncPayload's own constructor already rejects revision <= baseRevision, so a
        // malformed ordering can only reach applyDelta via a payload built through some other path
        // (e.g. a future relaxed constructor) — applyDelta defends independently regardless.
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceDeltaSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 1L, 1L, List.of(), List.of(), List.of()));
    }

    @Test
    void clearResetsToNeverSyncedState() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 3L, List.of(scalar(MANA, 50, 100)), List.of()));

        state.clear();

        assertFalse(state.hasSynced());
        assertTrue(state.scalar(MANA).isEmpty());
        // A delta arriving right after a clear (e.g. a stray in-flight packet from the old session)
        // must be treated as a gap, never silently applied against the wiped state.
        ClientResourceSyncState.ApplyResult result = state.applyDelta(new ResourceDeltaSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 3L, 4L, List.of(scalar(MANA, 1, 1)), List.of(), List.of()));
        assertEquals(ClientResourceSyncState.ApplyResult.REVISION_GAP, result);
    }

    @Test
    void deltaUpsertAndInvalidateApplyTogetherInOneBatch() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        state.applyFull(new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(scalar(MANA, 50, 100), scalar(STAMINA, 10, 100)), List.of()));

        state.applyDelta(new ResourceDeltaSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 1L, 2L, List.of(scalar(MANA, 45, 100)), List.of(), List.of(STAMINA)));

        assertEquals(45L, state.scalar(MANA).orElseThrow().currentUnits());
        assertTrue(state.scalar(STAMINA).isEmpty(), "an invalidated resource must be removed");
    }

    // ── Correction 1: stale full snapshot protection ────────────────────────────────────────────

    @Test
    void olderFullIsIgnoredAfterNewerDeltaState() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 5L, List.of(scalar(MANA, 50, 100)), List.of()));
        state.applyDelta(new ResourceDeltaSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 5L, 6L, List.of(scalar(MANA, 40, 100)), List.of(), List.of()));

        // A reordered-on-the-wire full snapshot claiming an older revision than what's already held.
        ClientResourceSyncState.ApplyResult result = state.applyFull(
                new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 4L, List.of(scalar(MANA, 999, 1000)), List.of()));

        assertEquals(ClientResourceSyncState.ApplyResult.STALE_IGNORED, result);
        assertEquals(40L, state.scalar(MANA).orElseThrow().currentUnits(), "a stale full must not mutate state at all");
        assertEquals(6L, state.revision());
    }

    @Test
    void noMutationOccursAfterAStaleFull() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        state.applyFull(new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 3L, List.of(scalar(MANA, 50, 100), scalar(STAMINA, 20, 100)), List.of()));

        state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 2L, List.of(scalar(MANA, 1, 1)), List.of()));

        assertEquals(50L, state.scalar(MANA).orElseThrow().currentUnits());
        assertEquals(20L, state.scalar(STAMINA).orElseThrow().currentUnits(), "Stamina must remain present, not dropped");
        assertEquals(3L, state.revision());
    }

    @Test
    void equalRevisionFullIsAcceptedAsIdempotentReplacement() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 4L, List.of(scalar(MANA, 50, 100)), List.of()));

        // Same revision resent (e.g. an explicit resync producing the same authoritative view).
        ClientResourceSyncState.ApplyResult result = state.applyFull(
                new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 4L, List.of(scalar(MANA, 50, 100)), List.of()));

        assertEquals(ClientResourceSyncState.ApplyResult.APPLIED_FULL, result);
        assertEquals(4L, state.revision());
    }

    @Test
    void newerFullReplacesTheViewAtomically() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        state.applyFull(new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(scalar(MANA, 50, 100), scalar(STAMINA, 10, 100)), List.of()));

        ClientResourceSyncState.ApplyResult result = state.applyFull(
                new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 9L, List.of(scalar(MANA, 70, 100)), List.of()));

        assertEquals(ClientResourceSyncState.ApplyResult.APPLIED_FULL, result);
        assertEquals(70L, state.scalar(MANA).orElseThrow().currentUnits());
        assertTrue(state.scalar(STAMINA).isEmpty(), "an entry omitted from the newer full must be gone, not partially retained");
        assertEquals(9L, state.revision());
    }

    @Test
    void rejectedFullPreservesPreviousValidStateAtomically() {
        ClientResourceSyncState state = new ClientResourceSyncState();
        state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 2L, List.of(scalar(MANA, 33, 100)), List.of()));

        // An incompatible-schema full must preserve the previous valid view entirely, atomically.
        ClientResourceSyncState.ApplyResult result = state.applyFull(
                new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION + 1, 5L, List.of(scalar(MANA, 1, 1)), List.of()));

        assertEquals(ClientResourceSyncState.ApplyResult.INCOMPATIBLE_SCHEMA, result);
        assertEquals(33L, state.scalar(MANA).orElseThrow().currentUnits());
        assertEquals(2L, state.revision());
    }

    // ── Correction 3: exact delta revision succession (client-side defensive re-check) ──────────

    @Test
    void deltaRevisionJumpLargerThanOneIsRejectedAtConstructionTime() {
        // ResourceDeltaSyncPayload's own constructor already enforces revision == baseRevision + 1
        // — a jump of more than one can never even become a payload instance.
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceDeltaSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 5L, 7L, List.of(), List.of(), List.of()));
    }

    @Test
    void baseRevisionAtLongMaxValueIsRejectedToAvoidOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceDeltaSyncPayload(
                        ResourceSyncProtocol.PROTOCOL_VERSION, Long.MAX_VALUE, Long.MAX_VALUE + 1, List.of(), List.of(), List.of()));
    }
}

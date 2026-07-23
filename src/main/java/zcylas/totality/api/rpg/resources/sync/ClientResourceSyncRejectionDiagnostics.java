package zcylas.totality.api.rpg.resources.sync;

/**
 * Pure formatter for the bounded DEBUG diagnostic logged by {@code ClientResourceSyncManager} when a
 * full or delta Resource sync payload is rejected as {@link ClientResourceSyncState.ApplyResult#MALFORMED}
 * or {@link ClientResourceSyncState.ApplyResult#INCOMPATIBLE_SCHEMA} — added per the Phase 3B-1
 * malformed/incompatible-payload diagnostics requirement. Kept free of any Minecraft/Fabric/logging
 * dependency so the exact bounded content of the message is directly unit-testable.
 *
 * <p>Deliberately never includes full payload contents (scalar/partition lists, resource ids) — only
 * the payload type, schema version, base/current/resulting revision, and the apply result itself.
 * {@code STALE_IGNORED}/{@code REVISION_GAP} are ordinary reordering outcomes and are not described
 * by this class at all; only a genuinely malformed or schema-incompatible payload — which almost
 * certainly indicates a real bug rather than ordinary network reordering — is worth this diagnostic.
 */
public final class ClientResourceSyncRejectionDiagnostics {

    public static String describeFull(
            int schemaVersion, long payloadRevision, long currentRevision, ClientResourceSyncState.ApplyResult result) {
        return "Resource sync full snapshot rejected: result=" + result
                + " schemaVersion=" + schemaVersion
                + " payloadRevision=" + payloadRevision
                + " currentRevision=" + currentRevision;
    }

    public static String describeDelta(
            int schemaVersion, long baseRevision, long payloadRevision, long currentRevision,
            ClientResourceSyncState.ApplyResult result) {
        return "Resource sync delta rejected: result=" + result
                + " schemaVersion=" + schemaVersion
                + " baseRevision=" + baseRevision
                + " payloadRevision=" + payloadRevision
                + " currentRevision=" + currentRevision;
    }

    private ClientResourceSyncRejectionDiagnostics() {}
}

package zcylas.totality.networking.resource;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.resources.sync.ClientResourceSyncState;
import zcylas.totality.api.rpg.resources.sync.ClientResyncRequestGate;

/**
 * Client-side runtime glue for the Phase 3A generic Resource sync contract: a single per-session
 * {@link ClientResourceSyncState} instance, applied to by the packet receivers registered in
 * {@code TotalityClientPacketHandlers}, cleared on disconnect/world-change (see
 * {@code TotalityClient}). A {@code REVISION_GAP} triggers an automatic resync request — at most
 * one request is ever in flight at a time (see {@link ClientResyncRequestGate}), so a run of
 * consecutive gap-producing deltas while a request is already pending sends no further requests.
 *
 * <p><b>Bounded retry (external-review correction, 2026-07-22):</b> the server's
 * {@code ResourceResyncRequestHandler} rate-limits accepted requests to one per 100 ticks and
 * silently drops anything more frequent — no rejection notice, no scheduled full snapshot. Without
 * a retry, a request dropped that way would leave {@link ClientResyncRequestGate} permanently
 * pending, since only an accepted {@code APPLIED_FULL} or an explicit lifecycle {@link #clear()}
 * ever un-pends it. {@link #tick()}, registered against the client's ordinary end-of-tick event (see
 * {@code TotalityClient}), drives {@link ClientResyncRequestGate#tick()} every client tick so a
 * still-pending, still-unresolved request is retried on a bounded schedule even if no further delta
 * ever arrives to prompt another attempt.
 *
 * <p>Phase 3A boundary: this class exists only to make the packet contract real/testable. It is
 * not a gameplay data source — nothing reads {@link #state()} for HUD, menu, or gameplay decisions
 * yet; that public façade is Phase 3B scope. {@link #state()} is package-private since nothing
 * outside this package needs it yet.
 */
public final class ClientResourceSyncManager {

    private static final ClientResourceSyncState STATE = new ClientResourceSyncState();
    private static final ClientResyncRequestGate RESYNC_GATE = new ClientResyncRequestGate();

    static ClientResourceSyncState state() {
        return STATE;
    }

    public static void applyFull(ResourceFullSyncPayload payload) {
        ClientResourceSyncState.ApplyResult result = STATE.applyFull(payload);
        // Only a genuinely accepted full snapshot resolves the outstanding gap — a stale, malformed,
        // or incompatible-schema full leaves the pending flag (and the retry timer) untouched, so a
        // still-unresolved gap keeps retrying on schedule via tick() rather than silently stopping.
        if (result == ClientResourceSyncState.ApplyResult.APPLIED_FULL) {
            RESYNC_GATE.clear();
        }
    }

    public static void applyDelta(ResourceDeltaSyncPayload payload) {
        ClientResourceSyncState.ApplyResult result = STATE.applyDelta(payload);
        if (result == ClientResourceSyncState.ApplyResult.REVISION_GAP) {
            if (RESYNC_GATE.requestIfNotPending()) {
                Totality.LOGGER.debug("Resource sync revision gap detected, requesting resync");
                ClientPlayNetworking.send(new ResourceResyncRequestPayload());
            }
        }
    }

    /**
     * Called once per client tick (see {@code TotalityClient.onInitializeClient()}). Cheap no-op
     * unless a resync request is currently pending: {@link ClientResyncRequestGate#tick()} itself
     * is a no-op while not pending, and no play connection means there is nothing useful to retry
     * (and nothing this could safely send to) — checked first via
     * {@link ClientPlayNetworking#canSend} so a retry is never attempted while disconnected. Does
     * not read or mutate any gameplay Resource value; it only ever resends the same
     * zero-field {@link ResourceResyncRequestPayload} the immediate-request path already sends.
     */
    public static void tick() {
        if (!ClientPlayNetworking.canSend(ResourceResyncRequestPayload.TYPE)) {
            return;
        }
        if (RESYNC_GATE.tick()) {
            Totality.LOGGER.debug("Resource sync resync request retry — no full snapshot accepted within the retry interval");
            ClientPlayNetworking.send(new ResourceResyncRequestPayload());
        }
    }

    public static void clear() {
        STATE.clear();
        RESYNC_GATE.clear();
    }

    private ClientResourceSyncManager() {}
}

package zcylas.totality.api.rpg.resources.sync;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure single-flight decision logic for the client resync-request path — see
 * {@code zcylas.totality.networking.resource.ClientResourceSyncManager}, which drives this gate's
 * transitions (request on the first {@code REVISION_GAP}, clear only on an accepted
 * {@code APPLIED_FULL}, bounded retry via {@link ClientResyncRequestGate#tick()} on every client
 * tick) but cannot itself be unit-tested since it also sends a real network packet.
 *
 * <p>Bounded-retry tests below use a small explicit {@code retryIntervalTicks} (via the
 * {@link ClientResyncRequestGate#ClientResyncRequestGate(long)} constructor) purely so a full
 * interval can be exercised in a handful of {@link ClientResyncRequestGate#tick()} calls rather than
 * {@link ClientResyncRequestGate#DEFAULT_RETRY_INTERVAL_TICKS}-many — the timing logic being tested
 * (count-then-fire-then-reset) is identical regardless of the configured interval length.
 */
class ClientResyncRequestGateTest {

    @Test
    void firstGapRequestsOnce() {
        ClientResyncRequestGate gate = new ClientResyncRequestGate();

        assertTrue(gate.requestIfNotPending(), "the first gap must be allowed to send a request");
        assertTrue(gate.isPending());
    }

    @Test
    void repeatedGapsWhilePendingDoNotRequestAgain() {
        ClientResyncRequestGate gate = new ClientResyncRequestGate();
        gate.requestIfNotPending();

        assertFalse(gate.requestIfNotPending(), "a second gap while a request is already pending must not send another");
        assertFalse(gate.requestIfNotPending(), "a third gap must also be suppressed");
    }

    @Test
    void validFullClearsPendingAndReEnablesFutureRequests() {
        ClientResyncRequestGate gate = new ClientResyncRequestGate();
        gate.requestIfNotPending();

        gate.clear(); // simulates ClientResourceSyncManager reacting to an accepted APPLIED_FULL

        assertFalse(gate.isPending());
        assertTrue(gate.requestIfNotPending(), "clearing must re-enable the next gap to request again");
    }

    @Test
    void rejectedFullDoesNotReEnableRequestsWithoutAnExplicitClear() {
        ClientResyncRequestGate gate = new ClientResyncRequestGate();
        gate.requestIfNotPending();

        // A stale/malformed/incompatible-schema full must never call clear() — simulated here by
        // simply not calling it — so the gate must remain pending.
        assertTrue(gate.isPending());
        assertFalse(gate.requestIfNotPending());
    }

    @Test
    void clearIsSafeAndIdempotentWhenNothingWasPending() {
        ClientResyncRequestGate gate = new ClientResyncRequestGate();

        gate.clear();

        assertFalse(gate.isPending());
        assertTrue(gate.requestIfNotPending());
    }

    // ── Bounded retry (external-review correction, 2026-07-22) ──────────────────────────────────

    @Test
    void tickNeverFiresWhileNotPending() {
        ClientResyncRequestGate gate = new ClientResyncRequestGate(5);

        for (int i = 0; i < 20; i++) {
            assertFalse(gate.tick(), "tick() must be a pure no-op while no request is pending");
        }
    }

    @Test
    void noRetryOneTickBeforeTheConfiguredInterval() {
        ClientResyncRequestGate gate = new ClientResyncRequestGate(5);
        gate.requestIfNotPending();

        // Interval is 5 — ticks 1..4 must not fire, only tick 5 (tested separately below) may.
        for (int i = 0; i < 4; i++) {
            assertFalse(gate.tick(), "retry must not fire before the configured interval elapses");
        }
    }

    @Test
    void retryBecomesDueExactlyAtTheConfiguredInterval() {
        ClientResyncRequestGate gate = new ClientResyncRequestGate(5);
        gate.requestIfNotPending();

        assertFalse(gate.tick()); // 1
        assertFalse(gate.tick()); // 2
        assertFalse(gate.tick()); // 3
        assertFalse(gate.tick()); // 4
        assertTrue(gate.tick(), "retry must become due at exactly the configured interval"); // 5
    }

    @Test
    void retryTimingResetsAfterFiringPreventingPerTickSpam() {
        ClientResyncRequestGate gate = new ClientResyncRequestGate(3);
        gate.requestIfNotPending();

        assertFalse(gate.tick());
        assertFalse(gate.tick());
        assertTrue(gate.tick(), "first retry fires at tick 3");

        // The timer must have reset to zero, not kept counting past the interval — the very next
        // tick must not immediately fire again.
        assertFalse(gate.tick(), "must not fire again on the tick immediately after a retry");
        assertFalse(gate.tick());
        assertTrue(gate.tick(), "second retry fires a full interval after the first, not sooner");
    }

    @Test
    void retryFiresFromTickAloneWithoutAnyDeltaOrFullEverBeingInvolved() {
        // The gate has no coupling to ResourceDeltaSyncPayload/ResourceFullSyncPayload at all —
        // this test only ever calls requestIfNotPending()/tick(), proving a retry can occur purely
        // from repeated tick() calls, independent of whether any further delta ever arrives.
        ClientResyncRequestGate gate = new ClientResyncRequestGate(4);
        gate.requestIfNotPending();

        for (int i = 0; i < 3; i++) {
            gate.tick();
        }
        assertTrue(gate.tick(), "a retry must fire from tick() alone with no other interaction");
    }

    @Test
    void validFullClearsPendingStateAndCancelsAnyScheduledRetry() {
        ClientResyncRequestGate gate = new ClientResyncRequestGate(3);
        gate.requestIfNotPending();
        gate.tick(); // 1 tick elapsed toward the interval

        gate.clear(); // simulates ClientResourceSyncManager.applyFull on an accepted APPLIED_FULL

        assertFalse(gate.isPending());
        // The retry timer must have been reset too, not merely left at 1 tick elapsed — ticking
        // right up to (but not past) the old interval must not spuriously fire since nothing is
        // pending, and a fresh request must restart its own timer from zero.
        for (int i = 0; i < 10; i++) {
            assertFalse(gate.tick(), "a cleared gate must never fire a retry");
        }
    }

    @Test
    void staleFullDoesNotCancelRetries() {
        // ClientResourceSyncManager.applyFull only calls clear() when the result is APPLIED_FULL —
        // a STALE_IGNORED result leaves this gate completely untouched. Simulated here by simply
        // never calling clear(): the retry timer must continue counting toward the next retry
        // exactly as if the stale full had never arrived.
        ClientResyncRequestGate gate = new ClientResyncRequestGate(4);
        gate.requestIfNotPending();

        assertFalse(gate.tick());
        assertFalse(gate.tick());
        assertFalse(gate.tick());
        assertTrue(gate.tick(), "retry must still fire on schedule; a stale full never touches this gate");
    }

    @Test
    void malformedFullDoesNotCancelRetries() {
        // Same underlying guarantee as staleFullDoesNotCancelRetries — ClientResourceSyncManager.
        // applyFull's single `if (result == APPLIED_FULL)` branch means a MALFORMED result is
        // likewise never distinguishable from "nothing happened" from this gate's perspective.
        ClientResyncRequestGate gate = new ClientResyncRequestGate(4);
        gate.requestIfNotPending();

        assertFalse(gate.tick());
        assertFalse(gate.tick());
        assertFalse(gate.tick());
        assertTrue(gate.tick(), "retry must still fire on schedule; a malformed full never touches this gate");
    }

    @Test
    void incompatibleSchemaFullDoesNotCancelRetries() {
        // Same underlying guarantee again — an INCOMPATIBLE_SCHEMA result is the third of the three
        // non-APPLIED_FULL outcomes ClientResourceSyncManager.applyFull can receive, and all three
        // are equally invisible to this gate since none of them ever calls clear().
        ClientResyncRequestGate gate = new ClientResyncRequestGate(4);
        gate.requestIfNotPending();

        assertFalse(gate.tick());
        assertFalse(gate.tick());
        assertFalse(gate.tick());
        assertTrue(gate.tick(), "retry must still fire on schedule; an incompatible-schema full never touches this gate");
    }

    @Test
    void lifecycleClearResetsPendingAndRetryTiming() {
        // Models TotalityClient's JOIN/DISCONNECT/AFTER_CLIENT_LEVEL_CHANGE listeners, which all
        // call ClientResourceSyncManager.clear() -> ClientResyncRequestGate.clear().
        ClientResyncRequestGate gate = new ClientResyncRequestGate(5);
        gate.requestIfNotPending();
        gate.tick();
        gate.tick();

        gate.clear();

        assertFalse(gate.isPending());
        assertTrue(gate.requestIfNotPending(), "a lifecycle clear must re-enable an immediate request afterward");
    }

    @Test
    void afterAValidFullANewLaterGapCanRequestImmediately() {
        ClientResyncRequestGate gate = new ClientResyncRequestGate();
        gate.requestIfNotPending();

        gate.clear(); // accepted APPLIED_FULL

        assertTrue(gate.requestIfNotPending(), "a fresh gap after a clear must always request immediately, not wait for a retry");
    }

    @Test
    void systemIsNotPermanentlyStuckWhenAResyncIsSilentlyThrottledByTheServer() {
        // Models the exact deadlock scenario this correction fixes:
        //   1. First gap -> immediate request, accepted by the server.
        ClientResyncRequestGate gate = new ClientResyncRequestGate(150);
        assertTrue(gate.requestIfNotPending());

        //   2. A valid full eventually arrives and clears the gate.
        gate.clear();

        //   3. A second, later gap occurs -- from the client's perspective this always requests
        //      immediately (it doesn't know a server-side throttle window is about to reject it).
        assertTrue(gate.requestIfNotPending());

        //   4. The server silently drops this one (within its own 100-tick window): no full ever
        //      arrives, so nothing calls clear() again. Ticks pass with no acknowledgment...
        for (int i = 0; i < 149; i++) {
            assertFalse(gate.tick(), "must not retry before the client's own retry interval elapses");
        }

        //   5. ...until the bounded retry interval elapses, at which point the client tries again
        //      entirely on its own, without ever needing the dropped request to be acknowledged.
        assertTrue(gate.tick(), "a bounded retry must eventually fire, proving the client is not stuck");
    }
}

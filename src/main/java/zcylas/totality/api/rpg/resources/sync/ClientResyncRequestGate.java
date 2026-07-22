package zcylas.totality.api.rpg.resources.sync;

/**
 * Pure single-flight decision gate for the client's serverbound resync-request path: at most one
 * {@code ResourceResyncRequestPayload} may be in flight at a time, so a run of consecutive
 * revision-gap deltas (which the server keeps sending while the client is behind) does not spam the
 * server with a duplicate request per gap. Deliberately free of any network/Minecraft dependency —
 * see {@code zcylas.totality.networking.resource.ClientResourceSyncManager} for the impure caller
 * that actually sends the packet and drives this gate's transitions.
 *
 * <p><b>Bounded retry (external-review correction, 2026-07-22):</b> the server's own
 * {@code ResourceResyncRequestHandler} rate-limits accepted requests to at most one per 100 ticks
 * and <em>silently drops</em> anything more frequent — it schedules no full snapshot for a dropped
 * request and sends no rejection notice. A single-flight gate that only ever clears on an accepted
 * full snapshot would therefore be able to strand itself forever: request sent, dropped by the
 * server's throttle, gate permanently pending, every later gap's request suppressed as a "duplicate"
 * of one that will never be acknowledged. {@link #tick()} exists so the caller can drive a bounded
 * retry — a request resent after a fixed number of ticks with no accepted full snapshot in between —
 * without depending on any acknowledgment of the dropped request ever arriving.
 */
public final class ClientResyncRequestGate {

    /**
     * Default retry interval in client ticks — modestly longer than
     * {@code ResourceResyncRequestHandler}'s 100-tick server-side rate limiter, so a retry is very
     * unlikely to itself be dropped by that same throttle even under a somewhat late/uneven client
     * tick rate. Chosen from the middle of the task's suggested 120-200 tick range.
     */
    public static final long DEFAULT_RETRY_INTERVAL_TICKS = 150;

    private final long retryIntervalTicks;
    private boolean pending = false;
    private long ticksSincePending = 0;

    public ClientResyncRequestGate() {
        this(DEFAULT_RETRY_INTERVAL_TICKS);
    }

    public ClientResyncRequestGate(long retryIntervalTicks) {
        if (retryIntervalTicks <= 0) {
            throw new IllegalArgumentException("retryIntervalTicks must be > 0, was " + retryIntervalTicks);
        }
        this.retryIntervalTicks = retryIntervalTicks;
    }

    /**
     * Returns {@code true} exactly once per pending window: the first call after construction, or
     * after {@link #clear()}, returns {@code true} and marks a request pending (resetting the retry
     * timer). Every subsequent call returns {@code false} without side effects until {@link #clear()}
     * is called again — the caller should send a resync request only when this returns {@code true}.
     */
    public boolean requestIfNotPending() {
        if (pending) {
            return false;
        }
        pending = true;
        ticksSincePending = 0;
        return true;
    }

    public boolean isPending() {
        return pending;
    }

    /**
     * Advances the pending-retry timer by exactly one client tick. A no-op (returns {@code false},
     * no state change) while not pending — so this is always safe to call unconditionally on every
     * client tick, connected or not, pending or not. While pending, once {@link #retryIntervalTicks}
     * ticks have elapsed since the request became pending (or since the previous retry fired) without
     * a {@link #clear()} in between, returns {@code true} exactly once and resets the timer — the
     * caller should resend a resync request only when this returns {@code true}. Repeated calls
     * within the same interval always return {@code false}: this never fires more than once per
     * {@link #retryIntervalTicks} ticks, so calling this every tick never spams a request every tick.
     */
    public boolean tick() {
        if (!pending) {
            return false;
        }
        ticksSincePending++;
        if (ticksSincePending >= retryIntervalTicks) {
            ticksSincePending = 0;
            return true;
        }
        return false;
    }

    /**
     * Clears the pending flag and resets the retry timer, re-enabling the next gap to send a request
     * immediately and cancelling any already-scheduled retry. Call only after a valid full snapshot
     * is accepted ({@code APPLIED_FULL}), or when client Resource synchronization state is explicitly
     * cleared (disconnect, world change). Must NOT be called for a stale, malformed, or
     * incompatible-schema full snapshot, nor for a rejected delta — those leave a genuine gap
     * unresolved, and only {@link #tick()}'s bounded retry — never a "natural" clearing of this gate
     * by itself — may prompt another request while none of those has occurred.
     */
    public void clear() {
        pending = false;
        ticksSincePending = 0;
    }
}

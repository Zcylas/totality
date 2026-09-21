package zcylas.totality.api.rpg.resources.client.parity;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Pure tick-counter and local-player-identity lifecycle state machine for the Phase 3B-2B
 * coordinator. Generically typed over whatever identity token the caller uses to detect local-player
 * replacement — the real client-only coordinator uses the actual {@code LocalPlayer} reference
 * itself; tests use a plain marker object — so this class has no Minecraft/Fabric dependency of its
 * own and is directly unit-testable.
 *
 * <h2>Monotonic tick counter</h2>
 * Advances by exactly one on every {@link #beginTick} call that returns a tick (i.e. every call
 * with a non-null player). Never derived from world time, server tick time, or wall-clock
 * milliseconds. The <b>first</b> poll after construction or after a {@link #clear()} uses tick
 * {@code 0} — not {@code 1} — preserving the elapsed-tick tracker's own established T/T+1/T+2
 * semantics unchanged (a mismatch first observed at the first poll's tick is {@code
 * TRANSITIONAL_MISMATCH}; still disagreeing two polls later is {@code PERSISTENT_MISMATCH}, exactly
 * as {@link ClientResourceParityTracker} already guarantees). Protected against overflow via a
 * saturating increment (see {@link #safeIncrementTick}) — practically unreachable at real
 * client-tick rates, but never silently wraps negative regardless.
 *
 * <h2>Local-player identity</h2>
 * {@link #beginTick} compares the supplied player token against the previously-seen one by
 * reference ({@code !=}), never by {@code equals()}/UUID — a respawn (or another client-side
 * replacement) can produce a new player object that still represents "the same" logical player by
 * UUID, and that replacement must still reset parity state (a stale pre-respawn observation must
 * never be compared against post-respawn generic/legacy values). A {@code null} player (no active
 * local player — main menu, between worlds) is idle: {@link #beginTick} returns {@link
 * OptionalLong#empty()} and performs at most one reset (the transition *into* the null state),
 * never a reset on every subsequent null-player call.
 */
public final class ClientResourceParityLifecycle<P> {

    private final ClientResourceParityTracker tracker;
    private long tickCounter = 0;
    private P currentPlayer;

    public ClientResourceParityLifecycle(ClientResourceParityTracker tracker) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    /**
     * Call once per real tick. Returns the tick to poll with if {@code player} is non-null;
     * otherwise returns empty and the caller must not poll this call. Handles all lifecycle
     * reset/idle logic internally — resetting on a genuine player-identity change (including the
     * null-to-non-null and non-null-to-null transitions), and never resetting again for a
     * continuing null state or a continuing identical player reference.
     */
    public OptionalLong beginTick(P player) {
        if (player == null) {
            if (currentPlayer != null) {
                clear();
            }
            return OptionalLong.empty();
        }
        if (player != currentPlayer) {
            clear();
            currentPlayer = player;
        }
        long tick = tickCounter;
        tickCounter = safeIncrementTick(tickCounter);
        return OptionalLong.of(tick);
    }

    /** Resets the tick counter to its pre-first-poll state, forgets the current player identity,
     *  and clears every tracked parity observation — the single reset operation every lifecycle
     *  trigger (JOIN, DISCONNECT, dimension change, player-identity change) funnels through. */
    public void clear() {
        tracker.clearAll();
        tickCounter = 0;
        currentPlayer = null;
    }

    /** Never wraps negative: increments up to {@link Long#MAX_VALUE}, then holds there forever.
     *  Package-private (rather than {@code private}) solely so a test can exercise the boundary
     *  directly without driving an unreasonable number of real ticks. */
    static long safeIncrementTick(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1;
    }
}

package zcylas.totality.api.rpg.resources.client.parity;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure elapsed-client-tick grace tracker for shadow-parity mismatches. No Minecraft/Fabric
 * dependency — driven entirely by explicit method calls with a caller-supplied monotonic tick
 * value, never wall-clock time, so it is directly unit-testable without a running client.
 *
 * <h2>Why elapsed ticks, not observation-count generations</h2>
 * The Phase 3B-2 readiness audit originally proposed a "generation" model (grace advances only when
 * a new comparison is triggered by a source update). This task's external review overrides that
 * recommendation: a generation-only model can leave a genuine mismatch {@code TRANSITIONAL_MISMATCH}
 * forever if no further source update ever arrives (confirmed real case: {@code
 * FormulaResolver.tryCast} changes generic Mana but sends no legacy Mana packet at all; Mana also has
 * no join-time legacy push, so a stale legacy default can disagree with a fresh generic snapshot
 * indefinitely without any further legacy-side notification). This tracker instead expects the
 * coordinator (Phase 3B-2B, not built in this slice) to poll all four eligible parity pairs once per
 * {@code END_CLIENT_TICK}, running a fresh comparison and feeding the outcome to {@link #observe}
 * every tick — see {@link #resourcesRequiringRecheck()} for identifying which resources most need
 * that fresh comparison, and {@link #advanceDeadline} for the narrower, purely-diagnostic operation
 * of re-evaluating a deadline without a fresh comparison.
 *
 * <h2>Exact grace semantics</h2>
 * With the default 2-tick grace: a mismatch first observed at tick {@code T} is
 * {@code TRANSITIONAL_MISMATCH}; still disagreeing at {@code T+1} remains {@code TRANSITIONAL_MISMATCH};
 * still disagreeing at {@code T+2} becomes {@code PERSISTENT_MISMATCH}. Changing mismatching values,
 * or repeating the identical mismatching values, never restarts the deadline while the two sides
 * remain continuously mismatched — only a genuine match (at any point) clears the pending mismatch,
 * and only a fresh mismatch following a match starts a new grace period.
 *
 * <h2>{@code PENDING_RESYNC} freeze — including across skipped ticks (external-review correction)</h2>
 * A mismatch observed while the caller reports the generic side as pending-resync does not consume
 * grace ticks at all. The naive approach of computing the elapsed delta from whatever tick was last
 * observed and simply skipping the addition while frozen is <b>not sufficient</b>: if the coordinator
 * does not call this tracker on every single frozen tick (e.g. it freezes at tick 11 and does not
 * call again until tick 100), the first call after resuming would otherwise compute a 89-tick delta
 * spanning the entire frozen gap. This tracker instead detects the specific frozen-to-unfrozen
 * transition explicitly: the first observation reporting {@code pendingResync == false} after a
 * previous observation reported {@code pendingResync == true} contributes <b>zero</b> newly-consumed
 * grace — it only establishes a fresh timing baseline. Only strictly subsequent, still-unfrozen
 * observations accumulate grace normally from that baseline. A match still clears the pending
 * mismatch unconditionally, freeze or not, at any point.
 */
public final class ClientResourceParityTracker {

    public static final long DEFAULT_GRACE_TICKS = 2;

    private final long graceDurationTicks;
    private final Map<Identifier, TrackedState> states = new LinkedHashMap<>();

    public ClientResourceParityTracker() {
        this(DEFAULT_GRACE_TICKS);
    }

    public ClientResourceParityTracker(long graceDurationTicks) {
        if (graceDurationTicks < 1) {
            throw new IllegalArgumentException("graceDurationTicks must be >= 1, was " + graceDurationTicks);
        }
        this.graceDurationTicks = graceDurationTicks;
    }

    /**
     * Records a fresh comparison outcome for {@code resourceId} at {@code tick}. This is the primary
     * entry point and the one Phase 3B-2B's coordinator is expected to call every {@code
     * END_CLIENT_TICK} for every generic-sync-eligible resource, each time supplying a freshly
     * recomputed {@code genericSummary}/{@code legacySummary}/{@code outcome} — see the Phase 3B-2A
     * implementation report's "Phase 3B-2B integration decision" section for the full polling design
     * this method is built to support (four small comparisons per tick, not diagnostic notification
     * hooks on the legacy managers).
     *
     * <p>A {@code tick} lower than the resource's own last-observed tick is safely ignored (the
     * existing observation is returned unchanged) — never throws, never corrupts state, per the
     * monotonicity requirement. A negative {@code tick} is rejected outright as a programming error.
     *
     * @param pendingResync whether the generic side currently reports {@code PENDING_RESYNC} trust;
     *                      only meaningful while {@code outcome == MISMATCH} (see the freeze behavior
     *                      documented on this class).
     */
    public ClientResourceParityObservation observe(
            Identifier resourceId,
            long tick,
            ClientResourceParityOutcome outcome,
            ClientResourceParitySummary genericSummary,
            ClientResourceParitySummary legacySummary,
            boolean pendingResync) {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(genericSummary, "genericSummary");
        Objects.requireNonNull(legacySummary, "legacySummary");
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0, was " + tick);
        }

        TrackedState existing = states.get(resourceId);
        if (existing != null && tick < existing.lastObservedTick) {
            // Decreasing tick — safely ignored, state unchanged.
            return toObservation(resourceId, existing);
        }

        TrackedState state = existing != null ? existing : new TrackedState();
        boolean hadPriorState = existing != null;

        if (outcome == ClientResourceParityOutcome.MISMATCH) {
            applyMismatch(state, tick, pendingResync, hadPriorState);
        } else {
            applyNonMismatch(state, outcome);
        }

        state.genericSummary = genericSummary;
        state.legacySummary = legacySummary;
        state.lastObservedTick = tick;

        states.put(resourceId, state);
        return toObservation(resourceId, state);
    }

    /**
     * Re-evaluates {@code resourceId}'s currently pending mismatch deadline (if any) at a later
     * {@code tick}, reusing its already-stored generic/legacy summaries verbatim — it does
     * <b>not</b> re-read or re-compare current Resource values, and it does <b>not</b> perform a
     * fresh parity comparison of any kind. This is a narrow, purely pure-state-machine operation
     * useful for proving that a deadline can advance without any source-side notification when the
     * underlying values are already known to be unchanged (see the test suite) — it must
     * <b>not</b> be used as the primary Phase 3B-2B integration route. A caller performing a real
     * parity recheck must first read the current generic/legacy state and run the appropriate
     * comparator, then call {@link #observe} with the fresh outcome; see {@link #observe}'s own
     * Javadoc for the actual polling design.
     *
     * <p>Returns {@link Optional#empty()} only if {@code resourceId} has never been observed at all;
     * if it has been observed but is not currently tracking a mismatch (e.g. {@code EXACT_MATCH}/
     * {@code GENERIC_NOT_READY}/...), the existing observation is returned unchanged — there is
     * nothing to advance.
     */
    public Optional<ClientResourceParityObservation> advanceDeadline(Identifier resourceId, long tick, boolean pendingResync) {
        Objects.requireNonNull(resourceId, "resourceId");
        TrackedState existing = states.get(resourceId);
        if (existing == null) {
            return Optional.empty();
        }
        if (!isMismatchTracking(existing.classification)) {
            return Optional.of(toObservation(resourceId, existing));
        }
        return Optional.of(observe(resourceId, tick, ClientResourceParityOutcome.MISMATCH,
                existing.genericSummary, existing.legacySummary, pendingResync));
    }

    /** The latest immutable observation for {@code resourceId}, if it has ever been observed. */
    public Optional<ClientResourceParityObservation> latest(Identifier resourceId) {
        Objects.requireNonNull(resourceId, "resourceId");
        TrackedState state = states.get(resourceId);
        return state == null ? Optional.empty() : Optional.of(toObservation(resourceId, state));
    }

    /**
     * Every Resource id currently classified {@code TRANSITIONAL_MISMATCH} — <b>including</b> one
     * whose grace is currently frozen by a pending resync (external-review correction: a frozen
     * transitional mismatch must remain discoverable here, since the coordinator must keep comparing
     * it in order to ever notice the moment {@code PENDING_RESYNC} ends and grace resumes; excluding
     * it would make it silently disappear from the one set Phase 3B-2B is told to revisit).
     *
     * <p>{@code PERSISTENT_MISMATCH} is deliberately <b>excluded</b> — it no longer needs continued
     * *deadline* advancement, since the deadline has already been reached and further elapsed ticks
     * cannot change that classification on their own. This does not mean a persistent mismatch is
     * ignored: Phase 3B-2B's full four-resource per-tick polling design (see {@link #observe}'s
     * Javadoc) freshly compares every eligible resource unconditionally every tick regardless of
     * this set, which is exactly what lets a persistent mismatch recover to {@code EXACT_MATCH} once
     * the underlying values actually agree again — that recovery path runs through {@link #observe}
     * directly, not through this discovery set.
     *
     * <p>{@code EXACT_MATCH}, {@code GENERIC_NOT_READY}, {@code MODEL_MISMATCH}, {@code
     * EXPECTED_SEMANTIC_DIFFERENCE}, and {@code NOT_APPLICABLE} are all excluded — none of them track
     * a pending deadline of any kind. Returns an immutable snapshot; never exposes the backing map.
     */
    public Set<Identifier> resourcesRequiringRecheck() {
        Set<Identifier> pending = new LinkedHashSet<>();
        for (Map.Entry<Identifier, TrackedState> entry : states.entrySet()) {
            if (entry.getValue().classification == ClientResourceParityClassification.TRANSITIONAL_MISMATCH) {
                pending.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(pending);
    }

    /** Clears all tracked state for one Resource id (lifecycle reset scope — a single resource). */
    public void clear(Identifier resourceId) {
        Objects.requireNonNull(resourceId, "resourceId");
        states.remove(resourceId);
    }

    /** Clears every tracked Resource's state — full lifecycle reset (disconnect/reconnect/dimension
     *  change), per the Phase 3B-2 readiness audit §7.6/§14. */
    public void clearAll() {
        states.clear();
    }

    private void applyMismatch(TrackedState state, long tick, boolean pendingResync, boolean hadPriorState) {
        boolean continuingMismatch = hadPriorState && isMismatchTracking(state.classification);

        if (!continuingMismatch) {
            // A fresh mismatch episode: either the very first observation ever, or a mismatch
            // following a match/not-ready/model-mismatch/etc. — starts a brand new grace period.
            state.firstMismatchTick = tick;
            state.graceTicksConsumed = 0;
            state.observationCount = 1;
        } else {
            // Whether the PREVIOUS observation was frozen — captured before this call's
            // pendingResync overwrites it below. The specific frozen-to-unfrozen transition (this
            // call is the first to report pendingResync==false after a previous frozen observation)
            // must contribute zero newly-consumed grace: it only establishes a fresh timing
            // baseline, discarding whatever real-tick span elapsed while frozen (including any
            // ticks the coordinator never called observe/advanceDeadline for at all) — see this
            // class's "PENDING_RESYNC freeze — including across skipped ticks" Javadoc section.
            boolean wasFrozen = state.graceFrozen;
            boolean resumingFromFreeze = wasFrozen && !pendingResync;
            if (!pendingResync && !resumingFromFreeze) {
                long delta = tick - state.lastObservedTick;
                state.graceTicksConsumed += delta;
            }
            state.observationCount = saturatingIncrement(state.observationCount);
        }
        state.graceFrozen = pendingResync;
        state.classification = state.graceTicksConsumed >= graceDurationTicks
                ? ClientResourceParityClassification.PERSISTENT_MISMATCH
                : ClientResourceParityClassification.TRANSITIONAL_MISMATCH;
    }

    private void applyNonMismatch(TrackedState state, ClientResourceParityOutcome outcome) {
        ClientResourceParityClassification newClassification = mapNonMismatch(outcome);
        boolean sameAsBefore = state.classification == newClassification;
        state.firstMismatchTick = null;
        state.graceTicksConsumed = 0;
        state.graceFrozen = false;
        state.classification = newClassification;
        state.observationCount = sameAsBefore ? saturatingIncrement(state.observationCount) : 1;
    }

    /** Never wraps negative: increments up to {@link Integer#MAX_VALUE}, then holds there forever —
     *  a sufficiently long-running tracker must not have {@code observationCount} overflow into a
     *  negative value that {@link ClientResourceParityObservation}'s own validation would then reject.
     *  Package-private (rather than {@code private}) solely so a test can exercise the boundary
     *  directly without driving billions of real tracker observations. */
    static int saturatingIncrement(int value) {
        return value == Integer.MAX_VALUE ? Integer.MAX_VALUE : value + 1;
    }

    private static boolean isMismatchTracking(ClientResourceParityClassification classification) {
        return classification == ClientResourceParityClassification.TRANSITIONAL_MISMATCH
                || classification == ClientResourceParityClassification.PERSISTENT_MISMATCH;
    }

    private static ClientResourceParityClassification mapNonMismatch(ClientResourceParityOutcome outcome) {
        return switch (outcome) {
            case MATCH -> ClientResourceParityClassification.EXACT_MATCH;
            case GENERIC_NOT_READY -> ClientResourceParityClassification.GENERIC_NOT_READY;
            case MODEL_MISMATCH -> ClientResourceParityClassification.MODEL_MISMATCH;
            case EXPECTED_SEMANTIC_DIFFERENCE -> ClientResourceParityClassification.EXPECTED_SEMANTIC_DIFFERENCE;
            case NOT_APPLICABLE -> ClientResourceParityClassification.NOT_APPLICABLE;
            case MISMATCH -> throw new IllegalStateException("MISMATCH must be routed through applyMismatch");
        };
    }

    private static ClientResourceParityObservation toObservation(Identifier resourceId, TrackedState state) {
        return new ClientResourceParityObservation(
                resourceId,
                state.classification,
                state.genericSummary,
                state.legacySummary,
                state.firstMismatchTick,
                state.lastObservedTick,
                state.observationCount,
                state.graceFrozen);
    }

    private static final class TrackedState {
        Long firstMismatchTick;
        long lastObservedTick;
        long graceTicksConsumed;
        int observationCount;
        boolean graceFrozen;
        ClientResourceParityClassification classification;
        ClientResourceParitySummary genericSummary;
        ClientResourceParitySummary legacySummary;
    }
}

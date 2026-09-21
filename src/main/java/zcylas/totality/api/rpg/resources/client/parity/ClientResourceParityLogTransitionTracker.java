package zcylas.totality.api.rpg.resources.client.parity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Pure per-resource "am I currently inside a persistent-mismatch episode" memory for Phase 3B-2C's
 * bounded diagnostic logging. This is the single owner of that decision — no other class in the
 * parity package tracks it, so there is never more than one place that can decide whether a
 * transition should log (the task's explicit "no duplicate transition ownership" requirement).
 *
 * <p>Deliberately independent of {@link ClientResourceParityTracker}: it consumes only the final
 * committed {@link ClientResourceParityClassification} for a resource, never re-derives mismatch/
 * grace state of its own. No Minecraft/Fabric/logging dependency whatsoever — generic over the
 * resource-key type {@code K} (external-review correction: this class previously imported
 * Minecraft's own resource-location key type directly, which contradicted its own Javadoc's "no
 * Minecraft dependency" claim; it is now genuinely dependency-free, with the client-only boundary
 * supplying that concrete key type as {@code K}). Directly unit-testable with any key type.
 *
 * <p>Bounded exactly like {@link ClientResourceParityTracker}'s own backing map: one entry per
 * distinct resource id ever classified, never a growing history.
 *
 * <h2>Episode-continuation semantics (second external-review correction)</h2>
 * While a persistent-mismatch episode is active, not every non-{@code PERSISTENT_MISMATCH}
 * classification means the episode actually ended. A prior version of this class closed the episode
 * (and emitted a recovery) on <b>any</b> non-persistent classification — which is wrong for two
 * classifications that are observation <i>uncertainty</i> or an ordinary <i>within-grace</i> mismatch,
 * not a real change in the underlying comparison:
 * <ul>
 *   <li>{@code GENERIC_NOT_READY} — the generic side has temporarily lost its full snapshot (e.g. a
 *       parity-tracker rebuild after a dimension change, per {@link ClientResourceParityTracker}'s
 *       own reset triggers). This says nothing about whether the two sides actually agree again — it
 *       is a readiness gap, not a comparison result.</li>
 *   <li>{@code TRANSITIONAL_MISMATCH} — the two sides still disagree; they are simply within the
 *       elapsed-tick grace window before persistence is (re-)declared. This is still a mismatch, not
 *       a recovery.</li>
 * </ul>
 * Both of these therefore <b>keep the episode active and return {@link
 * ClientResourceParityLogTransition#NONE}</b> — exactly like a repeated {@code PERSISTENT_MISMATCH}
 * observation. This is what makes the confirmed live dimension-transfer/return sequence correct: the
 * parity tracker's rebuild after a dimension change necessarily passes through {@code
 * GENERIC_NOT_READY} → {@code TRANSITIONAL_MISMATCH} → {@code PERSISTENT_MISMATCH} again for a
 * mismatch that never actually resolved, and none of those three observations may close or re-open
 * the already-reported episode.
 *
 * <p>Every other classification is treated as a genuine, stable end to the episode (one recovery
 * event) — {@code EXACT_MATCH} is the ordinary case, but {@code MODEL_MISMATCH}, {@code
 * EXPECTED_SEMANTIC_DIFFERENCE}, and {@code NOT_APPLICABLE} also close it: each represents the
 * previous persistent numeric-mismatch comparison no longer applying at all (a structural
 * incomparability, a recognized intentional-difference case, or the resource becoming
 * not-comparable), not a continuation of the same disagreement. Reusing {@code
 * RECOVERED_FROM_PERSISTENT_MISMATCH} for these three is a deliberate vocabulary choice (the task's
 * required-coverage list intentionally does not add a fourth {@link ClientResourceParityLogTransition}
 * value for this) — "the previously-reported episode is over" is the accurate meaning in every one
 * of these four cases, even though only {@code EXACT_MATCH} is a "recovery" in the everyday sense.
 *
 * <p>{@link #classify} uses an exhaustive {@code switch} over every current {@link
 * ClientResourceParityClassification} constant, with no {@code default} arm — the task's explicit
 * instruction is that a future classification added to that enum must force a compile error here
 * rather than silently falling through to an unexamined, possibly-incorrect default behavior.
 */
public final class ClientResourceParityLogTransitionTracker<K> {

    private final Map<K, Boolean> persistentEpisodeActive = new LinkedHashMap<>();

    /**
     * Records the latest classification for {@code resourceId} and returns whether this observation
     * represents a fresh entry into, a fresh close of (recovery from), or no change to, a
     * persistent-mismatch episode.
     *
     * <p><b>No episode currently active:</b> {@code PERSISTENT_MISMATCH} begins one (returns {@link
     * ClientResourceParityLogTransition#ENTERED_PERSISTENT_MISMATCH}); every other classification
     * returns {@link ClientResourceParityLogTransition#NONE} and leaves the resource inactive.
     *
     * <p><b>An episode is currently active:</b> {@code PERSISTENT_MISMATCH}, {@code
     * GENERIC_NOT_READY}, and {@code TRANSITIONAL_MISMATCH} all return {@link
     * ClientResourceParityLogTransition#NONE} and leave the episode active (see the class Javadoc for
     * why the latter two do not close it). {@code EXACT_MATCH}, {@code MODEL_MISMATCH}, {@code
     * EXPECTED_SEMANTIC_DIFFERENCE}, and {@code NOT_APPLICABLE} all close the episode and return
     * {@link ClientResourceParityLogTransition#RECOVERED_FROM_PERSISTENT_MISMATCH} exactly once.
     */
    public ClientResourceParityLogTransition classify(K resourceId, ClientResourceParityClassification classification) {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(classification, "classification");

        boolean episodeActive = Boolean.TRUE.equals(persistentEpisodeActive.get(resourceId));

        if (!episodeActive) {
            if (classification == ClientResourceParityClassification.PERSISTENT_MISMATCH) {
                persistentEpisodeActive.put(resourceId, true);
                return ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH;
            }
            return ClientResourceParityLogTransition.NONE;
        }

        // An episode is active — decide, per the exhaustive classification switch, whether it
        // continues (still nothing to log) or closes (exactly one exit/recovery event).
        boolean episodeContinues = switch (classification) {
            case PERSISTENT_MISMATCH -> true;
            case GENERIC_NOT_READY -> true;
            case TRANSITIONAL_MISMATCH -> true;
            case EXACT_MATCH -> false;
            case MODEL_MISMATCH -> false;
            case EXPECTED_SEMANTIC_DIFFERENCE -> false;
            case NOT_APPLICABLE -> false;
        };

        if (episodeContinues) {
            return ClientResourceParityLogTransition.NONE;
        }
        persistentEpisodeActive.put(resourceId, false);
        return ClientResourceParityLogTransition.RECOVERED_FROM_PERSISTENT_MISMATCH;
    }

    /**
     * Forgets one resource's transition memory (lifecycle reset scope — a single resource). Never
     * itself returns or emits a transition — a clear is not an observation, so it can never
     * fabricate a recovery event for whoever calls {@link #classify} next.
     */
    public void clear(K resourceId) {
        Objects.requireNonNull(resourceId, "resourceId");
        persistentEpisodeActive.remove(resourceId);
    }

    /** Forgets every resource's transition memory — full connection-scoped reset (JOIN/DISCONNECT).
     *  Deliberately <b>not</b> called on a dimension change or a player-identity replacement — see
     *  {@code ClientResourceParityLogObserver}'s Javadoc for why a persistent-mismatch logging
     *  episode is connection-scoped, not tied to the same tighter lifecycle as {@link
     *  ClientResourceParityTracker#clearAll()}. */
    public void clearAll() {
        persistentEpisodeActive.clear();
    }
}

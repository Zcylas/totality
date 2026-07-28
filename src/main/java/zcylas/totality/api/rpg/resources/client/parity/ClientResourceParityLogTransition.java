package zcylas.totality.api.rpg.resources.client.parity;

/**
 * The bounded set of outcomes {@link ClientResourceParityLogTransitionTracker#classify} can return
 * for one Resource id's classification observation — Phase 3B-2C's diagnostic-logging decision,
 * kept entirely separate from {@link ClientResourceParityClassification} (the parity result itself)
 * and from {@link ClientResourceParityOutcome} (a comparator's raw match/mismatch signal).
 */
public enum ClientResourceParityLogTransition {
    /** No diagnostic should be emitted for this observation. */
    NONE,
    /** This Resource just transitioned into {@link ClientResourceParityClassification#PERSISTENT_MISMATCH}
     *  from any other classification — emit exactly one bounded entry diagnostic. */
    ENTERED_PERSISTENT_MISMATCH,
    /** An active persistent-mismatch episode for this Resource just closed — emit exactly one
     *  bounded recovery diagnostic. Returned only when the observation reaches one of the
     *  documented "stable exit" classifications: {@link ClientResourceParityClassification#EXACT_MATCH},
     *  {@link ClientResourceParityClassification#MODEL_MISMATCH}, {@link
     *  ClientResourceParityClassification#EXPECTED_SEMANTIC_DIFFERENCE}, or {@link
     *  ClientResourceParityClassification#NOT_APPLICABLE}. {@link
     *  ClientResourceParityClassification#GENERIC_NOT_READY} and {@link
     *  ClientResourceParityClassification#TRANSITIONAL_MISMATCH} do <b>not</b> close an active
     *  episode — see {@link ClientResourceParityLogTransitionTracker#classify} for the full,
     *  exhaustive per-classification rule set. */
    RECOVERED_FROM_PERSISTENT_MISMATCH
}

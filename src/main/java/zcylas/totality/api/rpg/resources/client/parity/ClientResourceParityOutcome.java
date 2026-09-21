package zcylas.totality.api.rpg.resources.client.parity;

/**
 * The result vocabulary a pure parity comparator ({@link ClientResourceScalarParityComparator},
 * {@link ClientSpellSlotParityComparator}, {@link ClientRageParityPolicy}) produces. Comparators
 * never decide {@code TRANSITIONAL_MISMATCH} versus {@code PERSISTENT_MISMATCH} themselves — that
 * decision belongs entirely to {@link ClientResourceParityTracker}'s elapsed-tick grace state
 * machine, which is why {@link #MISMATCH} here is a single, tracker-agnostic "these differ and both
 * sides are otherwise comparable" signal rather than a pre-escalated classification.
 */
public enum ClientResourceParityOutcome {
    /** Generic and legacy values agree (after shape/overflow validation). */
    MATCH,
    /** Values disagree, but both sides are structurally comparable — a candidate for the tracker's
     *  elapsed-tick grace window, never a classification in itself. */
    MISMATCH,
    /** The generic side has no accepted full snapshot yet. */
    GENERIC_NOT_READY,
    /** The two sides are structurally incomparable. */
    MODEL_MISMATCH,
    /** A known, intentional representational difference. */
    EXPECTED_SEMANTIC_DIFFERENCE,
    /** No legacy/generic pair exists to compare at all. */
    NOT_APPLICABLE
}

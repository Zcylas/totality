package zcylas.totality.api.rpg.resources.client.parity;

/**
 * The final, tracker-assigned parity classification for one Resource id. Distinct from
 * {@link ClientResourceParityOutcome}, which is what a pure comparator produces before the
 * elapsed-tick grace tracker ({@link ClientResourceParityTracker}) decides whether an ordinary
 * numeric mismatch is still within grace or has become persistent.
 *
 * <p>Deliberately narrower than the Phase 3B-2 readiness audit's originally suggested vocabulary —
 * see {@code TOTALITY_RESOURCE_API_PHASE_3B2A_PURE_PARITY_IMPLEMENTATION_REPORT.md} for the exact
 * reasoning behind each omission ({@code RAGE_ABSENCE_AMBIGUOUS}, {@code LEGACY_NOT_READY},
 * {@code UNIT_SCALE_MISMATCH}, {@code PENDING_GENERIC_RESYNC} are all deliberately not present).
 */
public enum ClientResourceParityClassification {
    /** Generic and legacy values agree (after shape/overflow validation). */
    EXACT_MATCH,
    /** Values disagree; still within the tracker's elapsed-tick grace window. */
    TRANSITIONAL_MISMATCH,
    /** Values have disagreed continuously for at least the full grace duration. */
    PERSISTENT_MISMATCH,
    /** A known, intentional representational difference — never escalates. */
    EXPECTED_SEMANTIC_DIFFERENCE,
    /** The generic side has no accepted full snapshot yet ({@code NOT_SYNCHRONIZED_YET}). No grace begins. */
    GENERIC_NOT_READY,
    /** The two sides are structurally incomparable (shape contradiction, unexpected failure reason,
     *  or a value one side cannot represent, e.g. overflow). Never treated as an ordinary mismatch. */
    MODEL_MISMATCH,
    /** No legacy/generic pair exists to compare at all (native Resources). No grace. */
    NOT_APPLICABLE
}

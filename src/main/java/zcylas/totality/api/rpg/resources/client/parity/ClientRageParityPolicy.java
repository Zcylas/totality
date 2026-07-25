package zcylas.totality.api.rpg.resources.client.parity;

import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import java.util.Objects;

/**
 * Pure Rage absence/expectation policy — the resolution of the Phase 3B-2 readiness audit's "Rage
 * absence decision" (§9), refined per this task's explicit external-review correction: Rage's
 * absence ambiguity is fully disambiguated by whether Rage is expected for this player, so no
 * dedicated {@code RAGE_ABSENCE_AMBIGUOUS} classification exists.
 *
 * <p>Receives {@code rageExpectedForPlayer} as a plain {@code boolean} — this class never reads
 * {@code ClientClassManager} or any real class-manager object itself (Phase 3B-2B's job is to
 * derive this boolean from existing client class state; here it is only ever a parameter).
 *
 * <p><b>Exact sparse 0/0 representation (external-review correction):</b> the legacy sparse-map
 * fallback only counts as the documented, expected "never granted" representation when it is a
 * {@code Scalar} satisfying all of {@code currentUnits == 0}, {@code maximumUnits == 0},
 * {@code overflowUnits == 0}, and {@code unitScale == 1} — a malformed scale or a nonzero overflow on
 * that fallback is never suppressed as {@code EXPECTED_SEMANTIC_DIFFERENCE}; it surfaces as an
 * ordinary mismatch instead, since a legacy scale/overflow contradiction is exactly the kind of
 * structurally-wrong state this policy must not paper over.
 */
public final class ClientRageParityPolicy {

    private ClientRageParityPolicy() {}

    public static ClientResourceParityOutcome compare(
            boolean rageExpectedForPlayer,
            ClientResourceParitySummary generic,
            ClientResourceParitySummary legacy) {
        Objects.requireNonNull(generic, "generic");
        Objects.requireNonNull(legacy, "legacy");

        if (generic instanceof ClientResourceParitySummary.Unavailable unavailable) {
            if (unavailable.reason() == ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET) {
                return ClientResourceParityOutcome.GENERIC_NOT_READY;
            }
            if (unavailable.reason() == ClientResourceUnavailableReason.NOT_AVAILABLE_TO_PLAYER) {
                if (rageExpectedForPlayer) {
                    // A player Rage should be granted to, but the generic side reports none —
                    // always a real gap, regardless of whatever the legacy sparse map happens to
                    // read; never suppressed as an expected difference.
                    return ClientResourceParityOutcome.MISMATCH;
                }
                // Not expected: the legacy sparse map's 0/0 default is the documented, intentional
                // representation of "never granted" (PlayerChargesComponent's pools map has no entry
                // at all for a non-Barbarian) — only a genuine, well-formed 0/0 legacy read matches
                // that documented shape. Any nonzero legacy value, malformed scale, or nonzero
                // overflow here is stray/stale/corrupt legacy state this policy must not suppress.
                return isExpectedSparseZeroZero(legacy)
                        ? ClientResourceParityOutcome.EXPECTED_SEMANTIC_DIFFERENCE
                        : ClientResourceParityOutcome.MISMATCH;
            }
            return ClientResourceParityOutcome.MODEL_MISMATCH;
        }
        // Generic reports a present value — always comparable normally, regardless of whether Rage
        // was "expected" (a present generic value is never fabricated, so if it exists, comparing it
        // is always meaningful).
        return ClientResourceScalarParityComparator.compare(generic, legacy);
    }

    /**
     * Whether {@code summary} is the exact, well-formed sparse-map "never granted" representation:
     * a {@code Scalar} at {@code 0/0}, zero overflow, and the canonical {@code unitScale = 1}. A
     * malformed scale or nonzero overflow is deliberately <b>not</b> treated as this expected shape —
     * see the class Javadoc's "exact sparse 0/0 representation" note.
     */
    private static boolean isExpectedSparseZeroZero(ClientResourceParitySummary summary) {
        return summary instanceof ClientResourceParitySummary.Scalar scalar
                && scalar.currentUnits() == 0
                && scalar.maximumUnits() == 0
                && scalar.overflowUnits() == 0
                && scalar.unitScale() == ClientResourceScalarParityComparator.CANONICAL_UNIT_SCALE;
    }
}

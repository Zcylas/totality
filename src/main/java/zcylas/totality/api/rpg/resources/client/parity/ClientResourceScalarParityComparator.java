package zcylas.totality.api.rpg.resources.client.parity;

import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import java.util.Objects;

/**
 * Pure scalar parity comparator for Mana, Stamina, and an already-known-present Rage value (see
 * {@link ClientRageParityPolicy}, which delegates here once Rage's own absence/expectation branching
 * has been resolved). Operates purely on already-resolved {@link ClientResourceParitySummary} values
 * — never reads a real client manager, packet, or component — so it is directly unit-testable
 * without a Minecraft client.
 *
 * <p>Never decides {@code TRANSITIONAL_MISMATCH} versus {@code PERSISTENT_MISMATCH}: a numeric
 * disagreement between two otherwise-comparable scalar values is reported as a bare
 * {@link ClientResourceParityOutcome#MISMATCH}, left entirely to {@link ClientResourceParityTracker}'s
 * elapsed-tick grace window to classify further.
 *
 * <p><b>Unit-scale validation (external-review correction):</b> every current Phase 3B-2 parity pair
 * (Mana, Stamina, standard spell slots, Rage) uses canonical {@code unitScale = 1} on both the
 * generic and legacy sides. Before any numeric comparison, both sides must report exactly
 * {@link #CANONICAL_UNIT_SCALE} — a non-canonical scale on either side, or two sides that happen to
 * agree on a shared non-canonical scale, is {@code MODEL_MISMATCH}, never silently converted or
 * compared at either scale. No dedicated {@code UNIT_SCALE_MISMATCH} classification is introduced —
 * scale contradictions fold into the existing {@code MODEL_MISMATCH} vocabulary.
 */
public final class ClientResourceScalarParityComparator {

    /** The canonical unit scale every current Phase 3B-2 scalar parity pair (Mana, Stamina, a present
     *  Rage value) uses on both the generic and legacy sides. */
    public static final long CANONICAL_UNIT_SCALE = 1L;

    private ClientResourceScalarParityComparator() {}

    public static ClientResourceParityOutcome compare(
            ClientResourceParitySummary generic, ClientResourceParitySummary legacy) {
        Objects.requireNonNull(generic, "generic");
        Objects.requireNonNull(legacy, "legacy");

        if (generic instanceof ClientResourceParitySummary.Unavailable unavailable) {
            // NOT_SYNCHRONIZED_YET is the only reliable pre-full readiness gate (see the Phase
            // 3B-2 readiness audit §11 / this slice's "no legacy-readiness fabrication" constraint)
            // — every other Unavailable reason is structurally unexpected for a scalar Resource that
            // has already synced, and must never be numerically compared against.
            return unavailable.reason() == ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET
                    ? ClientResourceParityOutcome.GENERIC_NOT_READY
                    : ClientResourceParityOutcome.MODEL_MISMATCH;
        }
        if (!(generic instanceof ClientResourceParitySummary.Scalar genericScalar)) {
            // Generic reported a partitioned shape where a scalar comparison was expected.
            return ClientResourceParityOutcome.MODEL_MISMATCH;
        }
        if (!(legacy instanceof ClientResourceParitySummary.Scalar legacyScalar)) {
            // Legacy input is not scalar-shaped — a structural contradiction this comparator cannot
            // bridge; never numerically compared.
            return ClientResourceParityOutcome.MODEL_MISMATCH;
        }
        // Unit-scale validation: both sides must be exactly the canonical scale — never merely equal
        // to each other, and never converted. Two sides sharing the same non-canonical scale is still
        // MODEL_MISMATCH (a config/adapter drift this comparator must surface, not paper over).
        if (genericScalar.unitScale() != CANONICAL_UNIT_SCALE || legacyScalar.unitScale() != CANONICAL_UNIT_SCALE) {
            return ClientResourceParityOutcome.MODEL_MISMATCH;
        }
        // Neither side's legacy mirror in this codebase (ClientManaManager/ClientStaminaManager/
        // PlayerChargesComponent's Rage pool) can represent overflow at all — checked on both sides,
        // not just the generic side, before any numeric comparison is attempted.
        if (genericScalar.overflowUnits() != 0 || legacyScalar.overflowUnits() != 0) {
            return ClientResourceParityOutcome.MODEL_MISMATCH;
        }
        if (genericScalar.currentUnits() == legacyScalar.currentUnits()
                && genericScalar.maximumUnits() == legacyScalar.maximumUnits()) {
            return ClientResourceParityOutcome.MATCH;
        }
        return ClientResourceParityOutcome.MISMATCH;
    }
}

package zcylas.totality.api.rpg.resources.client.parity;

import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import java.util.Objects;
import java.util.Optional;

/**
 * Pure parity comparator for {@code totality:spell_slots}. Implements the exact confirmed mapping
 * from the Phase 3B-2 readiness audit §4.1/§10: the generic partition's {@code currentUnits} is
 * <b>remaining</b> slots (({@code maximum - used}, per {@code StandardSpellSlotsResourceAdapter}),
 * not used slots — the exact same quantity {@code ClientSpellSlotManager.getRemaining(level)}
 * already computes. Callers must supply the legacy side pre-converted to this shape (i.e. the
 * caller passes {@code getRemaining(level)} as the legacy partition's {@code currentUnits}, never
 * {@code getUsed}/an internal "used" count) — this comparator does not itself know about a
 * used-slots concept at all, only current/maximum/overflow triples.
 *
 * <p><b>Unit-scale validation (external-review correction):</b> standard spell slots use canonical
 * {@code unitScale = 1} on both sides. Both {@link ClientResourceParitySummary.Partitioned#unitScale()}
 * values must equal {@link #CANONICAL_UNIT_SCALE} — a non-canonical scale on either side, or two
 * sides sharing the same non-canonical scale, is {@code MODEL_MISMATCH}, checked before any
 * per-level comparison is attempted.
 */
public final class ClientSpellSlotParityComparator {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 10;

    /** The canonical unit scale standard spell slots use on both the generic and legacy sides. */
    public static final long CANONICAL_UNIT_SCALE = 1L;

    private ClientSpellSlotParityComparator() {}

    public static ClientResourceParityOutcome compare(
            ClientResourceParitySummary generic, ClientResourceParitySummary legacy) {
        Objects.requireNonNull(generic, "generic");
        Objects.requireNonNull(legacy, "legacy");

        if (generic instanceof ClientResourceParitySummary.Unavailable unavailable) {
            return unavailable.reason() == ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET
                    ? ClientResourceParityOutcome.GENERIC_NOT_READY
                    : ClientResourceParityOutcome.MODEL_MISMATCH;
        }
        if (!(generic instanceof ClientResourceParitySummary.Partitioned genericPartitioned)) {
            return ClientResourceParityOutcome.MODEL_MISMATCH;
        }
        if (!(legacy instanceof ClientResourceParitySummary.Partitioned legacyPartitioned)) {
            return ClientResourceParityOutcome.MODEL_MISMATCH;
        }
        if (genericPartitioned.unitScale() != CANONICAL_UNIT_SCALE || legacyPartitioned.unitScale() != CANONICAL_UNIT_SCALE) {
            return ClientResourceParityOutcome.MODEL_MISMATCH;
        }

        // Every level 1-10 must be present on both sides, with no representable overflow (the
        // legacy int-array manager has no overflow concept at all) — checked by key, never by raw
        // map/array position, so a future reordering cannot silently misalign the comparison.
        boolean anyMismatch = false;
        for (int level = MIN_LEVEL; level <= MAX_LEVEL; level++) {
            Optional<ClientResourceParitySummary.Partitioned.Partition> genericLevel = genericPartitioned.partition(level);
            Optional<ClientResourceParitySummary.Partitioned.Partition> legacyLevel = legacyPartitioned.partition(level);
            if (genericLevel.isEmpty() || legacyLevel.isEmpty()) {
                return ClientResourceParityOutcome.MODEL_MISMATCH;
            }
            ClientResourceParitySummary.Partitioned.Partition generic1 = genericLevel.get();
            ClientResourceParitySummary.Partitioned.Partition legacy1 = legacyLevel.get();
            if (generic1.overflowUnits() > 0 || legacy1.overflowUnits() > 0) {
                return ClientResourceParityOutcome.MODEL_MISMATCH;
            }
            if (generic1.currentUnits() != legacy1.currentUnits() || generic1.maximumUnits() != legacy1.maximumUnits()) {
                anyMismatch = true;
            }
        }

        // A level outside 1-10 on either side (with all of 1-10 also present) is a wire shape the
        // fixed ten-slot legacy array cannot possibly represent.
        for (int key : genericPartitioned.partitions().keySet()) {
            if (key < MIN_LEVEL || key > MAX_LEVEL) {
                return ClientResourceParityOutcome.MODEL_MISMATCH;
            }
        }
        for (int key : legacyPartitioned.partitions().keySet()) {
            if (key < MIN_LEVEL || key > MAX_LEVEL) {
                return ClientResourceParityOutcome.MODEL_MISMATCH;
            }
        }

        return anyMismatch ? ClientResourceParityOutcome.MISMATCH : ClientResourceParityOutcome.MATCH;
    }
}

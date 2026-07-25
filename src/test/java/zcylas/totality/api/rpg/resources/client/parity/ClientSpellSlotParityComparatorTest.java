package zcylas.totality.api.rpg.resources.client.parity;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers test-plan items 8-18: spell-slot partitioned comparison, the confirmed
 * currentUnits=remaining mapping, missing/extra/overflow handling, and deterministic
 * key-based (not positional) comparison.
 */
class ClientSpellSlotParityComparatorTest {

    /** Builds a full ten-level partitioned summary. {@code remaining}/{@code max} are indexed 0
     *  (level 1) through 9 (level 10), fed in the given key order to prove ordering-insensitivity. */
    private static ClientResourceParitySummary.Partitioned tenLevels(int[] remaining, int[] max, boolean reversedInsertionOrder) {
        return tenLevels(remaining, max, reversedInsertionOrder, 1L);
    }

    private static ClientResourceParitySummary.Partitioned tenLevels(
            int[] remaining, int[] max, boolean reversedInsertionOrder, long unitScale) {
        List<ClientResourceParitySummary.Partitioned.Partition> partitions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int level = reversedInsertionOrder ? 10 - i : i + 1;
            int idx = level - 1;
            partitions.add(new ClientResourceParitySummary.Partitioned.Partition(level, remaining[idx], max[idx], 0));
        }
        return ClientResourceParitySummary.Partitioned.of(partitions, unitScale);
    }

    private static int[] filled(int value) {
        int[] arr = new int[10];
        java.util.Arrays.fill(arr, value);
        return arr;
    }

    @Test
    void allZeroTenLevelsIsExactMatch() {
        var generic = tenLevels(filled(0), filled(0), false);
        var legacy = tenLevels(filled(0), filled(0), false);
        assertEquals(ClientResourceParityOutcome.MATCH, ClientSpellSlotParityComparator.compare(generic, legacy));
    }

    @Test
    void currentRepresentsRemainingNotUsed() {
        // Level 3: max=4, used=1 -> remaining=3. Both sides must agree on remaining=3, not used=1.
        int[] max = filled(0);
        int[] remaining = filled(0);
        max[2] = 4;
        remaining[2] = 3; // maximum(4) - used(1) = remaining(3), never the raw used count (1)
        var generic = tenLevels(remaining, max, false);
        var legacy = tenLevels(remaining, max, false);
        assertEquals(ClientResourceParityOutcome.MATCH, ClientSpellSlotParityComparator.compare(generic, legacy));

        // Now assert comparing against the *used* count (1) instead of remaining (3) would mismatch —
        // proving the comparator is sensitive to using the correct field, not tolerant of either.
        int[] usedInstead = filled(0);
        usedInstead[2] = 1;
        var legacyWithUsedMistakenly = tenLevels(usedInstead, max, false);
        assertEquals(ClientResourceParityOutcome.MISMATCH,
                ClientSpellSlotParityComparator.compare(generic, legacyWithUsedMistakenly));
    }

    @Test
    void perLevelCurrentMismatchIsOrdinaryMismatch() {
        int[] max = filled(5);
        int[] genericRemaining = filled(5);
        int[] legacyRemaining = filled(5);
        legacyRemaining[4] = 4; // level 5 differs
        var generic = tenLevels(genericRemaining, max, false);
        var legacy = tenLevels(legacyRemaining, max, false);
        assertEquals(ClientResourceParityOutcome.MISMATCH, ClientSpellSlotParityComparator.compare(generic, legacy));
    }

    @Test
    void perLevelMaximumMismatchIsOrdinaryMismatch() {
        int[] genericMax = filled(5);
        int[] legacyMax = filled(5);
        legacyMax[7] = 6; // level 8 differs
        var generic = tenLevels(filled(5), genericMax, false);
        var legacy = tenLevels(filled(5), legacyMax, false);
        assertEquals(ClientResourceParityOutcome.MISMATCH, ClientSpellSlotParityComparator.compare(generic, legacy));
    }

    @Test
    void comparisonIsKeyBasedNotPositional() {
        var generic = tenLevels(filled(3), filled(5), false);
        var legacyReversedInsertion = tenLevels(filled(3), filled(5), true);
        assertEquals(ClientResourceParityOutcome.MATCH,
                ClientSpellSlotParityComparator.compare(generic, legacyReversedInsertion));
    }

    @Test
    void missingLevelBecomesModelMismatch() {
        List<ClientResourceParitySummary.Partitioned.Partition> nineLevels = new ArrayList<>();
        for (int level = 1; level <= 9; level++) {
            nineLevels.add(new ClientResourceParitySummary.Partitioned.Partition(level, 0, 1, 0));
        }
        var genericMissingLevel10 = ClientResourceParitySummary.Partitioned.of(nineLevels, 1);
        var legacy = tenLevels(filled(0), filled(1), false);
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH,
                ClientSpellSlotParityComparator.compare(genericMissingLevel10, legacy));
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH,
                ClientSpellSlotParityComparator.compare(legacy, genericMissingLevel10));
    }

    @Test
    void extraOutOfRangeLevelBecomesModelMismatch() {
        List<ClientResourceParitySummary.Partitioned.Partition> elevenLevels = new ArrayList<>();
        for (int level = 1; level <= 10; level++) {
            elevenLevels.add(new ClientResourceParitySummary.Partitioned.Partition(level, 0, 1, 0));
        }
        elevenLevels.add(new ClientResourceParitySummary.Partitioned.Partition(11, 0, 1, 0));
        var genericWithExtraLevel = ClientResourceParitySummary.Partitioned.of(elevenLevels, 1);
        var legacy = tenLevels(filled(0), filled(1), false);
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH,
                ClientSpellSlotParityComparator.compare(genericWithExtraLevel, legacy));
    }

    @Test
    void nonzeroGenericOverflowBecomesModelMismatch() {
        List<ClientResourceParitySummary.Partitioned.Partition> partitions = new ArrayList<>();
        for (int level = 1; level <= 10; level++) {
            long overflow = level == 5 ? 1 : 0;
            partitions.add(new ClientResourceParitySummary.Partitioned.Partition(level, 0, 1, overflow));
        }
        var genericWithOverflow = ClientResourceParitySummary.Partitioned.of(partitions, 1);
        var legacy = tenLevels(filled(0), filled(1), false);
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH,
                ClientSpellSlotParityComparator.compare(genericWithOverflow, legacy));
    }

    @Test
    void genericNotSynchronizedYetBecomesGenericNotReady() {
        var generic = new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET);
        var legacy = tenLevels(filled(0), filled(1), false);
        assertEquals(ClientResourceParityOutcome.GENERIC_NOT_READY,
                ClientSpellSlotParityComparator.compare(generic, legacy));
    }

    @Test
    void scalarShapeContradictionBecomesModelMismatch() {
        var genericScalar = new ClientResourceParitySummary.Scalar(1, 1, 0, 1);
        var legacy = tenLevels(filled(0), filled(1), false);
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH,
                ClientSpellSlotParityComparator.compare(genericScalar, legacy));
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH,
                ClientSpellSlotParityComparator.compare(legacy, genericScalar));
    }

    // ── External-review correction 1: unit-scale validation ─────────────────────────────────────

    @Test
    void differentUnitScalesBecomeModelMismatchEvenWhenLevelsAgree() {
        var generic = tenLevels(filled(3), filled(5), false, 1L);
        var legacy = tenLevels(filled(3), filled(5), false, 1000L);
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH,
                ClientSpellSlotParityComparator.compare(generic, legacy));
    }

    @Test
    void bothSidesSharingTheSameNonCanonicalScaleStillBecomesModelMismatch() {
        var generic = tenLevels(filled(3), filled(5), false, 1000L);
        var legacy = tenLevels(filled(3), filled(5), false, 1000L);
        assertEquals(ClientResourceParityOutcome.MODEL_MISMATCH,
                ClientSpellSlotParityComparator.compare(generic, legacy));
    }

    @Test
    void canonicalUnitScaleConstantIsOne() {
        assertEquals(1L, ClientSpellSlotParityComparator.CANONICAL_UNIT_SCALE);
    }
}

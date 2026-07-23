package zcylas.totality.api.rpg.resources.client;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests 1-8 of the Phase 3B-1 task: scalar/partitioned construction, validation, immutability,
 * ordering, overflow preservation, and duplicate/negative rejection.
 */
class ClientResourceQueryResultTest {

    private static final Identifier MANA = Identifier.fromNamespaceAndPath("totality", "mana");
    private static final Identifier SLOTS = Identifier.fromNamespaceAndPath("totality", "spell_slots");

    // 1. Scalar success construction and validation.
    @Test
    void scalarSuccessConstructsWithValidValues() {
        var scalar = new ClientResourceQueryResult.Scalar(
                MANA, 40L, 100L, 0L, 1L, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.FRESH);
        assertEquals(MANA, scalar.resourceId());
        assertEquals(40L, scalar.currentUnits());
        assertEquals(100L, scalar.maximumUnits());
        assertEquals(ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, scalar.source());
        assertEquals(ClientResourceTrust.FRESH, scalar.trust());
    }

    @Test
    void scalarRejectsUnitScaleBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceQueryResult.Scalar(
                MANA, 1L, 1L, 0L, 0L, ClientResourceSource.NATIVE_CLIENT_VIEW, ClientResourceTrust.FRESH));
    }

    // 2. Partitioned success construction and validation.
    @Test
    void partitionedSuccessConstructsWithValidValues() {
        NavigableMap<Integer, ClientResourceQueryResult.Partitioned.Partition> partitions = new TreeMap<>();
        partitions.put(1, new ClientResourceQueryResult.Partitioned.Partition(1, 2, 4, 0));
        var partitioned = new ClientResourceQueryResult.Partitioned(
                SLOTS, partitions, 1L, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.FRESH);
        assertEquals(SLOTS, partitioned.resourceId());
        assertEquals(2L, partitioned.partition(1).orElseThrow().currentUnits());
    }

    @Test
    void partitionedRejectsKeyMismatchedWithPartitionId() {
        NavigableMap<Integer, ClientResourceQueryResult.Partitioned.Partition> partitions = new TreeMap<>();
        // Partition claims id 2 but is stored under key 1 — structurally contradictory input.
        partitions.put(1, new ClientResourceQueryResult.Partitioned.Partition(2, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceQueryResult.Partitioned(
                SLOTS, partitions, 1L, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.FRESH));
    }

    // 3. Partition map is immutable.
    @Test
    void partitionMapIsImmutable() {
        var partitioned = ClientResourceQueryResult.Partitioned.of(
                SLOTS,
                List.of(new ClientResourceQueryResult.Partitioned.Partition(1, 0, 2, 0)),
                1L, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.FRESH);

        assertThrows(UnsupportedOperationException.class,
                () -> partitioned.partitions().put(2, new ClientResourceQueryResult.Partitioned.Partition(2, 0, 1, 0)));
    }

    // 4. Partitions are deterministically ordered.
    @Test
    void partitionsAreOrderedAscendingRegardlessOfInsertionOrder() {
        var partitioned = ClientResourceQueryResult.Partitioned.of(
                SLOTS,
                List.of(
                        new ClientResourceQueryResult.Partitioned.Partition(3, 0, 1, 0),
                        new ClientResourceQueryResult.Partitioned.Partition(1, 0, 2, 0),
                        new ClientResourceQueryResult.Partitioned.Partition(2, 0, 3, 0)),
                1L, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.FRESH);

        assertEquals(List.of(1, 2, 3), List.copyOf(partitioned.partitions().keySet()));
    }

    // 5. Scalar overflow is preserved.
    @Test
    void scalarOverflowIsPreserved() {
        var scalar = new ClientResourceQueryResult.Scalar(
                MANA, 100L, 100L, 25L, 1L, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.FRESH);
        assertEquals(25L, scalar.overflowUnits());
    }

    // 6. Partition overflow is preserved.
    @Test
    void partitionOverflowIsPreserved() {
        var partition = new ClientResourceQueryResult.Partitioned.Partition(1, 4, 4, 2);
        assertEquals(2L, partition.overflowUnits());
    }

    // 7. Duplicate partition IDs are rejected.
    @Test
    void duplicatePartitionIdsAreRejectedByOf() {
        assertThrows(IllegalArgumentException.class, () -> ClientResourceQueryResult.Partitioned.of(
                SLOTS,
                List.of(
                        new ClientResourceQueryResult.Partitioned.Partition(1, 0, 1, 0),
                        new ClientResourceQueryResult.Partitioned.Partition(1, 0, 2, 0)),
                1L, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.FRESH));
    }

    // 8. Negative or structurally invalid client values are rejected.
    @Test
    void scalarRejectsNegativeCurrent() {
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceQueryResult.Scalar(
                MANA, -1L, 100L, 0L, 1L, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.FRESH));
    }

    @Test
    void scalarRejectsCurrentExceedingMaximumPlusOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceQueryResult.Scalar(
                MANA, 101L, 100L, 0L, 1L, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.FRESH));
    }

    @Test
    void partitionRejectsNegativeMaximum() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientResourceQueryResult.Partitioned.Partition(1, 0, -1, 0));
    }

    @Test
    void partitionRejectsCurrentExceedingMaximumPlusOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientResourceQueryResult.Partitioned.Partition(1, 5, 4, 0));
    }

    @Test
    void unavailableRequiresNonNullReason() {
        assertThrows(NullPointerException.class, () -> new ClientResourceQueryResult.Unavailable(MANA, null));
    }
}

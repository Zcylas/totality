package zcylas.totality.api.rpg.resources.client.parity;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientResourceParitySummaryTest {

    @Test
    void scalarRejectsUnitScaleBelowOne() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientResourceParitySummary.Scalar(0, 0, 0, 0));
    }

    @Test
    void scalarRejectsNegativeQuantities() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientResourceParitySummary.Scalar(-1, 100, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientResourceParitySummary.Scalar(0, -1, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientResourceParitySummary.Scalar(0, 100, -1, 1));
    }

    @Test
    void scalarRejectsCurrentAboveMaximumPlusOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientResourceParitySummary.Scalar(101, 100, 0, 1));
    }

    @Test
    void scalarAcceptsCurrentWithinOverflow() {
        var scalar = new ClientResourceParitySummary.Scalar(150, 100, 50, 1);
        assertEquals(150, scalar.currentUnits());
    }

    @Test
    void partitionRejectsNegativeQuantities() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientResourceParitySummary.Partitioned.Partition(1, -1, 10, 0));
    }

    @Test
    void partitionRejectsCurrentAboveMaximum() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientResourceParitySummary.Partitioned.Partition(1, 11, 10, 0));
    }

    @Test
    void partitionedOfRejectsDuplicatePartitionId() {
        var partitions = List.of(
                new ClientResourceParitySummary.Partitioned.Partition(1, 0, 1, 0),
                new ClientResourceParitySummary.Partitioned.Partition(1, 0, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ClientResourceParitySummary.Partitioned.of(partitions, 1));
    }

    @Test
    void partitionedRejectsMismatchedKeyAndPartitionId() {
        NavigableMap<Integer, ClientResourceParitySummary.Partitioned.Partition> map = new TreeMap<>();
        map.put(2, new ClientResourceParitySummary.Partitioned.Partition(1, 0, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ClientResourceParitySummary.Partitioned(map, 1));
    }

    @Test
    void partitionedOrdersAscendingRegardlessOfInsertionOrder() {
        var partitions = List.of(
                new ClientResourceParitySummary.Partitioned.Partition(3, 0, 1, 0),
                new ClientResourceParitySummary.Partitioned.Partition(1, 0, 1, 0),
                new ClientResourceParitySummary.Partitioned.Partition(2, 0, 1, 0));
        var partitioned = ClientResourceParitySummary.Partitioned.of(partitions, 1);
        assertEquals(List.of(1, 2, 3), List.copyOf(partitioned.partitions().keySet()));
    }

    @Test
    void partitionedMapIsImmutable() {
        var partitioned = ClientResourceParitySummary.Partitioned.of(
                List.of(new ClientResourceParitySummary.Partitioned.Partition(1, 0, 1, 0)), 1);
        assertThrows(UnsupportedOperationException.class,
                () -> partitioned.partitions().put(2, new ClientResourceParitySummary.Partitioned.Partition(2, 0, 1, 0)));
    }

    @Test
    void partitionLookupReturnsEmptyForMissingKey() {
        var partitioned = ClientResourceParitySummary.Partitioned.of(
                List.of(new ClientResourceParitySummary.Partitioned.Partition(1, 0, 1, 0)), 1);
        assertTrue(partitioned.partition(5).isEmpty());
    }

    @Test
    void unavailableRequiresNonNullReason() {
        assertThrows(NullPointerException.class, () -> new ClientResourceParitySummary.Unavailable(null));
    }
}

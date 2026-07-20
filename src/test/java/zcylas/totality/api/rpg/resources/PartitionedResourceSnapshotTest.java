package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Construction/invariant tests for {@link PartitionedResourceSnapshot}, the Phase 2D partitioned
 * query-result shape. Deliberately generic — uses arbitrary partition keys, not spell levels, to
 * prove this type has no owner-specific knowledge baked in.
 */
class PartitionedResourceSnapshotTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "fake_partitioned");

    @Test
    void constructsSuccessfullyFromAnOrderedMap() {
        Map<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> source = new LinkedHashMap<>();
        source.put(1, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(3, 4));
        source.put(2, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(0, 2));

        PartitionedResourceSnapshot snapshot = new PartitionedResourceSnapshot(ID, new TreeMap<>(source), 1);

        assertEquals(ID, snapshot.resourceId());
        assertEquals(1L, snapshot.unitScale());
        assertEquals(2, snapshot.partitions().size());
    }

    @Test
    void enforcesDeterministicAscendingOrderRegardlessOfInsertionOrder() {
        // A previous version of this test wrapped the out-of-order source in `new TreeMap<>(source)`
        // using natural ordering — which itself immediately re-sorts to ascending order before the
        // snapshot's constructor ever sees it, so the test proved nothing about the constructor's own
        // reordering behavior. This version instead uses a genuinely non-ascending NavigableMap — a
        // TreeMap ordered by Comparator.reverseOrder() — so the input the constructor actually
        // receives iterates descending, and only the constructor's own defensive TreeMap copy (using
        // natural/ascending order, per PartitionedResourceSnapshot's own compact constructor) can be
        // responsible for the ascending result asserted below.
        java.util.NavigableMap<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> descending =
                new TreeMap<>(java.util.Comparator.reverseOrder());
        descending.put(9, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(0, 1));
        descending.put(1, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(0, 2));
        descending.put(5, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(0, 1));

        // Sanity check on the test's own premise: the source map itself must genuinely iterate
        // descending (9, 5, 1) before it is ever handed to the snapshot constructor.
        assertEquals(List.of(9, 5, 1), List.copyOf(descending.keySet()),
                "sanity: the source map must iterate descending before construction");

        PartitionedResourceSnapshot snapshot = new PartitionedResourceSnapshot(ID, descending, 1);

        assertEquals(List.of(1, 5, 9), List.copyOf(snapshot.partitions().keySet()),
                "the snapshot's own constructor must produce ascending order regardless of the source map's order");
    }

    @Test
    void producesADefensiveCopyNotAliasingTheInputMap() {
        TreeMap<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> mutable = new TreeMap<>();
        mutable.put(1, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(0, 1));

        PartitionedResourceSnapshot snapshot = new PartitionedResourceSnapshot(ID, mutable, 1);
        mutable.put(2, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(0, 1));

        assertEquals(1, snapshot.partitions().size(),
                "mutating the caller's original map after construction must not affect the snapshot");
    }

    @Test
    void resultingMapIsImmutable() {
        TreeMap<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> mutable = new TreeMap<>();
        mutable.put(1, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(0, 1));
        PartitionedResourceSnapshot snapshot = new PartitionedResourceSnapshot(ID, mutable, 1);

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.partitions().put(2, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(0, 1)));
    }

    @Test
    void nullPartitionKeyIsRejected() {
        // A plain map implementation (LinkedHashMap) permits a null key, unlike TreeMap — the
        // resulting map must still be rejected by PartitionedResourceSnapshot's own compact
        // constructor when wrapped and passed through, proving the guard is the snapshot's own,
        // not merely an incidental property of whichever map the caller happened to use.
        Map<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> withNullKey = new LinkedHashMap<>();
        withNullKey.put(null, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(0, 1));

        assertThrows(NullPointerException.class, () -> new PartitionedResourceSnapshot(ID, asNavigable(withNullKey), 1));
    }

    @Test
    void nullPartitionValueIsRejected() {
        Map<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> withNullValue = new LinkedHashMap<>();
        withNullValue.put(1, null);

        assertThrows(NullPointerException.class, () -> new PartitionedResourceSnapshot(ID, asNavigable(withNullValue), 1));
    }

    @Test
    void nullResourceIdIsRejected() {
        assertThrows(NullPointerException.class, () -> new PartitionedResourceSnapshot(null, new TreeMap<>(), 1));
    }

    @Test
    void nullPartitionsMapIsRejected() {
        assertThrows(NullPointerException.class, () -> new PartitionedResourceSnapshot(ID, null, 1));
    }

    @Test
    void unitScaleBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PartitionedResourceSnapshot(ID, new TreeMap<>(), 0));
    }

    @Test
    void emptyPartitionsMapIsAllowedByThisGenericType() {
        // PartitionedResourceSnapshot itself has no opinion on "must have N partitions" — that
        // resource-specific invariant belongs to the adapter (see StandardSpellSlotsResourceAdapterTest
        // for the spell-slot-specific "exactly 1..10" guarantee), not this generic shape.
        PartitionedResourceSnapshot snapshot = new PartitionedResourceSnapshot(ID, new TreeMap<>(), 1);
        assertTrue(snapshot.partitions().isEmpty());
    }

    @Test
    void partitionLookupReturnsEmptyForAnAbsentKey() {
        PartitionedResourceSnapshot snapshot = new PartitionedResourceSnapshot(ID, new TreeMap<>(), 1);
        assertTrue(snapshot.partition(1).isEmpty());
    }

    @Test
    void partitionLookupReturnsThePresentValue() {
        TreeMap<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> map = new TreeMap<>();
        map.put(3, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(7, 9));
        PartitionedResourceSnapshot snapshot = new PartitionedResourceSnapshot(ID, map, 1);

        assertEquals(7L, snapshot.partition(3).orElseThrow().currentUnits());
        assertEquals(9L, snapshot.partition(3).orElseThrow().maximumUnits());
    }

    /** A {@code Map} cannot structurally contain a duplicate key, so "duplicate rejection" is proven
     *  by construction rather than by an explicit runtime check — this test documents that fact. */
    @Test
    void duplicatePartitionIdentitiesAreImpossibleByConstruction() {
        TreeMap<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> map = new TreeMap<>();
        map.put(1, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(1, 2));
        map.put(1, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(2, 2)); // overwrites, not duplicates

        PartitionedResourceSnapshot snapshot = new PartitionedResourceSnapshot(ID, map, 1);

        assertEquals(1, snapshot.partitions().size());
        assertEquals(2L, snapshot.partition(1).orElseThrow().currentUnits());
    }

    @SuppressWarnings("unchecked")
    private static java.util.NavigableMap<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> asNavigable(
            Map<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> map) {
        // Wraps a plain (possibly null-key/value-permitting) Map as a NavigableMap purely to satisfy
        // the constructor's declared parameter type — iteration order doesn't matter for these
        // rejection tests, and TreeMap itself would reject the null before the constructor's own
        // loop ever ran, which is exactly why LinkedHashMap is used as the source here instead.
        java.util.NavigableMap<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> navigable = new java.util.TreeMap<>(
                java.util.Comparator.nullsFirst(Integer::compareTo));
        for (var entry : map.entrySet()) {
            navigable.put(entry.getKey(), entry.getValue());
        }
        return navigable;
    }
}

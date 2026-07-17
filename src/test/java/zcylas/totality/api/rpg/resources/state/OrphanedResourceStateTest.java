package zcylas.totality.api.rpg.resources.state;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.ResourceModel;

import static org.junit.jupiter.api.Assertions.*;

class OrphanedResourceStateTest {

    @Test
    void unknownPartitionDefaultsToZero() {
        OrphanedResourceState orphan = new OrphanedResourceState(ResourceModel.SCALAR);
        assertEquals(0, orphan.getCurrent(0));
        assertEquals(0, orphan.getOverflow(0));
        assertEquals(0, orphan.scalarRegenerationRemainder());
        assertTrue(orphan.partitions().isEmpty());
    }

    @Test
    void scalarRegenerationRemainderIsPreserved() {
        OrphanedResourceState orphan = new OrphanedResourceState(ResourceModel.SCALAR);
        orphan.setScalarRegenerationRemainder(7);
        assertEquals(7, orphan.scalarRegenerationRemainder());
    }

    @Test
    void partitionsIsTheUnionOfCurrentAndOverflowKeys() {
        OrphanedResourceState orphan = new OrphanedResourceState(ResourceModel.PARTITIONED_POOL);
        orphan.putCurrent(1, 4);
        // partition 2 has ONLY an overflow entry, no current entry.
        orphan.putOverflow(2, 9);

        assertEquals(java.util.Set.of(1, 2), orphan.partitions(),
                "an overflow-only partition must still appear in partitions()");
        assertEquals(0, orphan.getCurrent(2), "partition 2 never had a current value");
        assertEquals(9, orphan.getOverflow(2));
    }

    @Test
    void copyIsIndependentOfOriginal() {
        OrphanedResourceState original = new OrphanedResourceState(ResourceModel.SCALAR);
        original.putCurrent(0, 42);
        original.putOverflow(0, 3);
        original.setScalarRegenerationRemainder(1);

        OrphanedResourceState copy = original.copy();
        assertNotSame(original, copy);
        assertEquals(42, copy.getCurrent(0));
        assertEquals(3, copy.getOverflow(0));
        assertEquals(1, copy.scalarRegenerationRemainder());

        copy.putCurrent(0, 999);
        copy.setScalarRegenerationRemainder(999);
        assertEquals(42, original.getCurrent(0), "mutating the copy must not affect the original");
        assertEquals(1, original.scalarRegenerationRemainder(), "mutating the copy must not affect the original");

        original.putCurrent(0, 111);
        assertEquals(999, copy.getCurrent(0), "mutating the original after copy must not affect the copy");
    }

    @Test
    void copyPreservesOverflowOnlyPartitions() {
        OrphanedResourceState original = new OrphanedResourceState(ResourceModel.PARTITIONED_POOL);
        original.putOverflow(5, 20); // no current entry at partition 5

        OrphanedResourceState copy = original.copy();
        assertEquals(20, copy.getOverflow(5));
        assertEquals(java.util.Set.of(5), copy.partitions());
    }
}

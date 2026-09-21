package zcylas.totality.api.rpg.resources.state;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.ResourceModel;

import static org.junit.jupiter.api.Assertions.*;

class PartitionedResourceStateTest {

    @Test
    void unknownPartitionDefaultsToZero() {
        PartitionedResourceState state = new PartitionedResourceState();
        assertEquals(0, state.getCurrent(3));
        assertEquals(0, state.getOverflow(3));
        assertTrue(state.partitions().isEmpty());
        assertEquals(ResourceModel.PARTITIONED_POOL, state.model());
    }

    @Test
    void setAndGetAreIndependentPerPartition() {
        PartitionedResourceState state = new PartitionedResourceState();
        state.setCurrent(1, 4);
        state.setCurrent(3, 2);
        state.setOverflow(1, 1);

        assertEquals(4, state.getCurrent(1));
        assertEquals(2, state.getCurrent(3));
        assertEquals(1, state.getOverflow(1));
        assertEquals(0, state.getOverflow(3));
        assertEquals(java.util.Set.of(1, 3), state.partitions());
    }

    @Test
    void copyProducesIndependentInstance() {
        PartitionedResourceState original = new PartitionedResourceState();
        original.setCurrent(5, 10);
        original.setOverflow(5, 1);

        PartitionedResourceState copy = original.copy();
        assertNotSame(original, copy);
        assertEquals(10, copy.getCurrent(5));
        assertEquals(1, copy.getOverflow(5));

        copy.setCurrent(5, 999);
        assertEquals(10, original.getCurrent(5), "mutating the copy must not affect the original");
    }

    @Test
    void partitionsIsTheUnionOfCurrentAndOverflowKeys() {
        PartitionedResourceState state = new PartitionedResourceState();
        state.setCurrent(1, 4);
        // partition 2 has ONLY an overflow entry, no current entry.
        state.setOverflow(2, 9);

        assertEquals(java.util.Set.of(1, 2), state.partitions(),
                "an overflow-only partition must still appear in partitions()");
        assertEquals(0, state.getCurrent(2), "partition 2 never had a current value");
        assertEquals(9, state.getOverflow(2));
    }

    @Test
    void copyPreservesOverflowOnlyPartition() {
        PartitionedResourceState original = new PartitionedResourceState();
        original.setOverflow(7, 15); // no current entry at partition 7

        PartitionedResourceState copy = original.copy();
        assertEquals(15, copy.getOverflow(7));
        assertEquals(java.util.Set.of(7), copy.partitions(),
                "an overflow-only partition must survive copy()");
    }

    @Test
    void hitDiceStyleMultiPartitionScenario() {
        // Wizard 5 / Barbarian 3 -> five d6, three d12 (canonical §25.10 example).
        PartitionedResourceState hitDice = new PartitionedResourceState();
        hitDice.setCurrent(6, 5);
        hitDice.setCurrent(12, 3);

        assertEquals(5, hitDice.getCurrent(6));
        assertEquals(3, hitDice.getCurrent(12));
        assertEquals(0, hitDice.getCurrent(8), "d8 was never contributed to, must remain 0");
    }
}

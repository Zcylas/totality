package zcylas.totality.api.rpg.resources.sync;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PartitionedResourceSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class ResourcePartitionedWireSnapshotTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "fake_partitioned");

    @Test
    void codecRoundTripsExactly() {
        List<ResourcePartitionWireEntry> entries = List.of(
                new ResourcePartitionWireEntry(1, 2, 4, 0),
                new ResourcePartitionWireEntry(2, 0, 4, 0));
        ResourcePartitionedWireSnapshot original = new ResourcePartitionedWireSnapshot(ID, 1L, entries);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ResourcePartitionedWireSnapshot.write(buf, original);
        ResourcePartitionedWireSnapshot decoded = ResourcePartitionedWireSnapshot.read(buf);

        assertEquals(original, decoded);
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void deterministicAscendingOrderRegardlessOfInputOrder() {
        List<ResourcePartitionWireEntry> outOfOrder = List.of(
                new ResourcePartitionWireEntry(9, 0, 1, 0),
                new ResourcePartitionWireEntry(1, 0, 1, 0),
                new ResourcePartitionWireEntry(5, 0, 1, 0));

        ResourcePartitionedWireSnapshot snapshot = new ResourcePartitionedWireSnapshot(ID, 1L, outOfOrder);

        List<Integer> order = snapshot.partitions().stream().map(ResourcePartitionWireEntry::partition).toList();
        assertEquals(List.of(1, 5, 9), order);
    }

    @Test
    void duplicatePartitionIdsAreRejected() {
        List<ResourcePartitionWireEntry> duplicate = List.of(
                new ResourcePartitionWireEntry(1, 0, 1, 0),
                new ResourcePartitionWireEntry(1, 0, 2, 0));

        assertThrows(IllegalArgumentException.class, () -> new ResourcePartitionedWireSnapshot(ID, 1L, duplicate));
    }

    @Test
    void tenSpellSlotLevelsSurviveConversionFromAPartitionedResourceSnapshot() {
        TreeMap<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> partitions = new TreeMap<>();
        for (int level = 1; level <= 10; level++) {
            partitions.put(level, new PartitionedResourceSnapshot.ResourcePartitionSnapshot(0, level));
        }
        PartitionedResourceSnapshot snapshot = new PartitionedResourceSnapshot(ID, partitions, 1L);

        ResourcePartitionedWireSnapshot wire = ResourcePartitionedWireSnapshot.from(snapshot);

        assertEquals(10, wire.partitions().size());
        List<Integer> order = wire.partitions().stream().map(ResourcePartitionWireEntry::partition).toList();
        assertEquals(new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)), order);
    }

    @Test
    void unitScaleBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ResourcePartitionedWireSnapshot(ID, 0L, List.of()));
    }

    @Test
    void nullResourceIdIsRejected() {
        assertThrows(NullPointerException.class, () -> new ResourcePartitionedWireSnapshot(null, 1L, List.of()));
    }

    @Test
    void currentAboveMaximumIsRejectedOnTheEntry() {
        assertThrows(IllegalArgumentException.class, () -> new ResourcePartitionWireEntry(1, 5, 4, 0));
    }

    @Test
    void oversizedPartitionCountIsRejectedBeforeAllocating() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeIdentifier(ID);
        buf.writeLong(1L);
        buf.writeVarInt(ResourceSyncProtocol.MAX_PARTITION_ENTRIES + 1);

        assertThrows(IllegalArgumentException.class, () -> ResourcePartitionedWireSnapshot.read(buf));
    }
}

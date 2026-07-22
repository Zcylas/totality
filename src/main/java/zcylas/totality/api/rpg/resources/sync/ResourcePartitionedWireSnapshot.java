package zcylas.totality.api.rpg.resources.sync;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.PartitionedResourceSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A client-safe, wire-shaped snapshot of one {@code PARTITIONED_POOL} resource (e.g. standard spell
 * slots). Partitions are always stored/serialized in deterministic ascending integer order,
 * regardless of the order supplied to the constructor — mirroring
 * {@link PartitionedResourceSnapshot}'s own guarantee at the query layer.
 */
public record ResourcePartitionedWireSnapshot(
        Identifier resourceId,
        long unitScale,
        List<ResourcePartitionWireEntry> partitions
) {

    public ResourcePartitionedWireSnapshot {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(partitions, "partitions");
        if (unitScale < 1) {
            throw new IllegalArgumentException("unitScale must be >= 1, was " + unitScale);
        }
        TreeMap<Integer, ResourcePartitionWireEntry> ordered = new TreeMap<>();
        for (ResourcePartitionWireEntry entry : partitions) {
            Objects.requireNonNull(entry, "partition entry must not be null");
            if (ordered.put(entry.partition(), entry) != null) {
                throw new IllegalArgumentException(
                        "duplicate partition id " + entry.partition() + " for resource " + resourceId);
            }
        }
        partitions = List.copyOf(ordered.values());
    }

    public static ResourcePartitionedWireSnapshot from(PartitionedResourceSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<ResourcePartitionWireEntry> entries = new ArrayList<>(snapshot.partitions().size());
        for (Map.Entry<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> entry :
                snapshot.partitions().entrySet()) {
            entries.add(new ResourcePartitionWireEntry(
                    entry.getKey(), entry.getValue().currentUnits(), entry.getValue().maximumUnits(), 0L));
        }
        return new ResourcePartitionedWireSnapshot(snapshot.resourceId(), snapshot.unitScale(), entries);
    }

    public static void write(FriendlyByteBuf buf, ResourcePartitionedWireSnapshot value) {
        buf.writeIdentifier(value.resourceId());
        buf.writeLong(value.unitScale());
        buf.writeVarInt(value.partitions().size());
        for (ResourcePartitionWireEntry entry : value.partitions()) {
            ResourcePartitionWireEntry.write(buf, entry);
        }
    }

    public static ResourcePartitionedWireSnapshot read(FriendlyByteBuf buf) {
        Identifier id = buf.readIdentifier();
        long unitScale = buf.readLong();
        int size = buf.readVarInt();
        if (size < 0 || size > ResourceSyncProtocol.MAX_PARTITION_ENTRIES) {
            throw new IllegalArgumentException(
                    "partition entry count out of bounds: " + size + " (max " + ResourceSyncProtocol.MAX_PARTITION_ENTRIES + ")");
        }
        List<ResourcePartitionWireEntry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(ResourcePartitionWireEntry.read(buf));
        }
        return new ResourcePartitionedWireSnapshot(id, unitScale, entries);
    }
}

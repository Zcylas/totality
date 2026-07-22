package zcylas.totality.networking.resource;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.resources.sync.ResourcePartitionedWireSnapshot;
import zcylas.totality.api.rpg.resources.sync.ResourceScalarWireSnapshot;
import zcylas.totality.api.rpg.resources.sync.ResourceSyncProtocol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * S2C — the complete synchronized generic Resource view for one player/connection. Applying this
 * payload replaces the client's entire previously synchronized view: an entry omitted here is
 * absent afterward, and {@link #revision()} establishes the base for subsequent
 * {@link ResourceDeltaSyncPayload}s. See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §18.2.
 */
public record ResourceFullSyncPayload(
        int schemaVersion,
        long revision,
        List<ResourceScalarWireSnapshot> scalars,
        List<ResourcePartitionedWireSnapshot> partitioned
) implements CustomPacketPayload {

    public ResourceFullSyncPayload {
        Objects.requireNonNull(scalars, "scalars");
        Objects.requireNonNull(partitioned, "partitioned");
        scalars = List.copyOf(scalars);
        partitioned = List.copyOf(partitioned);

        Set<Identifier> seen = new HashSet<>();
        for (ResourceScalarWireSnapshot s : scalars) {
            if (!seen.add(s.resourceId())) {
                throw new IllegalArgumentException("duplicate resource id in full snapshot: " + s.resourceId());
            }
        }
        for (ResourcePartitionedWireSnapshot p : partitioned) {
            if (!seen.add(p.resourceId())) {
                throw new IllegalArgumentException("duplicate resource id in full snapshot: " + p.resourceId());
            }
        }
    }

    public static final CustomPacketPayload.Type<ResourceFullSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "resource_sync_full"));

    public static final StreamCodec<FriendlyByteBuf, ResourceFullSyncPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.schemaVersion());
                buf.writeLong(payload.revision());
                buf.writeVarInt(payload.scalars().size());
                for (ResourceScalarWireSnapshot s : payload.scalars()) {
                    ResourceScalarWireSnapshot.write(buf, s);
                }
                buf.writeVarInt(payload.partitioned().size());
                for (ResourcePartitionedWireSnapshot p : payload.partitioned()) {
                    ResourcePartitionedWireSnapshot.write(buf, p);
                }
            },
            buf -> {
                int schemaVersion = buf.readVarInt();
                long revision = buf.readLong();
                int scalarCount = buf.readVarInt();
                if (scalarCount < 0 || scalarCount > ResourceSyncProtocol.MAX_RESOURCE_ENTRIES) {
                    throw new IllegalArgumentException(
                            "scalar count out of bounds: " + scalarCount + " (max " + ResourceSyncProtocol.MAX_RESOURCE_ENTRIES + ")");
                }
                List<ResourceScalarWireSnapshot> scalars = new ArrayList<>(scalarCount);
                for (int i = 0; i < scalarCount; i++) {
                    scalars.add(ResourceScalarWireSnapshot.read(buf));
                }
                int partitionedCount = buf.readVarInt();
                if (partitionedCount < 0 || partitionedCount > ResourceSyncProtocol.MAX_RESOURCE_ENTRIES) {
                    throw new IllegalArgumentException(
                            "partitioned count out of bounds: " + partitionedCount + " (max " + ResourceSyncProtocol.MAX_RESOURCE_ENTRIES + ")");
                }
                List<ResourcePartitionedWireSnapshot> partitioned = new ArrayList<>(partitionedCount);
                for (int i = 0; i < partitionedCount; i++) {
                    partitioned.add(ResourcePartitionedWireSnapshot.read(buf));
                }
                return new ResourceFullSyncPayload(schemaVersion, revision, scalars, partitioned);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

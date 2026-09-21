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
 * S2C — one coalesced batch of generic Resource changes for a single server tick: upserts (new or
 * changed) plus explicit invalidations (no-longer-present/unavailable). Only ever applied by the
 * client if {@link #baseRevision()} matches its currently held revision — see
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §18.2/§18.5 and
 * {@code zcylas.totality.api.rpg.resources.sync.ClientResourceSyncState}.
 */
public record ResourceDeltaSyncPayload(
        int schemaVersion,
        long baseRevision,
        long revision,
        List<ResourceScalarWireSnapshot> upsertScalars,
        List<ResourcePartitionedWireSnapshot> upsertPartitioned,
        List<Identifier> invalidated
) implements CustomPacketPayload {

    public ResourceDeltaSyncPayload {
        Objects.requireNonNull(upsertScalars, "upsertScalars");
        Objects.requireNonNull(upsertPartitioned, "upsertPartitioned");
        Objects.requireNonNull(invalidated, "invalidated");
        // One ordinary delta batch advances the unified view revision exactly once, so revision
        // must be exactly baseRevision + 1 — never a larger jump. baseRevision == Long.MAX_VALUE is
        // rejected outright rather than computed against, since baseRevision + 1 would silently
        // wrap around to Long.MIN_VALUE.
        if (baseRevision == Long.MAX_VALUE) {
            throw new IllegalArgumentException("baseRevision must not be Long.MAX_VALUE (would overflow)");
        }
        if (revision != baseRevision + 1) {
            throw new IllegalArgumentException(
                    "revision must equal baseRevision + 1, was baseRevision=" + baseRevision + " revision=" + revision);
        }
        upsertScalars = List.copyOf(upsertScalars);
        upsertPartitioned = List.copyOf(upsertPartitioned);
        invalidated = List.copyOf(invalidated);

        Set<Identifier> upserted = new HashSet<>();
        for (ResourceScalarWireSnapshot s : upsertScalars) {
            if (!upserted.add(s.resourceId())) {
                throw new IllegalArgumentException("duplicate resource id in delta upserts: " + s.resourceId());
            }
        }
        for (ResourcePartitionedWireSnapshot p : upsertPartitioned) {
            if (!upserted.add(p.resourceId())) {
                throw new IllegalArgumentException("duplicate resource id in delta upserts: " + p.resourceId());
            }
        }
        Set<Identifier> invalidatedSeen = new HashSet<>();
        for (Identifier id : invalidated) {
            if (!invalidatedSeen.add(id)) {
                throw new IllegalArgumentException("duplicate resource id in delta invalidations: " + id);
            }
            if (upserted.contains(id)) {
                throw new IllegalArgumentException(
                        "resource id both upserted and invalidated in the same delta: " + id);
            }
        }
    }

    public static final CustomPacketPayload.Type<ResourceDeltaSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "resource_sync_delta"));

    public static final StreamCodec<FriendlyByteBuf, ResourceDeltaSyncPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.schemaVersion());
                buf.writeLong(payload.baseRevision());
                buf.writeLong(payload.revision());
                buf.writeVarInt(payload.upsertScalars().size());
                for (ResourceScalarWireSnapshot s : payload.upsertScalars()) {
                    ResourceScalarWireSnapshot.write(buf, s);
                }
                buf.writeVarInt(payload.upsertPartitioned().size());
                for (ResourcePartitionedWireSnapshot p : payload.upsertPartitioned()) {
                    ResourcePartitionedWireSnapshot.write(buf, p);
                }
                buf.writeVarInt(payload.invalidated().size());
                for (Identifier id : payload.invalidated()) {
                    buf.writeIdentifier(id);
                }
            },
            buf -> {
                int schemaVersion = buf.readVarInt();
                long baseRevision = buf.readLong();
                long revision = buf.readLong();
                int scalarCount = buf.readVarInt();
                if (scalarCount < 0 || scalarCount > ResourceSyncProtocol.MAX_RESOURCE_ENTRIES) {
                    throw new IllegalArgumentException(
                            "scalar count out of bounds: " + scalarCount + " (max " + ResourceSyncProtocol.MAX_RESOURCE_ENTRIES + ")");
                }
                List<ResourceScalarWireSnapshot> upsertScalars = new ArrayList<>(scalarCount);
                for (int i = 0; i < scalarCount; i++) {
                    upsertScalars.add(ResourceScalarWireSnapshot.read(buf));
                }
                int partitionedCount = buf.readVarInt();
                if (partitionedCount < 0 || partitionedCount > ResourceSyncProtocol.MAX_RESOURCE_ENTRIES) {
                    throw new IllegalArgumentException(
                            "partitioned count out of bounds: " + partitionedCount + " (max " + ResourceSyncProtocol.MAX_RESOURCE_ENTRIES + ")");
                }
                List<ResourcePartitionedWireSnapshot> upsertPartitioned = new ArrayList<>(partitionedCount);
                for (int i = 0; i < partitionedCount; i++) {
                    upsertPartitioned.add(ResourcePartitionedWireSnapshot.read(buf));
                }
                int invalidatedCount = buf.readVarInt();
                if (invalidatedCount < 0 || invalidatedCount > ResourceSyncProtocol.MAX_RESOURCE_ENTRIES) {
                    throw new IllegalArgumentException(
                            "invalidated count out of bounds: " + invalidatedCount + " (max " + ResourceSyncProtocol.MAX_RESOURCE_ENTRIES + ")");
                }
                List<Identifier> invalidated = new ArrayList<>(invalidatedCount);
                for (int i = 0; i < invalidatedCount; i++) {
                    invalidated.add(buf.readIdentifier());
                }
                return new ResourceDeltaSyncPayload(
                        schemaVersion, baseRevision, revision, upsertScalars, upsertPartitioned, invalidated);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

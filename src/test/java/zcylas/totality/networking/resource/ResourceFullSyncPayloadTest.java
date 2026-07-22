package zcylas.totality.networking.resource;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.sync.ResourcePartitionWireEntry;
import zcylas.totality.api.rpg.resources.sync.ResourcePartitionedWireSnapshot;
import zcylas.totality.api.rpg.resources.sync.ResourceScalarWireSnapshot;
import zcylas.totality.api.rpg.resources.sync.ResourceSyncProtocol;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourceFullSyncPayloadTest {

    private static final Identifier SCALAR_ID = Identifier.fromNamespaceAndPath("totality", "fake_scalar");
    private static final Identifier PARTITIONED_ID = Identifier.fromNamespaceAndPath("totality", "fake_partitioned");

    @Test
    void codecRoundTripsExactly() {
        ResourceScalarWireSnapshot scalar = new ResourceScalarWireSnapshot(SCALAR_ID, 1L, 10L, 100L, 0L);
        ResourcePartitionedWireSnapshot partitioned = new ResourcePartitionedWireSnapshot(
                PARTITIONED_ID, 1L, List.of(new ResourcePartitionWireEntry(1, 0, 1, 0)));
        ResourceFullSyncPayload original = new ResourceFullSyncPayload(1, 7L, List.of(scalar), List.of(partitioned));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ResourceFullSyncPayload.CODEC.encode(buf, original);
        ResourceFullSyncPayload decoded = ResourceFullSyncPayload.CODEC.decode(buf);

        assertEquals(original, decoded);
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void emptyPayloadRoundTrips() {
        ResourceFullSyncPayload original = new ResourceFullSyncPayload(1, 0L, List.of(), List.of());

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ResourceFullSyncPayload.CODEC.encode(buf, original);
        ResourceFullSyncPayload decoded = ResourceFullSyncPayload.CODEC.decode(buf);

        assertEquals(original, decoded);
    }

    @Test
    void duplicateResourceIdWithinScalarsIsRejected() {
        ResourceScalarWireSnapshot a = new ResourceScalarWireSnapshot(SCALAR_ID, 1L, 1L, 10L, 0L);
        ResourceScalarWireSnapshot b = new ResourceScalarWireSnapshot(SCALAR_ID, 1L, 2L, 10L, 0L);

        assertThrows(IllegalArgumentException.class,
                () -> new ResourceFullSyncPayload(1, 0L, List.of(a, b), List.of()));
    }

    @Test
    void duplicateResourceIdAcrossScalarAndPartitionedIsRejected() {
        // Same literal id used as both a scalar and a partitioned entry — a shape mismatch that
        // must fail exactly like an in-list duplicate, not be silently accepted as "different lists".
        ResourceScalarWireSnapshot scalar = new ResourceScalarWireSnapshot(SCALAR_ID, 1L, 1L, 10L, 0L);
        ResourcePartitionedWireSnapshot partitioned = new ResourcePartitionedWireSnapshot(
                SCALAR_ID, 1L, List.of(new ResourcePartitionWireEntry(1, 0, 1, 0)));

        assertThrows(IllegalArgumentException.class,
                () -> new ResourceFullSyncPayload(1, 0L, List.of(scalar), List.of(partitioned)));
    }

    @Test
    void oversizedScalarCountIsRejectedBeforeAllocating() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(1);
        buf.writeLong(0L);
        buf.writeVarInt(ResourceSyncProtocol.MAX_RESOURCE_ENTRIES + 1);

        assertThrows(IllegalArgumentException.class, () -> ResourceFullSyncPayload.CODEC.decode(buf));
    }

    @Test
    void oversizedPartitionedCountIsRejectedBeforeAllocating() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(1);
        buf.writeLong(0L);
        buf.writeVarInt(0); // scalar count — none
        buf.writeVarInt(ResourceSyncProtocol.MAX_RESOURCE_ENTRIES + 1);

        assertThrows(IllegalArgumentException.class, () -> ResourceFullSyncPayload.CODEC.decode(buf));
    }

}

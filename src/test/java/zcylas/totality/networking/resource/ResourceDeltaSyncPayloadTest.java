package zcylas.totality.networking.resource;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.sync.ResourceScalarWireSnapshot;
import zcylas.totality.api.rpg.resources.sync.ResourceSyncProtocol;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ResourceDeltaSyncPayloadTest {

    private static final Identifier A = Identifier.fromNamespaceAndPath("totality", "fake_a");
    private static final Identifier B = Identifier.fromNamespaceAndPath("totality", "fake_b");

    @Test
    void codecRoundTripsExactly() {
        ResourceScalarWireSnapshot scalar = new ResourceScalarWireSnapshot(A, 1L, 5L, 10L, 0L);
        ResourceDeltaSyncPayload original = new ResourceDeltaSyncPayload(
                1, 3L, 4L, List.of(scalar), List.of(), List.of(B));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ResourceDeltaSyncPayload.CODEC.encode(buf, original);
        ResourceDeltaSyncPayload decoded = ResourceDeltaSyncPayload.CODEC.decode(buf);

        assertEquals(original, decoded);
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void revisionMustExceedBaseRevision() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceDeltaSyncPayload(1, 5L, 5L, List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceDeltaSyncPayload(1, 5L, 4L, List.of(), List.of(), List.of()));
    }

    @Test
    void duplicateResourceIdInUpsertScalarsIsRejected() {
        ResourceScalarWireSnapshot a1 = new ResourceScalarWireSnapshot(A, 1L, 1L, 10L, 0L);
        ResourceScalarWireSnapshot a2 = new ResourceScalarWireSnapshot(A, 1L, 2L, 10L, 0L);

        assertThrows(IllegalArgumentException.class,
                () -> new ResourceDeltaSyncPayload(1, 0L, 1L, List.of(a1, a2), List.of(), List.of()));
    }

    @Test
    void duplicateResourceIdInInvalidatedIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceDeltaSyncPayload(1, 0L, 1L, List.of(), List.of(), List.of(A, A)));
    }

    @Test
    void sameResourceIdCannotBeBothUpsertedAndInvalidated() {
        ResourceScalarWireSnapshot scalar = new ResourceScalarWireSnapshot(A, 1L, 1L, 10L, 0L);

        assertThrows(IllegalArgumentException.class,
                () -> new ResourceDeltaSyncPayload(1, 0L, 1L, List.of(scalar), List.of(), List.of(A)));
    }

    @Test
    void emptyBatchIsStructurallyAllowedByThisType() {
        // ResourceSyncManager itself never constructs/sends an empty batch (see
        // PlayerResourceSyncStateTest's "unchanged snapshots produce no delta" coverage) — this
        // type only guarantees its own field invariants, not that empty batches are never built.
        ResourceDeltaSyncPayload payload = new ResourceDeltaSyncPayload(1, 0L, 1L, List.of(), List.of(), List.of());
        assertTrue(payload.upsertScalars().isEmpty());
        assertTrue(payload.invalidated().isEmpty());
    }

    @Test
    void revisionMustBeExactlyBaseRevisionPlusOne() {
        // A jump of more than one — even though it is technically "greater than baseRevision" — is
        // rejected: one ordinary delta batch may only ever advance the unified view by one.
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceDeltaSyncPayload(1, 5L, 8L, List.of(), List.of(), List.of()));
    }

    @Test
    void baseRevisionAtLongMaxValueIsRejectedRatherThanOverflowing() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceDeltaSyncPayload(1, Long.MAX_VALUE, Long.MIN_VALUE, List.of(), List.of(), List.of()));
    }

    @Test
    void oversizedScalarCountIsRejectedBeforeAllocating() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(1);
        buf.writeLong(0L);
        buf.writeLong(1L);
        buf.writeVarInt(ResourceSyncProtocol.MAX_RESOURCE_ENTRIES + 1);

        assertThrows(IllegalArgumentException.class, () -> ResourceDeltaSyncPayload.CODEC.decode(buf));
    }

    @Test
    void oversizedInvalidatedCountIsRejectedBeforeAllocating() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(1);
        buf.writeLong(0L);
        buf.writeLong(1L);
        buf.writeVarInt(0); // scalar count
        buf.writeVarInt(0); // partitioned count
        buf.writeVarInt(ResourceSyncProtocol.MAX_RESOURCE_ENTRIES + 1);

        assertThrows(IllegalArgumentException.class, () -> ResourceDeltaSyncPayload.CODEC.decode(buf));
    }
}

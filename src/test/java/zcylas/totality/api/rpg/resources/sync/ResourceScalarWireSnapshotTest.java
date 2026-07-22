package zcylas.totality.api.rpg.resources.sync;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.ResourceSnapshot;

import static org.junit.jupiter.api.Assertions.*;

class ResourceScalarWireSnapshotTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "fake_scalar");

    @Test
    void codecRoundTripsExactly() {
        ResourceScalarWireSnapshot original = new ResourceScalarWireSnapshot(ID, 5L, 30L, 100L, 0L);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ResourceScalarWireSnapshot.write(buf, original);
        ResourceScalarWireSnapshot decoded = ResourceScalarWireSnapshot.read(buf);

        assertEquals(original, decoded);
        assertEquals(0, buf.readableBytes(), "the entire payload must be consumed");
    }

    @Test
    void fromConvertsAResourceSnapshotWithZeroOverflow() {
        ResourceSnapshot snapshot = new ResourceSnapshot(ID, 30L, 100L, 5L);
        ResourceScalarWireSnapshot wire = ResourceScalarWireSnapshot.from(snapshot);

        assertEquals(ID, wire.resourceId());
        assertEquals(5L, wire.unitScale());
        assertEquals(30L, wire.currentUnits());
        assertEquals(100L, wire.maximumUnits());
        assertEquals(0L, wire.overflowUnits());
    }

    @Test
    void negativeCurrentIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceScalarWireSnapshot(ID, 1L, -1L, 100L, 0L));
    }

    @Test
    void negativeMaximumIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceScalarWireSnapshot(ID, 1L, 0L, -1L, 0L));
    }

    @Test
    void currentAboveMaximumPlusOverflowIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceScalarWireSnapshot(ID, 1L, 11L, 10L, 0L));
    }

    @Test
    void currentExactlyAtMaximumPlusOverflowIsAllowed() {
        ResourceScalarWireSnapshot snapshot = new ResourceScalarWireSnapshot(ID, 1L, 15L, 10L, 5L);
        assertEquals(15L, snapshot.currentUnits());
    }

    @Test
    void unitScaleBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceScalarWireSnapshot(ID, 0L, 0L, 100L, 0L));
    }

    @Test
    void nullResourceIdIsRejected() {
        assertThrows(NullPointerException.class, () -> new ResourceScalarWireSnapshot(null, 1L, 0L, 100L, 0L));
    }
}

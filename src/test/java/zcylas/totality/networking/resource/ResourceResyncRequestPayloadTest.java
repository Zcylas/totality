package zcylas.totality.networking.resource;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceResyncRequestPayloadTest {

    @Test
    void codecRoundTrips() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ResourceResyncRequestPayload.CODEC.encode(buf, new ResourceResyncRequestPayload());
        ResourceResyncRequestPayload decoded = ResourceResyncRequestPayload.CODEC.decode(buf);

        assertNotNull(decoded);
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void payloadCarriesNoFields() {
        // Structural proof that this payload cannot name a target player: a record with zero
        // components has nothing a malicious client could set to request another player's view.
        assertEquals(0, ResourceResyncRequestPayload.class.getRecordComponents().length);
    }
}

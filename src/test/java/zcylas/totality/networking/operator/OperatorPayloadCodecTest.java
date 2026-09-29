package zcylas.totality.networking.operator;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.operator.OperatorAction;
import zcylas.totality.api.operator.OperatorResult;

import static org.junit.jupiter.api.Assertions.*;

/** The Operator Mode wire format carries only an id and an ordinal; anything unknown decodes to "refuse". */
class OperatorPayloadCodecTest {

    @Test
    void actionRoundTripsAndUnknownOrdinalsDecodeToNull() {
        for (OperatorAction a : OperatorAction.values()) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            OperatorActionPayload.CODEC.encode(buf, new OperatorActionPayload(42, a));
            assertEquals(new OperatorActionPayload(42, a), OperatorActionPayload.CODEC.decode(buf));
        }
        FriendlyByteBuf forged = new FriendlyByteBuf(Unpooled.buffer());
        forged.writeVarInt(7);
        forged.writeVarInt(12345);
        assertNull(OperatorActionPayload.CODEC.decode(forged).action(), "a forged ordinal names no action");
    }

    @Test
    void resultRoundTripsAndUnknownOrdinalsAreRejected() {
        for (OperatorResult r : OperatorResult.values()) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            OperatorActionResultPayload.CODEC.encode(buf, new OperatorActionResultPayload(3, r));
            assertEquals(r, OperatorActionResultPayload.CODEC.decode(buf).result());
        }
        FriendlyByteBuf forged = new FriendlyByteBuf(Unpooled.buffer());
        forged.writeVarInt(3);
        forged.writeVarInt(99);
        assertEquals(OperatorResult.REJECTED, OperatorActionResultPayload.CODEC.decode(forged).result());
    }
}

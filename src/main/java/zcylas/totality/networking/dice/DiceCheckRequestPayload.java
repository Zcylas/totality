// networking/dice/DiceCheckRequestPayload.java
package zcylas.totality.networking.dice;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.dice.DiceRollContext;

import java.util.UUID;

/** S2C — server opens the dice roll screen on the client. Contains context but NO result. */
public record DiceCheckRequestPayload(UUID sessionId, DiceRollContext context)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DiceCheckRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "dice_check_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiceCheckRequestPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeLong(p.sessionId().getMostSignificantBits());
                        buf.writeLong(p.sessionId().getLeastSignificantBits());
                        DiceRollContext.STREAM_CODEC.encode(buf, p.context());
                    },
                    buf -> {
                        UUID uuid    = new UUID(buf.readLong(), buf.readLong());
                        DiceRollContext ctx = DiceRollContext.STREAM_CODEC.decode(buf);
                        return new DiceCheckRequestPayload(uuid, ctx);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
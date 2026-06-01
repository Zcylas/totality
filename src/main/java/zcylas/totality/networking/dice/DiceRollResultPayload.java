// networking/dice/DiceRollResultPayload.java
package zcylas.totality.networking.dice;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.dice.DiceRollResult;

import java.util.UUID;

/** S2C — the server sends the actual roll result back after the player clicked. */
public record DiceRollResultPayload(UUID sessionId, DiceRollResult result)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DiceRollResultPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "dice_roll_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiceRollResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeLong(p.sessionId().getMostSignificantBits());
                        buf.writeLong(p.sessionId().getLeastSignificantBits());
                        DiceRollResult.STREAM_CODEC.encode(buf, p.result());
                    },
                    buf -> {
                        UUID uuid = new UUID(buf.readLong(), buf.readLong());
                        DiceRollResult result = DiceRollResult.STREAM_CODEC.decode(buf);
                        return new DiceRollResultPayload(uuid, result);
                    }
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
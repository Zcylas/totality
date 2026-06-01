// networking/dice/DiceRollClickPayload.java
package zcylas.totality.networking.dice;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

import java.util.UUID;

/** C2S — player clicked "Roll Dice". Server receives this and performs the actual roll. */
public record DiceRollClickPayload(UUID sessionId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DiceRollClickPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "dice_roll_click"));

    public static final StreamCodec<FriendlyByteBuf, DiceRollClickPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeLong(p.sessionId().getMostSignificantBits());
                        buf.writeLong(p.sessionId().getLeastSignificantBits());
                    },
                    buf -> new DiceRollClickPayload(new UUID(buf.readLong(), buf.readLong()))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
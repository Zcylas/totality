package zcylas.totality.networking.operator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.operator.OperatorResult;

/** Server -> client: what the server did with {@link OperatorActionPayload} {@code requestId}. */
public record OperatorActionResultPayload(int requestId, OperatorResult result) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OperatorActionResultPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "operator_action_result"));

    public static final StreamCodec<FriendlyByteBuf, OperatorActionResultPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.requestId());
                buf.writeVarInt(p.result().ordinal());
            },
            buf -> new OperatorActionResultPayload(buf.readVarInt(), OperatorResult.byOrdinal(buf.readVarInt())));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package zcylas.totality.networking.operator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** Server -> client: the answer to {@link OperatorAuthorizationQueryPayload} (presentation only). */
public record OperatorAuthorizationPayload(int requestId, boolean authorized) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OperatorAuthorizationPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "operator_authorization"));

    public static final StreamCodec<FriendlyByteBuf, OperatorAuthorizationPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.requestId());
                buf.writeBoolean(p.authorized());
            },
            buf -> new OperatorAuthorizationPayload(buf.readVarInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package zcylas.totality.networking.operator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * Client -> server: the Operator Mode activation phrase was heard mid-hold; may this player use it?
 * Only a presentation question (the HUD's "requesting authorization"); it changes nothing and grants
 * nothing — every action is re-checked on its own.
 */
public record OperatorAuthorizationQueryPayload(int requestId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OperatorAuthorizationQueryPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "operator_authorization_query"));

    public static final StreamCodec<FriendlyByteBuf, OperatorAuthorizationQueryPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.requestId()),
            buf -> new OperatorAuthorizationQueryPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

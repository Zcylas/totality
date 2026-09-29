package zcylas.totality.networking.operator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.operator.OperatorAction;

/**
 * Client -> server: one completed Operator Mode utterance asks for ONE whitelisted action. Typed: an
 * action ordinal, never text, target or flag — the server decides who, whether and what happened.
 *
 * @param requestId strictly increasing per connection; a repeated or older id is refused
 * @param action    null when the ordinal is unknown (refused by the server)
 */
public record OperatorActionPayload(int requestId, @Nullable OperatorAction action) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OperatorActionPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "operator_action"));

    public static final StreamCodec<FriendlyByteBuf, OperatorActionPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.requestId());
                buf.writeVarInt(p.action() == null ? -1 : p.action().ordinal());
            },
            buf -> new OperatorActionPayload(buf.readVarInt(), OperatorAction.byOrdinal(buf.readVarInt())));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

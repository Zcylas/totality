package zcylas.totality.networking.shop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** C2S — player closed the Trading screen (Cancel button or Esc) while it was still open. */
public record CloseTradePayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CloseTradePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "close_trade"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloseTradePayload> STREAM_CODEC =
            StreamCodec.unit(new CloseTradePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

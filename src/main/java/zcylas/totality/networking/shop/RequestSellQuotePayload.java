package zcylas.totality.networking.shop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/** C2S (Phase 4) — the player selected inventory slot {@code slotIndex} in SELL mode and wants a
 *  live, server-authoritative preview (unit payout, stack count, max sellable quantity) before
 *  committing. Never mutates anything — {@link zcylas.totality.api.shop.TradeSessionManager
 *  #requestSellQuote} only computes and responds with {@link SellQuoteResultPayload}. */
public record RequestSellQuotePayload(int slotIndex) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestSellQuotePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "request_sell_quote"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestSellQuotePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeInt(p.slotIndex()),
                    buf -> new RequestSellQuotePayload(buf.readInt())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

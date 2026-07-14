package zcylas.totality.networking.shop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * S2C (Phase 4, Part D) — a BUY or SELL attempt was rejected; carries an already-resolved
 * localization key (never raw, unlocalized English) so the Trading screen can show a concrete,
 * translated reason instead of failing silently. {@code reasonKey} is chosen SERVER-side from the
 * existing structured {@link zcylas.totality.api.shop.BuyResult.Reason}/
 * {@link zcylas.totality.api.shop.SellResult.Reason} enums (see {@code TradeSessionManager}'s
 * networking handlers) — this reuses those reasons rather than inventing a parallel system, and
 * the client never receives or trusts free-form server text for this message. Not sent on
 * success — the existing {@link ShowShopStatePayload} refresh already communicates that.
 */
public record TradeRejectionPayload(boolean buy, String reasonKey) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TradeRejectionPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "trade_rejection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TradeRejectionPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeBoolean(p.buy()); buf.writeUtf(p.reasonKey()); },
                    buf -> new TradeRejectionPayload(buf.readBoolean(), buf.readUtf())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

package zcylas.totality.networking.shop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

/**
 * S2C (Phase 4) — server-computed answer to {@link RequestSellQuotePayload}, mirroring {@link
 * zcylas.totality.api.shop.MerchantSellQuoteView}'s own fields directly (one source of truth for
 * "is this sellable and for how much" — see that class). The client derives its OWN user-facing
 * rejection message (item not accepted / no known value / merchant can't afford it) from the
 * {@code accepted}/{@code hasValue}/{@code effectiveMaxQuantity} booleans and numbers here, via
 * static localization keys — it never receives or trusts a raw server-authored reason string,
 * and it never computes {@code unitPayout} itself. {@code slotIndex} echoes the request so a
 * response that arrives after the player has already re-selected a different slot can be
 * discarded instead of misapplied.
 */
public record SellQuoteResultPayload(
        int slotIndex,
        boolean accepted,
        boolean hasValue,
        long unitPayout,
        int stackCount,
        int maxQuantityByStack,
        int maxQuantityByMerchant,
        int effectiveMaxQuantity
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SellQuoteResultPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "sell_quote_result"));

    /** An empty/invalid slot (out of range or no stack) — not accepted, no value, nothing sellable. */
    public static SellQuoteResultPayload empty(int slotIndex) {
        return new SellQuoteResultPayload(slotIndex, false, false, 0L, 0, 0, 0, 0);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, SellQuoteResultPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeInt(p.slotIndex());
                        buf.writeBoolean(p.accepted());
                        buf.writeBoolean(p.hasValue());
                        buf.writeVarLong(p.unitPayout());
                        buf.writeVarInt(p.stackCount());
                        buf.writeVarInt(p.maxQuantityByStack());
                        buf.writeVarInt(p.maxQuantityByMerchant());
                        buf.writeVarInt(p.effectiveMaxQuantity());
                    },
                    buf -> new SellQuoteResultPayload(
                            buf.readInt(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readVarLong(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

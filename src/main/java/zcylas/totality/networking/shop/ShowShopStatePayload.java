package zcylas.totality.networking.shop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

import java.util.List;

/** S2C — opens or updates the Trading screen. Also used to close it (ended = true).
 *  {@code merchantCredits} (design document Phase 3 Part J) is the merchant's live business
 *  Credits balance — always available via {@code MerchantRuntime.currentCredits()}, 0 for a
 *  non-entity-backed session with no real merchant balance to show. */
public record ShowShopStatePayload(
        int npcEntityId,
        Component shopName,
        List<ShopEntryDisplayData> sells,
        long walletBalance,
        long physicalCredits,
        long merchantCredits,
        boolean ended
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShowShopStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "show_shop_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowShopStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeInt(p.npcEntityId());
                        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, p.shopName());
                        ShopEntryDisplayData.LIST_STREAM_CODEC.encode(buf, p.sells());
                        buf.writeVarLong(p.walletBalance());
                        buf.writeVarLong(p.physicalCredits());
                        buf.writeVarLong(p.merchantCredits());
                        buf.writeBoolean(p.ended());
                    },
                    buf -> new ShowShopStatePayload(
                            buf.readInt(),
                            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf),
                            ShopEntryDisplayData.LIST_STREAM_CODEC.decode(buf),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            buf.readBoolean()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

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
 *  non-entity-backed session with no real merchant balance to show.
 *
 *  <p>Phase 4 additions: {@code merchantArchetype} is a translatable label ({@code
 *  MerchantRuntime.archetypeTranslationKey()}, e.g. "Provisioner") resolved client-side, never a
 *  hardcoded server string (Part H). {@code valuedInventorySlots} is the set of the PLAYER's own
 *  inventory slot indices whose current stack has a resolvable central {@code ItemValueRegistry}
 *  value as of this snapshot — {@code ItemValueRegistry} is server-only data with no client-side
 *  equivalent, so the SELL inventory grid's "no known value" state cannot be determined any other
 *  way; the "accepted category" state, by contrast, is determined client-side directly via the
 *  synced {@code ModTags.PROVISIONER_BUYS} item tag, which needs no new networking at all. */
public record ShowShopStatePayload(
        int npcEntityId,
        Component shopName,
        Component merchantArchetype,
        List<ShopEntryDisplayData> sells,
        long walletBalance,
        long physicalCredits,
        long merchantCredits,
        List<Integer> valuedInventorySlots,
        boolean ended
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShowShopStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "show_shop_state"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Integer>> INT_LIST_STREAM_CODEC =
            StreamCodec.of(
                    (buf, list) -> {
                        buf.writeVarInt(list.size());
                        for (int value : list) buf.writeVarInt(value);
                    },
                    buf -> {
                        int size = buf.readVarInt();
                        List<Integer> list = new java.util.ArrayList<>(size);
                        for (int i = 0; i < size; i++) list.add(buf.readVarInt());
                        return list;
                    }
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, ShowShopStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeInt(p.npcEntityId());
                        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, p.shopName());
                        ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.encode(buf, p.merchantArchetype());
                        ShopEntryDisplayData.LIST_STREAM_CODEC.encode(buf, p.sells());
                        buf.writeVarLong(p.walletBalance());
                        buf.writeVarLong(p.physicalCredits());
                        buf.writeVarLong(p.merchantCredits());
                        INT_LIST_STREAM_CODEC.encode(buf, p.valuedInventorySlots());
                        buf.writeBoolean(p.ended());
                    },
                    buf -> new ShowShopStatePayload(
                            buf.readInt(),
                            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf),
                            ComponentSerialization.TRUSTED_CONTEXT_FREE_STREAM_CODEC.decode(buf),
                            ShopEntryDisplayData.LIST_STREAM_CODEC.decode(buf),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            buf.readVarLong(),
                            INT_LIST_STREAM_CODEC.decode(buf),
                            buf.readBoolean()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

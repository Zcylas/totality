package zcylas.totality.networking.shop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One server-issued BUY entry (design document Phase 3 Part J). {@code limitedStock}/
 * {@code availableStock} are an explicit boolean+count pair rather than an
 * {@code OptionalInt}-style {@code -1 == unlimited} sentinel, so the wire representation and the
 * in-memory shape agree exactly. Existing fixed {@link zcylas.totality.api.shop.ShopTemplate}
 * shops remain unlimited — they always report {@code limitedStock = false}; a stock-backed
 * {@link zcylas.totality.api.shop.MerchantStockProvider} entry reports {@code limitedStock = true}
 * with its live {@code availableStock} (0 meaning sold out — see {@link #soldOut()}).
 */
public record ShopEntryDisplayData(
        ItemStack stack, long price, boolean affordable, boolean limitedStock, int availableStock
) {

    /** Convenience constructor for the unlimited (existing {@code ShopTemplate}) case — keeps
     *  every pre-Phase-3 call site compiling with the same three arguments it already passed. */
    public ShopEntryDisplayData(ItemStack stack, long price, boolean affordable) {
        this(stack, price, affordable, false, 0);
    }

    public boolean soldOut() {
        return limitedStock && availableStock <= 0;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopEntryDisplayData> STREAM_CODEC =
            StreamCodec.of(
                    (buf, d) -> {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, d.stack());
                        buf.writeVarLong(d.price());
                        buf.writeBoolean(d.affordable());
                        buf.writeBoolean(d.limitedStock());
                        buf.writeVarInt(d.availableStock());
                    },
                    buf -> new ShopEntryDisplayData(
                            ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                            buf.readVarLong(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readVarInt()
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, List<ShopEntryDisplayData>> LIST_STREAM_CODEC =
            StreamCodec.of(
                    (buf, list) -> {
                        buf.writeInt(list.size());
                        for (ShopEntryDisplayData d : list) STREAM_CODEC.encode(buf, d);
                    },
                    buf -> {
                        int size = buf.readInt();
                        List<ShopEntryDisplayData> list = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) list.add(STREAM_CODEC.decode(buf));
                        return list;
                    }
            );
}

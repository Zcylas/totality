package zcylas.totality.networking.shop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record ShopEntryDisplayData(ItemStack stack, long price, boolean affordable) {

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopEntryDisplayData> STREAM_CODEC =
            StreamCodec.of(
                    (buf, d) -> {
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, d.stack());
                        buf.writeVarLong(d.price());
                        buf.writeBoolean(d.affordable());
                    },
                    buf -> new ShopEntryDisplayData(
                            ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                            buf.readVarLong(),
                            buf.readBoolean()
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

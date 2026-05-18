package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ItemTypeComponent(ItemType type) {

    public static final Codec<ItemTypeComponent> CODEC =
            ItemType.CODEC.xmap(ItemTypeComponent::new, ItemTypeComponent::type);

    public static final StreamCodec<ByteBuf, ItemTypeComponent> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(
                    s -> new ItemTypeComponent(ItemType.valueOf(s.toUpperCase())),
                    c -> c.type().getSerializedName()
            );
}
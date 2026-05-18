package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record RarityComponent(ItemRarity rarity) {

    public static final Codec<RarityComponent> CODEC =
            ItemRarity.CODEC.xmap(RarityComponent::new, RarityComponent::rarity);

    public static final StreamCodec<ByteBuf, RarityComponent> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(
                    s -> new RarityComponent(ItemRarity.valueOf(s.toUpperCase())),
                    c -> c.rarity().getSerializedName()
            );
}
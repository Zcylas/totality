package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record LoreComponent(String text) {

    public static final Codec<LoreComponent> CODEC =
            Codec.STRING.xmap(LoreComponent::new, LoreComponent::text);

    public static final StreamCodec<ByteBuf, LoreComponent> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(LoreComponent::new, LoreComponent::text);
}
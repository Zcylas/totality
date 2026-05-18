package zcylas.totality.api.core.rpgutils;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record WeightComponent(float weight) {

    public static final Codec<WeightComponent> CODEC =
            Codec.FLOAT.xmap(WeightComponent::new, WeightComponent::weight);

    public static final StreamCodec<ByteBuf, WeightComponent> STREAM_CODEC =
            ByteBufCodecs.FLOAT.map(WeightComponent::new, WeightComponent::weight);
}
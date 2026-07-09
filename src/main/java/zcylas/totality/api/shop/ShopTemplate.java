package zcylas.totality.api.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record ShopTemplate(String name, List<ShopEntry> sells) {
    public static final Codec<ShopTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(ShopTemplate::name),
            ShopEntry.CODEC.listOf().fieldOf("sells").forGetter(ShopTemplate::sells)
    ).apply(i, ShopTemplate::new));
}

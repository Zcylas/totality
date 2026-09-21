package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Ordered, immutable set of classifications for a single item — the replacement for the old
 * singleton {@link ItemTypeComponent}. Order is registration-authored and stable; it drives
 * which badge is drawn first, not which badge "wins." Classification never determines the
 * tooltip's rarity theme or border. Callers should read classifications through
 * {@link ItemComponents#classificationsOf(net.minecraft.world.item.ItemStack)}, which also
 * covers items that still only carry the legacy singleton component.
 */
public record ClassificationsComponent(List<ItemType> ordered) {

    public ClassificationsComponent {
        ordered = List.copyOf(ordered);
    }

    public static ClassificationsComponent of(ItemType... types) {
        return new ClassificationsComponent(List.of(types));
    }

    public static final Codec<ClassificationsComponent> CODEC =
            ItemType.CODEC.listOf().xmap(ClassificationsComponent::new, ClassificationsComponent::ordered);

    public static final StreamCodec<ByteBuf, ClassificationsComponent> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(
                    names -> new ClassificationsComponent(names.stream()
                            .map(s -> ItemType.valueOf(s.toUpperCase()))
                            .toList()),
                    component -> component.ordered().stream()
                            .map(ItemType::getSerializedName)
                            .toList()
            );
}

package zcylas.totality.api.core.rpgutils.rarity;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * Ordered, immutable set of classifications for a single item — the replacement for the old
 * singleton {@link ItemTypeComponent}. Order is registration-authored and stable; it drives
 * which classification is drawn first, not which one "wins." Classification never determines the
 * tooltip's rarity theme or border. Callers should read classifications through
 * {@link ItemComponents#classificationsOf(net.minecraft.world.item.ItemStack)}, which also
 * covers items that still only carry the legacy singleton component.
 *
 * <p>Each entry is a {@link Classification}: a broad {@link ItemType} category, optionally paired with
 * a specific type ({@code TOOL • AXE}). The original flat format — a list of category strings — is
 * still read and written unchanged for entries without a type (see {@link Classification#CODEC}).
 */
public record ClassificationsComponent(List<Classification> entries) {

    public ClassificationsComponent {
        entries = List.copyOf(entries);
    }

    /** Category-only entries, identical in meaning and encoding to the original flat list. */
    public static ClassificationsComponent of(ItemType... categories) {
        return new ClassificationsComponent(java.util.Arrays.stream(categories).map(Classification::of).toList());
    }

    public static ClassificationsComponent of(Classification... entries) {
        return new ClassificationsComponent(List.of(entries));
    }

    /** The broad categories in authored order — the view every category-based check uses. */
    public List<ItemType> ordered() {
        return entries.stream().map(Classification::category).toList();
    }

    public static final Codec<ClassificationsComponent> CODEC =
            Classification.CODEC.listOf().xmap(ClassificationsComponent::new, ClassificationsComponent::entries);

    public static final StreamCodec<ByteBuf, ClassificationsComponent> STREAM_CODEC =
            Classification.STREAM_CODEC.apply(ByteBufCodecs.list()).map(
                    ClassificationsComponent::new, ClassificationsComponent::entries);
}

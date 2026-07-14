package zcylas.totality.api.shop.assortment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * An independent roll (design document Section 4) — {@code entry} is added to a rolled
 * assortment with probability {@code chance}, evaluated on its own, unrelated to any
 * {@link AssortmentSelectionGroup}. JSON is flattened (item/stock/chance as siblings), matching
 * {@link WeightedAssortmentEntry}'s convention.
 */
public record ChanceAssortmentEntry(AssortmentItemEntry entry, double chance) {

    public ChanceAssortmentEntry {
        if (entry == null) throw new IllegalArgumentException("entry must not be null");
    }

    public static final Codec<ChanceAssortmentEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("item").forGetter(c -> c.entry().item()),
            Codec.INT.fieldOf("stock").forGetter(c -> c.entry().stock()),
            Codec.DOUBLE.fieldOf("chance").forGetter(ChanceAssortmentEntry::chance)
    ).apply(i, (item, stock, chance) -> new ChanceAssortmentEntry(new AssortmentItemEntry(item, stock), chance)));
}

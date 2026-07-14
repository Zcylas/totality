package zcylas.totality.api.shop.assortment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * One candidate inside an {@link AssortmentSelectionGroup} — an {@link AssortmentItemEntry} plus
 * a relative selection weight. JSON is flattened (item/stock/weight as siblings) rather than
 * nesting {@code entry} as a sub-object, matching the design document's illustrative schema.
 *
 * <p>A single weighted group covers every Phase 3 selection shape: a uniform "choose N distinct"
 * group (all entries share weight 1, e.g. the four-common-materials pick), a uniform single
 * choice (two equally-weighted cooked-food entries), and a non-uniform tiered single choice (the
 * tool slot's 90% Stone / 10% Iron split, expressed as weight 9 per Stone entry and weight 1 per
 * Iron entry — see {@link ProvisionerAssortmentPool} for the full worked math).
 */
public record WeightedAssortmentEntry(AssortmentItemEntry entry, int weight) {

    public static final Codec<WeightedAssortmentEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("item").forGetter(w -> w.entry().item()),
            Codec.INT.fieldOf("stock").forGetter(w -> w.entry().stock()),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(WeightedAssortmentEntry::weight)
    ).apply(i, (item, stock, weight) -> new WeightedAssortmentEntry(new AssortmentItemEntry(item, stock), weight)));
}

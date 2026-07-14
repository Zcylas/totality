package zcylas.totality.api.shop.assortment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * One authored candidate item + its initial stock (design document Section 3a, Phase 3 Part A).
 * Reuses {@code ItemStack.CODEC} exactly as {@link zcylas.totality.api.shop.ShopEntry} and
 * {@link zcylas.totality.api.economy.value.ItemValueRule} already do, so component-bearing
 * templates (Water Bottle vs. Potion of Healing — both {@code minecraft:potion}, distinguished
 * only by {@code minecraft:potion_contents}) work correctly.
 *
 * <p>Deliberately carries NO price — Phase 3 resolves BUY price live against
 * {@link zcylas.totality.api.economy.value.ItemValueRegistry}/
 * {@link zcylas.totality.api.economy.value.ItemPricingService} at quote/commit time, so a future
 * price modifier never requires regenerating stock. {@code stock} is this template's INITIAL
 * stock when rolled — validated positive by {@link ProvisionerAssortmentPool#validate()}, not by
 * this record itself (an authoring-time rule, distinct from
 * {@link zcylas.totality.api.shop.MerchantStockEntry}'s runtime "zero is a legitimate sold-out
 * state" rule).
 */
public record AssortmentItemEntry(ItemStack item, int stock) {

    public static final Codec<AssortmentItemEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("item").forGetter(AssortmentItemEntry::item),
            Codec.INT.fieldOf("stock").forGetter(AssortmentItemEntry::stock)
    ).apply(i, AssortmentItemEntry::new));
}

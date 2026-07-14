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

    /** Immutable and defensive (Phase 3 hardening pass, Section 5): stores its OWN copy of
     *  {@code item}, never the caller's reference, so mutating the original after construction
     *  (or a pool sharing one {@code ItemStack} instance across several entries) can never affect
     *  this entry or any future roll built from it. Deliberately does NOT normalize the authored
     *  count here — {@link ProvisionerAssortmentPool#validate()} needs to see the AUTHORED count
     *  as-is to reject a malformed entry (count != 1) as an authoring error, rather than having it
     *  silently coerced to 1 before validation ever sees the mistake. */
    public AssortmentItemEntry {
        if (item == null) throw new IllegalArgumentException("item must not be null");
        item = item.copy();
    }

    /** Defensive copy on every read (Phase 3 hardening pass, Section 5) — no caller can mutate
     *  this entry's internal item template through the returned stack. */
    @Override
    public ItemStack item() {
        return item.copy();
    }

    public static final Codec<AssortmentItemEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("item").forGetter(AssortmentItemEntry::item),
            Codec.INT.fieldOf("stock").forGetter(AssortmentItemEntry::stock)
    ).apply(i, AssortmentItemEntry::new));
}

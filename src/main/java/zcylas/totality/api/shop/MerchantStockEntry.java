package zcylas.totality.api.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * One line of a merchant's per-entity, roll-once BUY stock (design document Section 3a) — the
 * runtime counterpart to {@link zcylas.totality.api.shop.assortment.AssortmentItemEntry}'s
 * authoring-time template. Deliberately does NOT carry a price: price always resolves live
 * against {@link zcylas.totality.api.economy.value.ItemValueRegistry}/
 * {@link zcylas.totality.api.economy.value.ItemPricingService} at quote/commit time, so a future
 * price modifier never requires rerolling or rewriting stock.
 *
 * <p>Immutable and defensive: the canonical constructor stores its OWN copy of {@code item}
 * (never the caller's reference) and normalizes its count to 1 so the item template can never be
 * confused with business stock count — {@code currentStock} is the sole source of truth for "how
 * many," exactly as the design document's "ItemStack count is not ambiguously used as business
 * stock" requirement demands. The {@link #item()} accessor is overridden to return a further
 * defensive copy on every call, so no caller — however it obtained a reference to this record —
 * can mutate the internal template out from under a {@link MerchantStockProvider}. Decrementing
 * stock never mutates in place; {@link #withStock(int)} returns a new instance, so a
 * {@code MerchantStockProvider}'s backing list can safely replace one element by index without
 * ever reordering or removing it (index stability matters — the client identifies BUY entries by
 * index).
 */
public record MerchantStockEntry(ItemStack item, int currentStock) {

    /** {@code currentStock} allows zero (a legitimately sold-out entry) but never negative —
     *  the same "never negative, zero is fine" contract {@link MerchantRuntime#setCurrentCredits}
     *  uses for Credits. Decode-time violations (corrupted NBT) are caught by the {@code CODEC}'s
     *  own range validation below, not by this constructor throwing — a thrown exception mid-decode
     *  would not fail gracefully the way a {@code DataResult} error does. This constructor's own
     *  check remains as a second, defense-in-depth guard against direct programmatic misuse. */
    public MerchantStockEntry {
        if (currentStock < 0) {
            throw new IllegalArgumentException("currentStock must not be negative, was " + currentStock);
        }
        item = item.copy();
        if (item.getCount() != 1) {
            item.setCount(1);
        }
    }

    /** Defensive copy on every read — callers (including {@link MerchantStockProvider#stockEntries()})
     *  can never mutate this entry's internal item template. */
    @Override
    public ItemStack item() {
        return item.copy();
    }

    /** Returns a new entry with the same item template and {@code newStock} in place of this
     *  one's — never mutates {@code this}. */
    public MerchantStockEntry withStock(int newStock) {
        return new MerchantStockEntry(item, newStock);
    }

    public static final Codec<MerchantStockEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("item").forGetter(MerchantStockEntry::item),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("current_stock").forGetter(MerchantStockEntry::currentStock)
    ).apply(i, MerchantStockEntry::new));
}

package zcylas.totality.api.shop.assortment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * "Choose {@code rolls} entries from {@code entries}" (design document Section 3a). {@code
 * distinct} controls whether the same entry can be picked more than once across the group's own
 * {@code rolls} — every Phase 3 group uses {@code distinct = true} (the default), but both modes
 * are supported since the schema exposes the field.
 *
 * <p>One weighted-without-replacement algorithm serves every shape Phase 3 needs: a uniform
 * "choose N distinct" group (all entries weight 1), a uniform single choice ({@code rolls = 1},
 * two equally-weighted entries), and a non-uniform tiered single choice (the tool slot: {@code
 * rolls = 1}, four Stone entries at weight 9 each and four Iron entries at weight 1 each — total
 * weight 40, so any one Stone entry has a 9/40 = 22.5% chance and the four Stone entries together
 * sum to 90%, while any one Iron entry has a 1/40 = 2.5% chance summing to 10% across its four —
 * exactly the required 90/10 tier split with an equal chance within each tier).
 */
public record AssortmentSelectionGroup(int rolls, boolean distinct, List<WeightedAssortmentEntry> entries) {

    public static final Codec<AssortmentSelectionGroup> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("rolls").forGetter(AssortmentSelectionGroup::rolls),
            Codec.BOOL.optionalFieldOf("distinct", true).forGetter(AssortmentSelectionGroup::distinct),
            WeightedAssortmentEntry.CODEC.listOf().fieldOf("entries").forGetter(AssortmentSelectionGroup::entries)
    ).apply(i, AssortmentSelectionGroup::new));

    /** Picks {@code rolls} entries against {@code random}, honoring {@link #distinct()}. Assumes
     *  this group has already passed {@link ProvisionerAssortmentPool#validate()} (positive
     *  rolls, non-empty entries, positive weights, a distinct group never requesting more rolls
     *  than it has entries) — an unvalidated group may simply return fewer than {@code rolls}
     *  picks once its pool is exhausted, rather than throwing. */
    List<WeightedAssortmentEntry> pick(RandomSource random) {
        List<WeightedAssortmentEntry> pool = new ArrayList<>(entries);
        List<WeightedAssortmentEntry> picked = new ArrayList<>(rolls);
        for (int i = 0; i < rolls && !pool.isEmpty(); i++) {
            WeightedAssortmentEntry chosen = pickWeighted(pool, random);
            picked.add(chosen);
            if (distinct) {
                pool.remove(chosen);
            }
        }
        return picked;
    }

    private static WeightedAssortmentEntry pickWeighted(List<WeightedAssortmentEntry> pool, RandomSource random) {
        int totalWeight = 0;
        for (WeightedAssortmentEntry candidate : pool) {
            totalWeight += candidate.weight();
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (WeightedAssortmentEntry candidate : pool) {
            cumulative += candidate.weight();
            if (roll < cumulative) {
                return candidate;
            }
        }
        // Unreachable when every weight is positive (validated) — defensive fallback only.
        return pool.get(pool.size() - 1);
    }
}

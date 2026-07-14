package zcylas.totality.api.shop.assortment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.shop.MerchantStockEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * A static, shared, data-driven definition of what an individual
 * {@link zcylas.totality.entity.npc.ProvisionerNpcEntity} may roll for its own persistent
 * assortment (design document Section 3a, Phase 3 Part A) — the pick-lists and probabilities,
 * NOT any single Provisioner's actual result. Loaded from
 * {@code data/totality/provisioner_assortments/*.json} by {@link ProvisionerAssortmentRegistry},
 * the SAME {@code ServerLifecycleEvents.SERVER_STARTED} + {@code RegistryOps} pattern
 * {@link zcylas.totality.api.shop.ShopRegistry}/
 * {@link zcylas.totality.api.economy.value.ItemValueRegistry} already use, for the identical
 * reason: entries carry component-bearing {@code ItemStack} templates.
 *
 * <p>{@link #validate()} is a PURE function (no logging, no I/O) — exactly like
 * {@link zcylas.totality.api.economy.value.ItemValueRegistry#validate}, it exists so self-tests
 * can feed it synthetic malformed pools and inspect the structured result directly, without
 * producing real {@code ERROR}-level log lines for throwaway test data. Only
 * {@link ProvisionerAssortmentRegistry}'s production datapack-loading path turns a
 * {@link ValidationResult}'s errors into logged errors.
 */
public record ProvisionerAssortmentPool(
        List<AssortmentItemEntry> guaranteed,
        List<AssortmentSelectionGroup> selectionGroups,
        List<ChanceAssortmentEntry> chanceEntries
) {

    public static final Codec<ProvisionerAssortmentPool> CODEC = RecordCodecBuilder.create(i -> i.group(
            AssortmentItemEntry.CODEC.listOf().optionalFieldOf("guaranteed", List.of())
                    .forGetter(ProvisionerAssortmentPool::guaranteed),
            AssortmentSelectionGroup.CODEC.listOf().optionalFieldOf("selection_groups", List.of())
                    .forGetter(ProvisionerAssortmentPool::selectionGroups),
            ChanceAssortmentEntry.CODEC.listOf().optionalFieldOf("chance_entries", List.of())
                    .forGetter(ProvisionerAssortmentPool::chanceEntries)
    ).apply(i, ProvisionerAssortmentPool::new));

    public record ValidationResult(boolean valid, List<String> errors) {}

    /**
     * Rejects malformed pool definitions rather than silently producing strange merchants (Phase
     * 3 Part A). Checks, in order encountered: every item stack is non-empty and authored at
     * count 1 (stock is tracked separately — Part C's "ItemStack count is not ambiguously used as
     * business stock" requirement); every entry's initial stock is positive; every group's roll
     * count is positive and non-empty; a distinct group never requests more rolls than it has
     * entries; every weight is positive and a group's total weight is positive; every chance is
     * finite and within {@code [0, 1]}; and no item template (matched by
     * {@code ItemStack.isSameItemSameComponents}) appears more than once across the ENTIRE pool's
     * authored candidate set (guaranteed ∪ every group's entries ∪ chance entries) — this pool
     * intentionally has no entry-merging rule, so an accidental duplicate anywhere is rejected
     * outright rather than silently doubling that item's effective odds/stock.
     */
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();
        List<ItemStack> allTemplates = new ArrayList<>();

        for (int g = 0; g < guaranteed.size(); g++) {
            AssortmentItemEntry entry = guaranteed.get(g);
            validateEntry(entry, "guaranteed[" + g + "]", errors);
            allTemplates.add(entry.item());
        }

        for (int g = 0; g < selectionGroups.size(); g++) {
            AssortmentSelectionGroup group = selectionGroups.get(g);
            String groupLabel = "selection_groups[" + g + "]";
            if (group.rolls() <= 0) {
                errors.add(groupLabel + ": rolls must be positive, was " + group.rolls());
            }
            if (group.entries().isEmpty()) {
                errors.add(groupLabel + ": must contain at least one entry");
            } else if (group.distinct() && group.rolls() > group.entries().size()) {
                errors.add(groupLabel + ": distinct group requests " + group.rolls()
                        + " rolls but only has " + group.entries().size() + " entries");
            }

            long totalWeight = 0;
            for (int e = 0; e < group.entries().size(); e++) {
                WeightedAssortmentEntry weighted = group.entries().get(e);
                String entryLabel = groupLabel + ".entries[" + e + "]";
                validateEntry(weighted.entry(), entryLabel, errors);
                if (weighted.weight() <= 0) {
                    errors.add(entryLabel + ": weight must be positive, was " + weighted.weight());
                } else {
                    totalWeight += weighted.weight();
                }
                allTemplates.add(weighted.entry().item());
            }
            if (!group.entries().isEmpty() && totalWeight <= 0) {
                errors.add(groupLabel + ": total weight must be positive, was " + totalWeight);
            }
        }

        for (int c = 0; c < chanceEntries.size(); c++) {
            ChanceAssortmentEntry chance = chanceEntries.get(c);
            String label = "chance_entries[" + c + "]";
            validateEntry(chance.entry(), label, errors);
            if (!Double.isFinite(chance.chance()) || chance.chance() < 0.0 || chance.chance() > 1.0) {
                errors.add(label + ": chance must be finite and within [0, 1], was " + chance.chance());
            }
            allTemplates.add(chance.entry().item());
        }

        for (int a = 0; a < allTemplates.size(); a++) {
            for (int b = a + 1; b < allTemplates.size(); b++) {
                if (ItemStack.isSameItemSameComponents(allTemplates.get(a), allTemplates.get(b))) {
                    errors.add("duplicate item template " + allTemplates.get(a)
                            + " appears more than once across the pool (guaranteed/selection_groups/"
                            + "chance_entries) — no merge rule exists, this is rejected as an authoring error");
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    private static void validateEntry(AssortmentItemEntry entry, String label, List<String> errors) {
        if (entry.item().isEmpty()) {
            errors.add(label + ": item stack must not be empty");
        }
        if (entry.item().getCount() != 1) {
            errors.add(label + ": item template stack count must be 1 (stock is tracked separately), was "
                    + entry.item().getCount());
        }
        if (entry.stock() <= 0) {
            errors.add(label + ": stock must be positive, was " + entry.stock());
        }
    }

    /**
     * Rolls this pool exactly once against {@code random}, producing the frozen per-entity stock
     * list a {@link zcylas.totality.entity.npc.ProvisionerNpcEntity} persists (design document
     * Section 3a). Deterministic for a deterministic {@code random} — verification drives this
     * directly with injected {@link RandomSource} instances to reach both weighted tool-tier
     * branches and both chance-entry outcomes without needing a live entity. Result order is
     * guaranteed ∪ (each group's picks, in group-then-pick order) ∪ chance entries (in authored
     * order) — stable and reproducible for a given random sequence, matching the entry-order
     * stability {@link zcylas.totality.api.shop.MerchantStockProvider} requires.
     */
    public List<MerchantStockEntry> roll(RandomSource random) {
        List<MerchantStockEntry> result = new ArrayList<>();
        for (AssortmentItemEntry entry : guaranteed) {
            result.add(new MerchantStockEntry(entry.item(), entry.stock()));
        }
        for (AssortmentSelectionGroup group : selectionGroups) {
            for (WeightedAssortmentEntry picked : group.pick(random)) {
                result.add(new MerchantStockEntry(picked.entry().item(), picked.entry().stock()));
            }
        }
        for (ChanceAssortmentEntry chance : chanceEntries) {
            if (random.nextDouble() < chance.chance()) {
                result.add(new MerchantStockEntry(chance.entry().item(), chance.entry().stock()));
            }
        }
        return result;
    }
}

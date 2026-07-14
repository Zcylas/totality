package zcylas.totality.api.economy.value;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.Totality;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Central item-value definitions, loaded from {@code data/<namespace>/item_values/*.json}
 * (one {@link ItemValueRule} per file, mirroring the one-shop/one-quest-per-file convention
 * already used by {@code ShopRegistry}/{@code QuestRegistry}).
 *
 * <p>Mirrors {@link zcylas.totality.api.shop.ShopRegistry}'s loading pattern exactly, and for
 * the identical reason: {@link ItemValueRule}'s reference stack can carry registry-backed
 * components (e.g. {@code minecraft:potion_contents} resolves a {@code Holder<Potion>}),
 * which needs a real {@code RegistryOps} built from {@code server.registryAccess()} — a plain
 * {@code SimplePreparableReloadListener}'s {@code prepare()} phase only has
 * {@code JsonOps.INSTANCE}, which fails on anything registry-dependent. Deferred to
 * {@code ServerLifecycleEvents.SERVER_STARTED}, same trade-off as {@code ShopRegistry}: no
 * live {@code /reload} support.
 *
 * <p>Zero is an ALLOWED base value (an item can be worth nothing without that implying
 * anything about whether a merchant accepts it — Section 5 of the design document keeps
 * "has a value" and "is accepted" strictly separate). Only NEGATIVE values are rejected.
 *
 * <p>Canonical matching rule (design document Section 1a): a plain item-ID rule is the
 * fallback; component-aware rules outrank it; among matching component-aware rules, the one
 * with more explicitly authored component entries is more specific and wins; rules of
 * different specificity may freely coexist. Only EQUALLY specific rules can conflict — either
 * as exact duplicates, or as an ambiguous tie (equally specific, and not mutually exclusive on
 * any shared component value) — both are rejected at load time, never resolved by load order.
 * Equally specific rules that disagree on a shared component value (Water vs. Healing) are
 * mutually exclusive, not ambiguous, and are both kept.
 *
 * <p>{@link #validate} is a PURE function (no logging, no I/O) — it exists specifically so
 * self-tests can feed it synthetic conflicting data and inspect the structured result directly,
 * without producing real {@code ERROR}-level log lines for throwaway test data. Only
 * {@link #load}, the production datapack path, turns a {@link ValidationResult}'s conflicts
 * into logged errors.
 */
public final class ItemValueRegistry {

    public static final ItemValueRegistry INSTANCE = new ItemValueRegistry();
    private static final String FOLDER = "item_values";

    private Map<Item, List<ItemValueRule>> rulesByItem = new HashMap<>();

    private ItemValueRegistry() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            INSTANCE.load(server);
            ItemValueVerification.runSelfTestIfDev(server);
        });
    }

    /**
     * Resolves the base value for {@code stack} via {@link #pickMostSpecificMatch}. Ambiguous
     * ties are rejected at load time (see {@link #validate}), so resolution here never needs to
     * break a tie itself.
     */
    public Optional<Long> resolveBaseValue(ItemStack stack) {
        List<ItemValueRule> candidates = rulesByItem.get(stack.getItem());
        if (candidates == null) return Optional.empty();
        return pickMostSpecificMatch(candidates, stack).map(ItemValueRule::baseValue);
    }

    /**
     * Among every rule in {@code candidates} that matches {@code stack}, returns the one with
     * the most explicitly authored component entries (a plain item-ID fallback, specificity 0,
     * is used only if no component-aware rule matches). Package-private and pure so it can be
     * exercised directly against a hand-built candidate list, without a full datapack load.
     */
    static Optional<ItemValueRule> pickMostSpecificMatch(List<ItemValueRule> candidates, ItemStack stack) {
        ItemValueRule best = null;
        for (ItemValueRule rule : candidates) {
            if (!rule.matches(stack)) continue;
            if (best == null || rule.specificity() > best.specificity()) {
                best = rule;
            }
        }
        return Optional.ofNullable(best);
    }

    private void load(MinecraftServer server) {
        Map<Identifier, ItemValueRule> parsed = new LinkedHashMap<>();
        ResourceManager manager = server.getResourceManager();
        RegistryOps<com.google.gson.JsonElement> ops =
                RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());

        Map<Identifier, Resource> resources = manager.listResources(FOLDER, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier resourceId = entry.getKey();
            try (InputStream stream = entry.getValue().open();
                 InputStreamReader reader = new InputStreamReader(stream)) {

                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                ItemValueRule.CODEC.parse(ops, json)
                        .resultOrPartial(err -> Totality.LOGGER.error("Failed to parse item value {}: {}", resourceId, err))
                        .ifPresent(rule -> {
                            if (rule.baseValue() < 0) {
                                Totality.LOGGER.error(
                                        "Rejected item value {}: base_value {} is negative — negative values are not allowed.",
                                        resourceId, rule.baseValue());
                                return;
                            }
                            parsed.put(resourceId, rule);
                        });
            } catch (Exception e) {
                Totality.LOGGER.error("Failed to load item value {}", resourceId, e);
            }
        }

        ValidationResult result = validate(parsed);
        this.rulesByItem = result.accepted();

        for (RuleConflict conflict : result.conflicts()) {
            String kind = conflict.kind() == ConflictKind.DUPLICATE
                    ? (conflict.componentAware() ? "an identical component set" : "an identical plain item-ID fallback")
                    : "different, equally-specific component sets that could both match the same stack, with no "
                            + "clear most-specific winner";
            String suffix = conflict.kind() == ConflictKind.DUPLICATE
                    ? "remove one of them."
                    : "author a more specific rule, or remove one, rather than relying on load order.";
            Totality.LOGGER.error("Rejected {} item value rules for {}: {} and {} both declare {} — {}",
                    conflict.kind() == ConflictKind.DUPLICATE ? "duplicate" : "ambiguous",
                    conflict.item(), conflict.ruleA(), conflict.ruleB(), kind, suffix);
        }

        int accepted = rulesByItem.values().stream().mapToInt(List::size).sum();
        int rejected = parsed.size() - accepted;
        Totality.LOGGER.info("Parsed {} item value rules: {} accepted, {} rejected, across {} items",
                parsed.size(), accepted, rejected, rulesByItem.size());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pure validation — no logging, no I/O. See class Javadoc.
    // ─────────────────────────────────────────────────────────────────────

    enum ConflictKind { DUPLICATE, AMBIGUOUS }

    record RuleConflict(Item item, Identifier ruleA, Identifier ruleB, ConflictKind kind, boolean componentAware) {}

    record ValidationResult(Map<Item, List<ItemValueRule>> accepted, List<RuleConflict> conflicts) {}

    /**
     * Groups rules by item and rejects any rule that conflicts with another rule for the same
     * item: an identical reference stack/component patch (a literal duplicate, including two
     * plain fallbacks), or two component-aware rules that are equally specific and could both
     * match the same real stack simultaneously (see {@link ItemValueRuleConflicts}). Conflicting
     * rules are excluded from {@link ValidationResult#accepted()} entirely (rather than picking
     * one by load order); every conflict is also reported in {@link ValidationResult#conflicts()}
     * for the caller to log or inspect, but THIS METHOD NEVER LOGS ANYTHING ITSELF.
     *
     * <p>EVERY pair within an item's group is evaluated, even once one side of the pair is
     * already rejected — a rule already rejected against one conflicting rule can still be the
     * thing that proves a THIRD rule also conflicts (three identical duplicates must all be
     * rejected, not just the first two compared). Skipping already-rejected entries would make
     * the result depend on iteration order, which is exactly what this method must not do.
     *
     * <p>Package-private and pure (no instance state, no I/O, no logging) so it can be
     * exercised directly against a hand-built map without loading an actual datapack or
     * producing real diagnostic log output for synthetic test data.
     */
    static ValidationResult validate(Map<Identifier, ItemValueRule> parsed) {
        Map<Item, List<Map.Entry<Identifier, ItemValueRule>>> byItem = new HashMap<>();
        for (Map.Entry<Identifier, ItemValueRule> entry : parsed.entrySet()) {
            byItem.computeIfAbsent(entry.getValue().referenceStack().getItem(), k -> new ArrayList<>()).add(entry);
        }

        Map<Item, List<ItemValueRule>> accepted = new HashMap<>();
        List<RuleConflict> conflicts = new ArrayList<>();

        for (Map.Entry<Item, List<Map.Entry<Identifier, ItemValueRule>>> group : byItem.entrySet()) {
            List<Map.Entry<Identifier, ItemValueRule>> entries = group.getValue();
            Set<Identifier> rejected = new HashSet<>();

            for (int i = 0; i < entries.size(); i++) {
                for (int j = i + 1; j < entries.size(); j++) {
                    Identifier idA = entries.get(i).getKey();
                    Identifier idB = entries.get(j).getKey();

                    ItemValueRule a = entries.get(i).getValue();
                    ItemValueRule b = entries.get(j).getValue();

                    if (ItemValueRuleConflicts.isDuplicate(a, b)) {
                        conflicts.add(new RuleConflict(group.getKey(), idA, idB, ConflictKind.DUPLICATE, a.isComponentAware()));
                        rejected.add(idA);
                        rejected.add(idB);
                    } else if (ItemValueRuleConflicts.isAmbiguous(a, b)) {
                        conflicts.add(new RuleConflict(group.getKey(), idA, idB, ConflictKind.AMBIGUOUS, true));
                        rejected.add(idA);
                        rejected.add(idB);
                    }
                }
            }

            List<ItemValueRule> kept = new ArrayList<>();
            for (Map.Entry<Identifier, ItemValueRule> entry : entries) {
                if (!rejected.contains(entry.getKey())) kept.add(entry.getValue());
            }
            if (!kept.isEmpty()) accepted.put(group.getKey(), kept);
        }
        return new ValidationResult(accepted, conflicts);
    }
}

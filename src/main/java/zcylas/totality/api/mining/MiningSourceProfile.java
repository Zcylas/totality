package zcylas.totality.api.mining;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Authored Mining Damage / Mining Speed / Mining Tier for a conventional Pickaxe/Axe/Shovel/Hoe mining
 * source — V2's first balance pass (Shovel added in the playtest-correction pass, §5). ONE
 * material table is the single source of truth: it drives {@link #resolve} (gameplay
 * damage/speed/tier lookup) AND {@link #participatingItems} (datagen input for the Impact
 * enchantment's supported-items tag), so a tool can never be Impact-enchantable without also
 * having an authored Mining Damage to add to.
 *
 * <p><b>Participation is EXPLICIT item identity, not a repair-material probe.</b> An earlier draft
 * identified a tool's material by {@code ItemStack.isValidRepairItem} (the same technique {@link
 * MiningTuning#forceTolerance} still uses for Force Tolerance) — but that meant a future/modded
 * Pickaxe merely repairable with, say, a Diamond would silently resolve as the authored Diamond
 * profile even though it was never added to {@link #participatingItems}/the Impact tag, breaking
 * the invariant that authored-profile participation and Impact applicability come from the same
 * source of truth. This pass resolves ONLY the exact vanilla Wood/Stone/Copper/Iron/Diamond/
 * Netherite Pickaxe/Axe/Shovel items below. Force Tolerance is unaffected — it keeps using its own
 * repair-material resolver, which this correction is not about. Future Totality/custom mining
 * sources will register their own explicit entries here when they're built, never inherit one by
 * repair material.
 *
 * <p><b>Mining Tier is MATERIAL capability, not tool-category (Pickaxe-vs-Axe) suitability</b>
 * (Block Breaking V2 review-fix, wrong-tool-Tier pass). {@link MiningTier#ofTool} probes
 * exclusively against Pickaxe-appropriate representative blocks (Stone/Iron Ore/Diamond Ore/
 * Obsidian), so it always returns 0 for an Axe regardless of material — correct for what that
 * function itself measures, but wrong to reuse as "how materially capable is this profiled
 * source", which is what a profiled Axe's Mining Tier needs to mean now that
 * {@link TargetEffectiveness} (not Mining Tier) is responsible for Pickaxe-vs-Axe suitability.
 * Each row's authored {@code tier} is therefore probed once from its own Pickaxe (the existing,
 * already-correct progression) and shared by the Pickaxe, Axe AND Shovel of that material — an
 * Iron Axe or Iron Shovel is exactly as materially capable as an Iron Pickaxe, they just differ in
 * which blocks they're the *preferred* tool for (see {@link TargetEffectiveness}). This pass does
 * not rebalance the tier progression itself, only stops re-deriving it from a Pickaxe-only probe
 * for an Axe/Shovel.
 *
 * <p><b>Block Breaking V2 Pass 2 (conventional tool completion):</b> Gold has its accepted row (Mining Damage 25,
 * Mining Speed 3.0/s, Tier 1 — its Pickaxe probes 1; vanilla item durability 32 is untouched) and every row now
 * carries its Hoe, with exactly the same material Damage/Speed/Tier as that row's Pickaxe/Axe/Shovel. Gold and Hoes
 * therefore run the profiled pipeline (Impact, Efficiency/Haste cadence, target effectiveness, Power zones, wear)
 * instead of the legacy formula, so Gold no longer adds STR to ordinary strikes. Swords, shears and every other
 * TOOL-component item stay specialized, non-profiled sources. Adding a future material (or a future mining
 * source's own table) never requires touching {@link #resolve} or the enchantment/tag wiring — only a new row.
 */
public final class MiningSourceProfile {

    private MiningSourceProfile() {}

    public record Entry(float miningDamage, float miningSpeed, int tier) {}

    private record Material(String displayName, Item pickaxe, Item axe, Item shovel, Item hoe,
                            float miningDamage, float miningSpeed) {}

    /**
     * Ordered Netherite -> Wood, then Gold. Damage/Speed are the authored V2 test balance; each
     * row's {@code tier} is probed from its own Pickaxe via {@link MiningTier#ofTool} — the exact,
     * unchanged, already-authoritative progression — never hand-typed, so it can never silently
     * drift from what a Pickaxe of that material has always resolved to. The probe itself is
     * deliberately NOT run here at table-construction time (see {@link #resolve}) — constructing an
     * {@code ItemStack} this early crashes during datagen bootstrap, before item components are
     * bound; {@link #participatingItems} (datagen's only caller into this class) never needed the
     * Tier value anyway, only the item identities.
     *
     * <p><b>Shovel vertical-slice pass:</b> each material's Shovel shares the exact same
     * Damage/Speed/Tier as its Pickaxe/Axe — same progression, one more tool category. Copper
     * Shovel exists in this Minecraft version ({@code Items.COPPER_SHOVEL}) and is included exactly
     * like the other Copper tools.
     */
    private static final List<Material> MATERIALS = List.of(
            new Material("Netherite", Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE, 100f, 2.5f),
            new Material("Diamond", Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE, 75f, 2.4f),
            new Material("Iron", Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE, 50f, 2.25f),
            new Material("Copper", Items.COPPER_PICKAXE, Items.COPPER_AXE, Items.COPPER_SHOVEL, Items.COPPER_HOE, 40f, 2.1f),
            new Material("Stone", Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_HOE, 35f, 2.0f),
            new Material("Wood", Items.WOODEN_PICKAXE, Items.WOODEN_AXE, Items.WOODEN_SHOVEL, Items.WOODEN_HOE, 20f, 1.5f),
            new Material("Gold", Items.GOLDEN_PICKAXE, Items.GOLDEN_AXE, Items.GOLDEN_SHOVEL, Items.GOLDEN_HOE, 25f, 3.0f)
    );

    /** Exact-item participation map, built once from {@link #MATERIALS} — the single source of truth. */
    private static final Map<Item, Material> BY_ITEM;
    static {
        Map<Item, Material> map = new HashMap<>();
        for (Material m : MATERIALS) {
            map.put(m.pickaxe(), m);
            map.put(m.axe(), m);
            map.put(m.shovel(), m);
            map.put(m.hoe(), m);
        }
        BY_ITEM = Map.copyOf(map);
    }

    /** Authored Mining Damage/Speed/Tier for {@code tool}, or empty when it's not one of the exact
     *  authored items (swords, shears, any modded tool, any non-Pickaxe/Axe/Shovel/Hoe — regardless of repair material).
     *  Tier is probed from the material's own Pickaxe lazily, here, on every real (gameplay/tooltip)
     *  call — never at class-load/datagen time, see {@link #MATERIALS}. */
    public static Optional<Entry> resolve(ItemStack tool) {
        if (tool.isEmpty()) return Optional.empty();
        Material m = BY_ITEM.get(tool.getItem());
        if (m == null) return Optional.empty();
        int tier = MiningTier.ofTool(new ItemStack(m.pickaxe()));
        return Optional.of(new Entry(m.miningDamage(), m.miningSpeed(), tier));
    }

    /** Display name of the authored material driving {@code tool}'s stats (Shift-tooltip provenance), or null. */
    public static String displayName(ItemStack tool) {
        if (tool.isEmpty()) return null;
        Material m = BY_ITEM.get(tool.getItem());
        return m == null ? null : m.displayName();
    }

    /**
     * Every vanilla item that has an authored row — the Impact enchantment's supported-items tag
     * is generated from exactly this list (see {@code ModItemTagProvider}), so tag membership and
     * "has an authored Mining Damage" can never drift apart.
     */
    public static List<Item> participatingItems() {
        List<Item> items = new ArrayList<>();
        for (Material m : MATERIALS) {
            items.add(m.pickaxe());
            items.add(m.axe());
            items.add(m.shovel());
            items.add(m.hoe());
        }
        return items;
    }
}

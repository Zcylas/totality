package zcylas.totality.api.mining;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mining Tier: whether a source is capable of effectively damaging a material at all. Independent
 * of Mining Power (how much) and cadence (how often). 0 = needs nothing, 1 = any proper tool,
 * 2 = stone, 3 = iron, 4 = diamond, 5 = beyond.
 */
public final class MiningTier {

    private MiningTier() {}

    /** Minimum tier needed to damage {@code state}, derived from vanilla properties/tags (fallback). */
    public static int fallbackRequiredTier(BlockState state) {
        if (!state.requiresCorrectToolForDrops()) return 0;
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return 4;
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) return 3;
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) return 2;
        return 1;
    }

    /**
     * Mining Tier of a SPECIALIZED, non-profiled tool source (swords, shears, modded tools; Block Breaking V2 Pass 2):
     * its pickaxe-probe Tier ({@link #ofTool}, 0 for a Sword or Shears), except that on a block the tool's OWN
     * vanilla rules make it the correct tool for drops (Sword or Shears on Cobweb), that block's Required Mining Tier
     * never gates it. Nothing else changes: it gains no conventional Mining Damage, Impact or Power profile, and
     * vanilla harvesting (drops, Silk Touch...) is still decided by vanilla at the terminal break.
     */
    public static int specializedTier(ItemStack tool, BlockState state) {
        int probed = ofTool(tool);
        return tool.isCorrectToolForDrops(state) ? Math.max(probed, BlockProfiles.resolveStatic(state).requiredTier()) : probed;
    }

    /**
     * Swords ({@code #minecraft:swords}) and Shears are specialized sources EXCLUDED from Alt/Power Mining (Block
     * Breaking V2 Pass 3): no Power swing can start or be released with one, so neither the profiled zones nor the
     * legacy Power multiplier / Force Tolerance stress ever apply to them. Their ordinary breaking is unchanged, and
     * bare hands keep Power Mining. Shared by the client (no meter) and the server (authority).
     */
    public static boolean excludedFromPowerMining(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.getItem() instanceof ShearsItem;
    }

    /**
     * Is the held item a mining/tool SOURCE? Decided by the item's real {@code TOOL} data component (its
     * rules), never by an item list and never by {@link #ofTool}: axes, shovels, hoes, shears and modded
     * tools are tool sources even though they have no pickaxe Tier. Empty hands and arbitrary items are not.
     */
    public static boolean isToolSource(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.TOOL);
    }

    /**
     * The held item's OWN mining speed against {@code state}: {@code ItemStack.getDestroySpeed}, i.e. the
     * {@code TOOL} component's rules (verified in 26.2: {@code Item.getDestroySpeed} returns
     * {@code Tool.getMiningSpeed} or 1.0). It excludes Efficiency, Haste, Mining Fatigue, water and airborne
     * modifiers, which live in {@code Player.getDestroySpeed} and feed cadence only.
     */
    public static float intrinsicSpeed(ItemStack stack, BlockState state) {
        return stack.getDestroySpeed(state);
    }

    /**
     * MATERIAL Tier of a held tool (0..4), probed from its own rules against PICKAXE-appropriate
     * representative blocks (Stone/Iron Ore/Diamond Ore/Obsidian). This says how hard a material a
     * Pickaxe-style probe finds the tool able to harvest; it deliberately does NOT decide whether
     * the item is a tool source (see {@link #isToolSource}). Because the probe blocks are
     * Pickaxe-specific, a non-Pickaxe tool (an Axe, Shovel, Hoe, shears, ...) always probes 0 here,
     * regardless of its actual material.
     * <p><b>Do not call this directly for a PROFILED Pickaxe/Axe</b> (see {@link MiningSourceProfile}) —
     * use {@link ResolvedMiningSource#of}{@code (tool, state).tier()} instead, which reads the
     * authored material Tier (identical for a Pickaxe and an Axe of the same material) for a
     * profiled source, and only falls back to this pickaxe-probe-based value for non-profiled/
     * legacy tools. This function's own return value and behavior are unchanged — it is also what
     * {@link MiningSourceProfile} probes once (from each material's Pickaxe) to build that authored
     * Tier table in the first place, and it remains the direct, correct answer for legacy tools.
     * <p>Kept for reference: probed against representative blocks so it follows whatever the tool's
     * data-driven rules say instead of a hard-coded item list. Non-mining items are tier 0.
     */
    public static int ofTool(ItemStack tool) {
        if (tool.isEmpty()) return 0;
        if (!tool.isCorrectToolForDrops(Blocks.STONE.defaultBlockState())) return 0;
        if (!tool.isCorrectToolForDrops(Blocks.IRON_ORE.defaultBlockState())) return 1;
        if (!tool.isCorrectToolForDrops(Blocks.DIAMOND_ORE.defaultBlockState())) return 2;
        if (!tool.isCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState())) return 3;
        return 4;
    }
}

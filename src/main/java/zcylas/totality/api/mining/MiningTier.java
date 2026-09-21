package zcylas.totality.api.mining;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
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
     * MATERIAL Tier of a held tool (0..4), probed from its own rules against representative blocks. This
     * says how hard a material the tool can harvest; it deliberately does NOT decide whether the item is a
     * tool source (see {@link #isToolSource}). A non-pickaxe tool probes 0 and can only work Tier-0 blocks.
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

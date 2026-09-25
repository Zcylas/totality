package zcylas.totality.api.mining;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * How well a profiled conventional tool matches its target block (Block Breaking V2 §2 of the
 * wrong-tool review-fix pass; damage tightened §1 of the playtest-correction pass). Resolved from
 * Minecraft's own semantic mining tags ({@code #minecraft:mineable/pickaxe}, {@code mineable/axe},
 * {@code mineable/shovel}) — never a hand-maintained block list, and never Pickaxe-vs-Stone
 * conditionals baked into the damage/speed calculators.
 *
 * <p>Deliberately independent of Mining Tier: an impact whose Tier is too low is gated to exactly
 * 0 Integrity damage entirely inside {@link BlockBreaking#applyImpact} (unchanged), regardless of
 * what this class returns — target effectiveness only scales an impact that Tier already allowed
 * to happen at all, it can never let a wrong tool bypass Tier.
 *
 * <p>Pickaxe/Axe/Shovel/Hoe are resolved (Hoe via {@code mineable/hoe}, Block Breaking V2 Pass 2). Any other profiled source (a future Totality mining
 * source that defines its own table) defaults to {@link #EFFECTIVE} until it defines its own rule,
 * so this never silently penalizes something this pass wasn't asked to touch.
 *
 * <p><b>Playtest-correction pass, §1:</b> wrong-tool Damage was found too generous at x0.25 — a
 * highly enchanted Pickaxe (e.g. Netherite + Impact V = 125 Mining Damage) could still one/two-hit
 * a 100-Durability Dirt/Grass block meant for a Shovel, making Pickaxes viable Shovel substitutes.
 * Tightened to x0.10 (Speed stays x0.50, unchanged) — still usable in an emergency, clearly
 * inferior to the correct tool. Matching-tool values, the Tier-always-wins rule, and the resolution
 * mechanism (semantic tags, never a conditional) are all unchanged.
 *
 * @param preferred whether the tool is the block's preferred/effective tool — exposed for future
 *                  consumers (e.g. a contextual block-inspection indicator); not consumed by the
 *                  tooltip (§7 of the wrong-tool pass — the tool tooltip stays target-independent).
 */
public record TargetEffectiveness(float damageMultiplier, float speedMultiplier, boolean preferred) {

    public static final TargetEffectiveness EFFECTIVE = new TargetEffectiveness(1.00f, 1.00f, true);
    public static final TargetEffectiveness WRONG_TOOL = new TargetEffectiveness(0.10f, 0.50f, false);

    /**
     * The block's effective tools come from its {@link BlockProfiles Block/Material Profile}; a block with no
     * authored tools resolves through {@link #fallbackTools}, i.e. exactly the vanilla tags used before profiles.
     * An explicitly tool-neutral profile penalises no tool.
     */
    public static TargetEffectiveness resolve(ItemStack tool, BlockState state) {
        BlockProfile.Tool category = category(tool);
        if (category == null) return EFFECTIVE;
        return BlockProfiles.resolveStatic(state).tools().effective(category) ? EFFECTIVE : WRONG_TOOL;
    }

    @Nullable
    private static BlockProfile.Tool category(ItemStack tool) {
        if (tool.is(ItemTags.PICKAXES)) return BlockProfile.Tool.PICKAXE;
        if (tool.is(ItemTags.AXES)) return BlockProfile.Tool.AXE;
        if (tool.is(ItemTags.SHOVELS)) return BlockProfile.Tool.SHOVEL;
        if (tool.is(ItemTags.HOES)) return BlockProfile.Tool.HOE;
        return null;
    }

    /** Compatibility fallback for a block with no authored effective tool: the vanilla {@code mineable/*} tags. */
    public static BlockProfile.ToolAffinity fallbackTools(BlockState state) {
        EnumSet<BlockProfile.Tool> tools = EnumSet.noneOf(BlockProfile.Tool.class);
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) tools.add(BlockProfile.Tool.PICKAXE);
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) tools.add(BlockProfile.Tool.AXE);
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) tools.add(BlockProfile.Tool.SHOVEL);
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) tools.add(BlockProfile.Tool.HOE);
        return new BlockProfile.ToolAffinity(tools, false);
    }
}

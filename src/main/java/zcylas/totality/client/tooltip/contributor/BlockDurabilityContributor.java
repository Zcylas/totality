package zcylas.totality.client.tooltip.contributor;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.api.mining.BlockProfile;
import zcylas.totality.api.mining.BlockProfiles;
import zcylas.totality.client.tooltip.group.TooltipGroup;
import zcylas.totality.client.tooltip.group.TooltipGroups;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Block Durability / Required Mining Tier / Effective Tool — PROPERTIES entries — for any {@link BlockItem} whose placed block is
 * ORDINARY (Block Breaking V2 §12) — surfaces the block's {@link BlockProfiles Block/Material Profile}
 * (authored values, and the hardness-derived compatibility fallback for everything else), the same
 * authority mining resolves through, never a second source of truth. SPECIAL, UNBREAKABLE and
 * not-applicable blocks hold no Block Durability and show none. The same Mining Tier icon as
 * {@link MiningToolContributor} is deliberately reused, so a tool's Mining Tier and a block's
 * Required Mining Tier read as the same stat (§11).
 */
public final class BlockDurabilityContributor implements TooltipContributor {

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        if (!(ctx.stack().getItem() instanceof BlockItem blockItem)) return List.of();
        BlockState state = blockItem.getBlock().defaultBlockState();
        BlockProfile.Resolved resolved = BlockProfiles.resolveStatic(state);
        if (!resolved.ordinary()) return List.of();

        List<TooltipSection> sections = new ArrayList<>();
        sections.add(new TooltipSection.IconStatRow(new TooltipSection.StatIcon.Item(new ItemStack(Items.STONE)),
                "Block Durability", MiningStatFormat.integer(resolved.maxDurability()), 0xFFB0B0B0));
        sections.add(new TooltipSection.IconStatRow(new TooltipSection.StatIcon.Effect(MobEffects.JUMP_BOOST),
                "Required Mining Tier", String.valueOf(resolved.requiredTier()), 0xFF42A5F5));
        String effectiveTool = effectiveToolText(resolved.tools());
        if (effectiveTool != null) {
            List<EffectiveTool> tools = effectiveTools(resolved.tools());
            Item icon = tools.isEmpty() ? Items.IRON_PICKAXE : tools.get(0).icon();   // tool-neutral: the generic tool icon
            sections.add(new TooltipSection.IconStatRow(new TooltipSection.StatIcon.Item(new ItemStack(icon)),
                    "Effective Tool", effectiveTool, 0xFFD0D0D0));
        }
        return sections;
    }

    /** A tool category the block is effectively mined with — the same categories {@code TargetEffectiveness} uses. */
    record EffectiveTool(String label, Item icon) {}

    static final String ANY_TOOL = "Any";

    /**
     * Value of the Effective Tool row: the effective categories ("Pickaxe", "Pickaxe, Axe"...), "Any" for
     * an explicitly tool-neutral profile (every tool is effective; its Required Mining Tier is still its own row), or
     * null (no row) when no conventional tool is preferred. Pure, so it is unit-testable.
     */
    @org.jetbrains.annotations.Nullable
    static String effectiveToolText(BlockProfile.ToolAffinity affinity) {
        if (affinity.neutral()) return ANY_TOOL;
        List<String> labels = new ArrayList<>();
        for (BlockProfile.Tool tool : affinity.tools()) labels.add(label(tool));
        return labels.isEmpty() ? null : String.join(", ", labels);
    }

    private static String label(BlockProfile.Tool tool) {
        return switch (tool) {
            case PICKAXE -> "Pickaxe";
            case AXE -> "Axe";
            case SHOVEL -> "Shovel";
            case HOE -> "Hoe";
        };
    }

    /**
     * The block's effective tool categories, read from the same profile affinity
     * {@link zcylas.totality.api.mining.TargetEffectiveness} decides preferred-tool effectiveness with, so the
     * tooltip cannot disagree with gameplay. Empty when no specific tool is preferred (including tool-neutral).
     */
    static List<EffectiveTool> effectiveTools(BlockProfile.ToolAffinity affinity) {
        List<EffectiveTool> tools = new ArrayList<>();
        if (affinity.tools().contains(BlockProfile.Tool.PICKAXE)) tools.add(new EffectiveTool(label(BlockProfile.Tool.PICKAXE), Items.IRON_PICKAXE));
        if (affinity.tools().contains(BlockProfile.Tool.AXE)) tools.add(new EffectiveTool(label(BlockProfile.Tool.AXE), Items.IRON_AXE));
        if (affinity.tools().contains(BlockProfile.Tool.SHOVEL)) tools.add(new EffectiveTool(label(BlockProfile.Tool.SHOVEL), Items.IRON_SHOVEL));
        if (affinity.tools().contains(BlockProfile.Tool.HOE)) tools.add(new EffectiveTool(label(BlockProfile.Tool.HOE), Items.IRON_HOE));
        return tools;
    }

    @Override
    public TooltipGroup bodyGroup(TooltipContext ctx) {
        return TooltipGroups.PROPERTIES;
    }

}

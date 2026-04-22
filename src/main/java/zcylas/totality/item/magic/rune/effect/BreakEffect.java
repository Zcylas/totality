package zcylas.totality.item.magic.rune.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractEffectRune;
import zcylas.totality.api.magic.util.SpellUtil;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BreakEffect extends AbstractEffectRune {

    public static final BreakEffect INSTANCE = new BreakEffect();

    private BreakEffect() {
        super("break", "Break");
    }

    @Override
    public int getManaCost() {
        return 10;
    }

    /**
     * Harvest tier ladder:
     * 0 Amplify = 2 = Iron
     * 1 Amplify = 3 = Diamond
     * 2 Amplify = 4 = Netherite
     */
    public static int getHarvestLevel(FormulaStats stats) {
        return 2 + stats.getAmpCount();
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        int harvestLevel = getHarvestLevel(stats);
        List<BlockPos> blocks = SpellUtil.calcAoeBlocks(caster, hit.getBlockPos(), hit, stats.getAoeRadius());

        for (BlockPos pos : blocks) {
            breakBlock(serverLevel, pos, harvestLevel, caster);
        }
    }

    private void breakBlock(ServerLevel serverLevel, BlockPos pos,
                            int harvestLevel, LivingEntity caster) {
        BlockState state = serverLevel.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(serverLevel, pos) < 0) return;

        if (caster instanceof ServerPlayer player) {
            if (player.hasCorrectToolForDrops(state)
                    || harvestLevel >= getRequiredHarvestLevel(state)) {
                ItemStack tool = getToolForTier(harvestLevel).copy();
                state.getBlock().playerWillDestroy(serverLevel, pos, state, player);
                boolean removed = serverLevel.removeBlock(pos, false);
                if (removed) {
                    state.getBlock().destroy(serverLevel, pos, state);
                    state.getBlock().playerDestroy(serverLevel, player, pos, state,
                            serverLevel.getBlockEntity(pos), tool);
                }
            }
        } else {
            if (harvestLevel >= getRequiredHarvestLevel(state)) {
                serverLevel.destroyBlock(pos, true);
            }
        }
    }

    /**
     * Returns an appropriate tool ItemStack for the given harvest level.
     */
    private ItemStack getToolForTier(int level) {
        return switch (level) {
            case 0, 1 -> new ItemStack(Items.STONE_PICKAXE);
            case 2    -> new ItemStack(Items.IRON_PICKAXE);
            case 3    -> new ItemStack(Items.DIAMOND_PICKAXE);
            default   -> new ItemStack(Items.NETHERITE_PICKAXE);
        };
    }

    /**
     * Rough harvest level required for a block state.
     * 0=wood/stone, 1=stone, 2=iron, 3=diamond, 4=netherite
     */
    private int getRequiredHarvestLevel(BlockState state) {
        if (state.is(net.minecraft.tags.BlockTags.NEEDS_DIAMOND_TOOL)) return 3;
        if (state.is(net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL))    return 2;
        if (state.is(net.minecraft.tags.BlockTags.NEEDS_STONE_TOOL))   return 1;
        return 0;
    }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_BREAK; }

    @Override
    public int getTier() { return 1; }

    public String getDescription() { return "Breaks blocks at the target location."; }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("amplify", "aoe", "dampen", "pierce", "sensitive");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("amplify",   "Increases the harvest level.");
        map.put("aoe",       "Breaks blocks in a larger area.");
        map.put("dampen",    "Decreases the harvest level.");
        map.put("pierce",    "Also breaks blocks behind the hit block.");
        map.put("sensitive", "Breaks blocks with shears instead of a pickaxe.");
    }
}
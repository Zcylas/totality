package zcylas.totality.item.magic.rune.effect;

import net.minecraft.core.BlockPos;
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

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);

        // Don't break air or unbreakable blocks
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) return;

        int harvestLevel = getHarvestLevel(stats);
        ItemStack tool = getToolForTier(harvestLevel);

        if (caster instanceof ServerPlayer player) {
            // Check if the player's tool or our spell tool can harvest this block
            boolean canHarvest = player.hasCorrectToolForDrops(state)
                    || harvestLevel >= getRequiredHarvestLevel(state);

            if (!canHarvest) return;

            ItemStack toolCopy = tool.copy();
            state.getBlock().playerWillDestroy(serverLevel, pos, state, player);
            boolean removed = serverLevel.removeBlock(pos, false);
            if (removed) {
                state.getBlock().destroy(serverLevel, pos, state);
                state.getBlock().playerDestroy(
                        serverLevel, player, pos, state,
                        serverLevel.getBlockEntity(pos), toolCopy);
            }
        } else {
            // Non-player caster — just destroy with drops
            if (harvestLevel >= getRequiredHarvestLevel(state)) {
                level.destroyBlock(pos, true);
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
}
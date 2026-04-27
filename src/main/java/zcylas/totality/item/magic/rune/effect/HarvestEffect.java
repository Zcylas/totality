package zcylas.totality.item.magic.rune.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractEffectRune;
import zcylas.totality.api.magic.util.SpellUtil;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class HarvestEffect extends AbstractEffectRune {

    public static final HarvestEffect INSTANCE = new HarvestEffect();

    private HarvestEffect() { super("harvest", "Harvest"); }

    @Override public int getManaCost() { return 10; }
    @Override public int getTier()     { return 1; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_HARVEST; }
    public String getDescription() { return "Harvests grown crops without destroying the plant."; }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("aoe", "pierce", "fortune");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("aoe",     "Harvests crops in a larger area.");
        map.put("pierce",  "Harvests crops in a line.");
        map.put("fortune", "Increases crop drops.");
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        List<BlockPos> blocks = SpellUtil.calcAoeBlocks(
                caster, hit.getBlockPos(), hit, stats.getAoeRadius());

        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);

            // If hitting farmland, check the block above
            BlockPos checkPos = pos;
            BlockState checkState = state;

// If the block above is a crop, use that instead
            BlockState above = level.getBlockState(pos.above());
            if (above.getBlock() instanceof CropBlock
                    || above.getBlock() instanceof NetherWartBlock) {
                checkPos  = pos.above();
                checkState = above;
            }

            if (state.getBlock() instanceof CocoaBlock) {
                harvestCocoa(pos, state, serverLevel, caster, stats);
            } else if (state.getBlock() instanceof NetherWartBlock) {
                harvestNetherWart(pos, state, serverLevel, caster, stats);
            } else if (state.getBlock() instanceof CropBlock crop) {
                harvestCrop(pos, state, crop, serverLevel, caster, stats);
            }
        }
    }

    private void harvestCrop(BlockPos pos, BlockState state, CropBlock crop,
                             ServerLevel level, LivingEntity caster, FormulaStats stats) {
        if (!crop.isMaxAge(state)) return;
        spawnDrops(pos, state, level, caster, stats, true);
        level.setBlockAndUpdate(pos, crop.getStateForAge(1));
    }

    private void harvestCocoa(BlockPos pos, BlockState state,
                              ServerLevel level, LivingEntity caster, FormulaStats stats) {
        if (state.getValue(CocoaBlock.AGE) != 2) return;
        spawnDrops(pos, state, level, caster, stats, true);
        level.setBlockAndUpdate(pos, state.setValue(CocoaBlock.AGE, 0));
    }

    private void harvestNetherWart(BlockPos pos, BlockState state,
                                   ServerLevel level, LivingEntity caster, FormulaStats stats) {
        if (state.getValue(NetherWartBlock.AGE) != 3) return;
        spawnDrops(pos, state, level, caster, stats, true);
        level.setBlockAndUpdate(pos, state.setValue(NetherWartBlock.AGE, 0));
    }

    private void spawnDrops(BlockPos pos, BlockState state, ServerLevel level,
                            LivingEntity caster, FormulaStats stats, boolean removeSeed) {
        ItemStack tool = new ItemStack(net.minecraft.world.item.Items.NETHERITE_HOE);
        if (stats.getFortuneLevel() > 0) {
            tool.enchant(
                    level.registryAccess()
                            .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                            .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE),
                    Math.min(stats.getFortuneLevel(), 3));
        }

        List<ItemStack> drops = Block.getDrops(state, level, pos,
                level.getBlockEntity(pos), caster, tool);

        if (removeSeed) {
            for (ItemStack drop : drops) {
                if (drop.getItem() instanceof BlockItem bi
                        && bi.getBlock() == state.getBlock()) {
                    drop.shrink(1);
                    break;
                }
            }
        }

        Vec3 center = pos.getBottomCenter();
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            level.addFreshEntity(new ItemEntity(level,
                    center.x, center.y, center.z, drop));
        }
    }
}
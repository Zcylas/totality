package zcylas.totality.item.magic.rune.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import zcylas.totality.api.magic.grimoire.context.FormulaContext;
import zcylas.totality.api.magic.grimoire.context.FormulaResolver;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractEffectRune;
import zcylas.totality.api.magic.grimoire.util.SpellUtil;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class GrowEffect extends AbstractEffectRune {

    public static final GrowEffect INSTANCE = new GrowEffect();

    private GrowEffect() { super("grow", "Grow"); }

    @Override public int getManaCost() { return 70; }
    @Override public int getTier()     { return 2; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_GROW; }
    public String getDescription() { return "Causes plants to accelerate in growth as if bonemealed."; }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("aoe", "pierce");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("aoe",   "Grows plants in a larger area.");
        map.put("pierce","Grows plants in a line.");
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        List<BlockPos> blocks = SpellUtil.calcAoeBlocks(
                caster, hit.getBlockPos(), hit, stats.getAoeRadius());

        ItemStack boneMeal = new ItemStack(Items.BONE_MEAL, 64);

        for (BlockPos pos : blocks) {
            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof BonemealableBlock bonemealable) {
                if (bonemealable.isValidBonemealTarget(serverLevel, pos, state)) {
                    bonemealable.performBonemeal(serverLevel, serverLevel.getRandom(), pos, state);
                    serverLevel.levelEvent(1505, pos, 0); // bonemeal particles
                }
            } else {
                // Try water plants on adjacent face
                BlockPos relative = pos.relative(hit.getDirection());
                BlockState relativeState = level.getBlockState(relative);
                if (state.isFaceSturdy(level, pos, hit.getDirection())
                        && relativeState.getBlock() instanceof BonemealableBlock relBonemealable) {
                    if (relBonemealable.isValidBonemealTarget(serverLevel, relative, relativeState)) {
                        relBonemealable.performBonemeal(serverLevel, serverLevel.getRandom(), relative, relativeState);
                        serverLevel.levelEvent(1505, relative, 0);
                    }
                }
            }
        }
    }
}
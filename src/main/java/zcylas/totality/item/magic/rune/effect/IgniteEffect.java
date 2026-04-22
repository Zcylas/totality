package zcylas.totality.item.magic.rune.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractEffectRune;
import zcylas.totality.api.magic.util.SpellUtil;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class IgniteEffect extends AbstractEffectRune {

    public static final IgniteEffect INSTANCE = new IgniteEffect();

    // Base fire duration in seconds
    private static final int BASE_DURATION  = 3;
    private static final int AMP_DURATION   = 2;

    private IgniteEffect() {
        super("ignite", "Ignite");
    }

    @Override
    public int getManaCost() { return 15; }

    @Override
    public int getTier() { return 1; }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_IGNITE; }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        int durationTicks = (int)((BASE_DURATION + AMP_DURATION * stats.getAmpCount()
                + 2 * stats.getDurationModifier()) * 20);
        durationTicks = Math.max(20, durationTicks);
        hit.getEntity().setRemainingFireTicks(durationTicks);
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {
        if (!(level instanceof ServerLevel)) return;

        Direction face    = hit.getDirection();
        int radius        = (int) stats.getAoeRadius();
        List<BlockPos> blocks = zcylas.totality.api.magic.util.SpellUtil
                .calcAoeBlocks(caster, hit.getBlockPos(), hit, radius);

        for (BlockPos pos : blocks) {
            BlockPos above = pos.relative(face);
            if (BaseFireBlock.canBePlacedAt(level, above, face)) {
                BlockState fire = BaseFireBlock.getState(level, above);
                level.setBlock(above, fire, 3);
                level.updateNeighborsAt(above, fire.getBlock());
            }
        }
    }

    public String getDescription() { return "Sets the target on fire."; }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("amplify", "aoe", "extend_time", "reduce_time", "sensitive", "pierce");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("amplify",     "Increases the fire duration.");
        map.put("aoe",         "Sets fire to a larger area.");
        map.put("extend_time", "Increases the fire duration.");
        map.put("reduce_time", "Decreases the fire duration.");
        map.put("sensitive",   "Creates magic fire that won't spread or destroy blocks.");
        map.put("pierce",      "Sets fire to blocks behind the hit block.");
    }
}
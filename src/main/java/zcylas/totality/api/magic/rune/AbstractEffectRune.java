package zcylas.totality.api.magic.rune;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.FormulaStats;

/**
 * An Effect rune — defines WHAT happens at the hit point.
 * Examples: Break
 */
public abstract class AbstractEffectRune extends AbstractRune {

    public AbstractEffectRune(String id, String name) {
        super(id, name);
    }

    @Override
    public final int getTypeIndex() {
        return 2;
    }

    /**
     * Called when the effect resolves at a hit point.
     * Dispatches to onResolveBlock or onResolveEntity.
     */
    public final void onResolve(HitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        if (hit instanceof BlockHitResult blockHit) {
            onResolveBlock(blockHit, level, caster, stats, context, resolver);
        } else if (hit instanceof EntityHitResult entityHit) {
            onResolveEntity(entityHit, level, caster, stats, context, resolver);
        }
    }

    /**
     * Override to handle block hits.
     */
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {}

    /**
     * Override to handle entity hits.
     */
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {}
}
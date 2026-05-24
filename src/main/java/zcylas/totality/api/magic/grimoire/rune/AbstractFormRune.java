package zcylas.totality.api.magic.grimoire.rune;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import zcylas.totality.api.magic.grimoire.context.FormulaContext;
import zcylas.totality.api.magic.grimoire.context.FormulaResolver;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;

/**
 * A Form rune — defines HOW a spell is delivered.
 * Examples: Touch, Projectile
 */
public abstract class AbstractFormRune extends AbstractRune {

    public AbstractFormRune(String id, String name) {
        super(id, name);
    }

    @Override
    public final int getTypeIndex() {
        return 1;
    }

    /**
     * Called when the player right-clicks air.
     */
    public abstract CastResult onCast(ItemStack stack, LivingEntity caster,
                                      Level level, FormulaStats stats,
                                      FormulaContext context, FormulaResolver resolver);

    /**
     * Called when the player right-clicks a block.
     */
    public abstract CastResult onCastOnBlock(BlockHitResult hit, LivingEntity caster,
                                             FormulaStats stats, FormulaContext context,
                                             FormulaResolver resolver);

    /**
     * Called when the player right-clicks an entity.
     */
    public abstract CastResult onCastOnEntity(ItemStack stack, LivingEntity caster,
                                              Entity target, InteractionHand hand,
                                              FormulaStats stats, FormulaContext context,
                                              FormulaResolver resolver);

    public enum CastResult {
        SUCCESS,
        FAILURE
    }
}
package zcylas.totality.item.magic.rune.form;

import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractFormRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.Set;

public class SelfForm extends AbstractFormRune {

    public static final SelfForm INSTANCE = new SelfForm();

    private SelfForm() {
        super("self", "Self");
    }

    @Override
    public int getManaCost() { return 10; }

    @Override
    public int getTier() { return 1; }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_SELF; }

    @Override
    public CastResult onCast(ItemStack stack, LivingEntity caster,
                             Level level, FormulaStats stats,
                             FormulaContext context, FormulaResolver resolver) {
        // Self applies effects directly to the caster
        resolver.onResolveEffect(level,
                new net.minecraft.world.phys.EntityHitResult(caster));
        return CastResult.SUCCESS;
    }

    @Override
    public CastResult onCastOnBlock(BlockHitResult hit, LivingEntity caster,
                                    FormulaStats stats, FormulaContext context,
                                    FormulaResolver resolver) {
        resolver.onResolveEffect(caster.level(),
                new EntityHitResult(caster));
        return CastResult.SUCCESS;
    }

    @Override
    public CastResult onCastOnEntity(ItemStack stack, LivingEntity caster,
                                     Entity target, InteractionHand hand,
                                     FormulaStats stats, FormulaContext context,
                                     FormulaResolver resolver) {
        resolver.onResolveEffect(caster.level(),
                new EntityHitResult(caster));
        return CastResult.SUCCESS;
    }

    public String getDescription() { return "Applies the spell directly on the caster."; }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of(); // forms don't use augments
    }
}
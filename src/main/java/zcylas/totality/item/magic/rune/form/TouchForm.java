package zcylas.totality.item.magic.rune.form;

import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.grimoire.context.FormulaContext;
import zcylas.totality.api.magic.grimoire.context.FormulaResolver;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractFormRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.Map;
import java.util.Set;

public class TouchForm extends AbstractFormRune {

    public static final TouchForm INSTANCE = new TouchForm();

    private TouchForm() {
        super("touch", "Touch");
    }

    @Override
    public int getManaCost() {
        return 5;
    }

    @Override
    public CastResult onCast(ItemStack stack, LivingEntity caster,
                             Level level, FormulaStats stats,
                             FormulaContext context, FormulaResolver resolver) {
        if (!stats.isSensitive()) return CastResult.FAILURE;

        // With Sensitive, cast into air/fluid at look position
        double range = 5.0;
        Vec3 eyePos  = caster.getEyePosition();
        Vec3 lookVec = caster.getLookAngle();
        Vec3 target  = eyePos.add(lookVec.scale(range));
        net.minecraft.core.BlockPos blockPos = net.minecraft.core.BlockPos.containing(target);

        resolver.onResolveEffect(level, new BlockHitResult(
                target, caster.getDirection().getOpposite(), blockPos, true)); // isInside=true for air
        return CastResult.SUCCESS;
    }

    @Override
    public CastResult onCastOnBlock(BlockHitResult hit, LivingEntity caster,
                                    FormulaStats stats, FormulaContext context,
                                    FormulaResolver resolver) {
        resolver.onResolveEffect(caster.level(), hit);
        return CastResult.SUCCESS;
    }

    @Override
    public CastResult onCastOnEntity(ItemStack stack, LivingEntity caster,
                                     Entity target, InteractionHand hand,
                                     FormulaStats stats, FormulaContext context,
                                     FormulaResolver resolver) {
        resolver.onResolveEffect(caster.level(), new EntityHitResult(target));
        return CastResult.SUCCESS;
    }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_TOUCH; }

    @Override
    public int getTier() { return 1; }

    public String getDescription() { return "Applies the spell at the targeted block or entity."; }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("sensitive");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("sensitive", "Can target fluids and air.");
    }
}
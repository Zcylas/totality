package zcylas.totality.item.magic.rune.form;

import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractFormRune;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.entity.magic.GrimoireProjectileEntity;

import java.util.Map;
import java.util.Set;

public class ProjectileForm extends AbstractFormRune {

    public static final ProjectileForm INSTANCE = new ProjectileForm();

    private static final float BASE_SPEED = 1.5f;
    private static final float ACCEL_BONUS = 0.5f;


    private ProjectileForm() {
        super("projectile", "Projectile");
    }

    @Override
    public int getManaCost() {
        return 10;
    }

    @Override
    public CastResult onCast(ItemStack stack, LivingEntity caster,
                             Level level, FormulaStats stats,
                             FormulaContext context, FormulaResolver resolver) {
        spawnProjectile(caster, level, context, stats);
        return CastResult.SUCCESS;
    }

    @Override
    public CastResult onCastOnBlock(BlockHitResult hit, LivingEntity caster,
                                    FormulaStats stats, FormulaContext context,
                                    FormulaResolver resolver) {
        spawnProjectile(caster, caster.level(), context, stats);
        return CastResult.SUCCESS;
    }

    @Override
    public CastResult onCastOnEntity(ItemStack stack, LivingEntity caster,
                                     Entity target, InteractionHand hand,
                                     FormulaStats stats, FormulaContext context,
                                     FormulaResolver resolver) {
        spawnProjectile(caster, caster.level(), context, stats);
        return CastResult.SUCCESS;
    }

    private void spawnProjectile(LivingEntity caster, Level level,
                                 FormulaContext context, FormulaStats stats) {
        if (level.isClientSide()) return;

        float speed = BASE_SPEED + ACCEL_BONUS * stats.getAccelerationModifier();
        speed = Math.max(0.1f, speed);

        int count = 1 + stats.getSplitCount();

        for (int i = 0; i < count; i++) {
            GrimoireProjectileEntity projectile = new GrimoireProjectileEntity(
                    level, caster, context.getFormula());

            float spread = count > 1 ? (i - (count - 1) / 2.0f) * 10.0f : 0f;

            projectile.shootFromRotation(caster, caster.getXRot(),
                    caster.getYRot() + spread, 0f, speed, 0f);

            level.addFreshEntity(projectile);
        }
    }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("pierce", "sensitive", "accelerate", "decelerate", "split");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("pierce",      "Projectiles pierce through entities and blocks an additional time.");
        map.put("sensitive",   "Projectiles hit plants and materials that don't block motion.");
        map.put("accelerate",  "Increases projectile speed.");
        map.put("decelerate",  "Decreases projectile speed.");
        map.put("split",       "Fires additional projectiles.");
    }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_PROJECTILE; }

    @Override
    public int getTier() { return 1; }

    public String getDescription() { return "Fires a projectile that applies the spell on impact."; }

}
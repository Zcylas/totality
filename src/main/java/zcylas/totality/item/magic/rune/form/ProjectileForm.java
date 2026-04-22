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

    private static final float PROJECTILE_SPEED = 1.5f;

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
        spawnProjectile(caster, level, context);
        return CastResult.SUCCESS;
    }

    @Override
    public CastResult onCastOnBlock(BlockHitResult hit, LivingEntity caster,
                                    FormulaStats stats, FormulaContext context,
                                    FormulaResolver resolver) {
        spawnProjectile(caster, caster.level(), context);
        return CastResult.SUCCESS;
    }

    @Override
    public CastResult onCastOnEntity(ItemStack stack, LivingEntity caster,
                                     Entity target, InteractionHand hand,
                                     FormulaStats stats, FormulaContext context,
                                     FormulaResolver resolver) {
        spawnProjectile(caster, caster.level(), context);
        return CastResult.SUCCESS;
    }

    private void spawnProjectile(LivingEntity caster, Level level,
                                 FormulaContext context) {
        if (level.isClientSide()) return;

        GrimoireProjectileEntity projectile = new GrimoireProjectileEntity(
                level, caster, context.getFormula());

        // Shoot in the direction the caster is looking
        projectile.shootFromRotation(
                caster,
                caster.getXRot(),
                caster.getYRot(),
                0f,
                PROJECTILE_SPEED,
                0f); // 0 inaccuracy = perfectly straight

        level.addFreshEntity(projectile);
    }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_PROJECTILE; }

    @Override
    public int getTier() { return 1; }

    public String getDescription() { return "Fires a projectile that applies the spell on impact."; }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("pierce", "sensitive");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("pierce",    "Projectiles pierce through entities and blocks an additional time.");
        map.put("sensitive", "Projectiles hit plants and materials that don't block motion.");
    }
}
package zcylas.totality.item.magic.rune.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.grimoire.context.FormulaContext;
import zcylas.totality.api.magic.grimoire.context.FormulaResolver;
import zcylas.totality.api.magic.grimoire.formula.ArcaneFormula;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractEffectRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.Map;
import java.util.Set;

public class BurstEffect extends AbstractEffectRune {

    public static final BurstEffect INSTANCE = new BurstEffect();

    private BurstEffect() { super("burst", "Burst"); }

    @Override public int getManaCost() { return 500; }
    @Override public int getTier()     { return 3; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_BURST; }

    @Override
    public String getDescription() {
        return "Resolves remaining spell effects in a spherical area around the target.";
    }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("aoe", "sensitive", "dampen");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("aoe",       "Increases the burst radius.");
        map.put("sensitive", "Targets blocks instead of entities.");
        map.put("dampen",    "Targets only the shell of the sphere, not the interior.");
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {
        makeSphere(hit.getBlockPos(), level, caster, stats, context, resolver);
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        makeSphere(hit.getEntity().blockPosition(), level, caster, stats, context, resolver);
    }

    private void makeSphere(BlockPos center, Level level, LivingEntity caster,
                            FormulaStats stats, FormulaContext context,
                            FormulaResolver resolver) {
        if (level.isClientSide()) return;

        ArcaneFormula formula = context.getFormula();
        int myIndex = formula.getRunes().indexOf(this);
        if (myIndex >= formula.getRunes().size() - 1) return;

        // Child context starts AFTER Burst — equivalent to AN's makeChildContext()
        int effectIndex = myIndex + 1;

        int radius = (int) stats.getAoeRadius() + (stats.isSensitive() ? 1 : 3);
        boolean shellOnly = stats.getAmpCount() < 0;
        Thread.dumpStack();
        if (stats.isSensitive()) {
            for (BlockPos pos : BlockPos.withinManhattan(center, radius, radius, radius)) {
                double dist = Math.sqrt(center.distSqr(pos));
                if (shellOnly && (dist < radius - 0.5 || dist > radius + 0.5)) continue;
                if (!shellOnly && dist > radius + 0.5) continue;

                BlockPos immutable = pos.immutable();
                Vec3 hitVec = Vec3.atCenterOf(immutable);

                // Child context contains only runes after Burst
                FormulaContext childContext = context.makeChildContext(effectIndex);
                FormulaResolver childResolver = new FormulaResolver(childContext);
                childResolver.onResolveEffect(level,
                        new BlockHitResult(hitVec, Direction.UP, immutable, false));
            }
        } else {
            AABB box = new AABB(center).inflate(radius);
            for (Entity entity : level.getEntities(caster, box, Entity::isAlive)) {
                double dist = Math.sqrt(center.distSqr(entity.blockPosition()));
                if (shellOnly && (dist < radius - 0.5 || dist > radius + 0.5)) continue;
                if (!shellOnly && dist > radius + 0.5) continue;

                FormulaContext childContext = context.makeChildContext(effectIndex);
                FormulaResolver childResolver = new FormulaResolver(childContext);
                childResolver.onResolveEffect(level, new EntityHitResult(entity));
            }
        }

        context.cancel();
    }
}
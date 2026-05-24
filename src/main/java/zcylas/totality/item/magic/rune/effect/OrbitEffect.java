package zcylas.totality.item.magic.rune.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.grimoire.context.FormulaContext;
import zcylas.totality.api.magic.grimoire.context.FormulaResolver;
import zcylas.totality.api.magic.grimoire.formula.ArcaneFormula;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractEffectRune;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.entity.magic.OrbitProjectileEntity;

import java.util.Map;
import java.util.Set;

public class OrbitEffect extends AbstractEffectRune {

    public static final OrbitEffect INSTANCE = new OrbitEffect();

    private static final int   BASE_COUNT    = 3;
    private static final float BASE_RADIUS   = 1.5f;
    private static final float BASE_SPEED    = 1.0f;
    private static final int   BASE_LIFETIME = 60 * 20;
    private static final int   AMP_LIFETIME  = 30 * 20;

    private OrbitEffect() { super("orbit", "Orbit"); }

    @Override public int getManaCost() { return 50; }
    @Override public int getTier()     { return 2; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_ORBIT; }

    @Override
    public String getDescription() {
        return "Summons orbiting projectiles around the target that cast spells on entities they hit.";
    }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("amplify", "aoe", "extend_time", "reduce_time",
                "sensitive", "pierce", "dampen", "split");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("amplify",     "Increases the speed of the orbiting projectiles.");
        map.put("aoe",         "Increases the orbit radius.");
        map.put("extend_time", "Increases the duration of the orbiting projectiles.");
        map.put("reduce_time", "Decreases the duration of the orbiting projectiles.");
        map.put("sensitive",   "Allows orbiting projectiles to hit blocks.");
        map.put("pierce",      "Increases the number of orbiting projectiles.");
        map.put("dampen",      "Decreases the speed of the orbiting projectiles.");
        map.put("split",       "Increases the number of orbiting projectiles.");
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {
        spawnOrbits(level, caster, context.getFormula(), stats, hit.getLocation(), context);
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        int   count    = BASE_COUNT + stats.getPierceCount() + stats.getSplitCount();
        float radius   = BASE_RADIUS + (float) stats.getAoeRadius();
        float speed    = Math.max(0.1f, BASE_SPEED + stats.getAmpCount() * 0.5f);
        int   lifetime = (int) Math.max(20, BASE_LIFETIME + AMP_LIFETIME * stats.getDurationModifier());

        for (int i = 0; i < count; i++) {
            OrbitProjectileEntity orb = new OrbitProjectileEntity(
                    level, caster, context.getFormula(),
                    i, count, radius, speed, lifetime, stats.isSensitive());
            orb.setTrackedEntity(hit.getEntity());
            level.addFreshEntity(orb);
        }

        context.cancel(); // ← was missing!
    }

    private void spawnOrbits(Level level, LivingEntity caster, ArcaneFormula formula,
                             FormulaStats stats, Vec3 groundPos, FormulaContext context) {
        int   count    = BASE_COUNT + stats.getPierceCount() + stats.getSplitCount();
        float radius   = BASE_RADIUS + (float) stats.getAoeRadius();
        float speed    = Math.max(0.1f, BASE_SPEED + stats.getAmpCount() * 0.5f);
        int   lifetime = (int) Math.max(20, BASE_LIFETIME + AMP_LIFETIME * stats.getDurationModifier());

        for (int i = 0; i < count; i++) {
            OrbitProjectileEntity orb = new OrbitProjectileEntity(
                    level, caster, formula,
                    groundPos, i, count, radius, speed, lifetime, stats.isSensitive());
            level.addFreshEntity(orb);
        }

        context.cancel(); // ← was missing!
    }
}
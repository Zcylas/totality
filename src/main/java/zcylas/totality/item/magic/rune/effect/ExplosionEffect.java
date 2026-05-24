package zcylas.totality.item.magic.rune.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.grimoire.context.FormulaContext;
import zcylas.totality.api.magic.grimoire.context.FormulaResolver;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractEffectRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.Map;
import java.util.Set;

public class ExplosionEffect extends AbstractEffectRune {

    public static final ExplosionEffect INSTANCE = new ExplosionEffect();

    private static final float BASE_RADIUS  = 2.0f;
    private static final float AMP_RADIUS   = 0.5f;
    private static final float AOE_RADIUS   = 1.5f;

    private ExplosionEffect() {
        super("explosion", "Explosion");
    }

    @Override
    public int getManaCost() { return 200; }

    @Override
    public int getTier() { return 2; }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_EXPLOSION; }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {
        explode(level, caster, hit.getLocation(), stats);
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        explode(level, caster, hit.getEntity().position(), stats);
    }

    private void explode(Level level, LivingEntity caster, Vec3 pos, FormulaStats stats) {
        float radius = BASE_RADIUS
                + AMP_RADIUS * stats.getAmpCount()
                + AOE_RADIUS * (float) stats.getAoeRadius();

        level.explode(
                caster,
                pos.x, pos.y, pos.z,
                radius,
                Level.ExplosionInteraction.MOB);
    }

    public String getDescription() { return "Creates an explosion at the target location."; }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("amplify", "aoe", "dampen", "sensitive");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("amplify",   "Increases the explosion size and damage.");
        map.put("aoe",       "Greatly increases the explosion size.");
        map.put("dampen",    "Decreases the explosion size and damage.");
        map.put("sensitive", "Drops all blocks instead of destroying them.");
    }
}
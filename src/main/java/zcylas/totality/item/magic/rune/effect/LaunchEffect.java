package zcylas.totality.item.magic.rune.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import zcylas.totality.api.magic.context.FormulaContext;
import zcylas.totality.api.magic.context.FormulaResolver;
import zcylas.totality.api.magic.formula.FormulaStats;
import zcylas.totality.api.magic.rune.AbstractEffectRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.Map;
import java.util.Set;

public class LaunchEffect extends AbstractEffectRune {

    public static final LaunchEffect INSTANCE = new LaunchEffect();

    // Base knockup amount — same as AN's default
    private static final double BASE_KNOCKUP = 0.8;
    // Additional knockup per Amplify
    private static final double AMP_KNOCKUP   = 0.25;

    private LaunchEffect() {
        super("launch", "Launch");
    }

    @Override
    public int getManaCost() { return 30; }

    @Override
    public int getTier() { return 1; }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_LAUNCH; }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        double knockup = BASE_KNOCKUP + AMP_KNOCKUP * stats.getAmpCount();

        if (stats.getAoeRadius() > 0) {
            // Launch all nearby entities
            double radius = 2.0 + stats.getAoeRadius();
            net.minecraft.world.phys.AABB box = hit.getEntity()
                    .getBoundingBox().inflate(radius);
            for (Entity entity : level.getEntities(caster, box, Entity::isAlive)) {
                entity.setDeltaMovement(entity.getDeltaMovement().add(0, knockup, 0));
                entity.hurtMarked = true;
                entity.fallDistance = 0.0f;
            }
        } else {
            Entity entity = hit.getEntity();
            entity.setDeltaMovement(entity.getDeltaMovement().add(0, knockup, 0));
            entity.hurtMarked = true;
            entity.fallDistance = 0.0f;
        }
    }

    @Override
    public void onResolveBlock(BlockHitResult hit, Level level,
                               LivingEntity caster, FormulaStats stats,
                               FormulaContext context, FormulaResolver resolver) {
        // Block launching requires FallingBlockEntity logic — skipped for now
    }

    public String getDescription() { return "Launches the target entity into the air."; }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("amplify", "aoe", "dampen", "sensitive");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("amplify",   "Increases the knockup force.");
        map.put("aoe",       "Launches entities in a larger area.");
        map.put("dampen",    "Decreases the knockup force.");
        map.put("sensitive", "Only launches entities, not blocks.");
    }
}
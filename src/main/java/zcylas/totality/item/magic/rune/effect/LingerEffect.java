package zcylas.totality.item.magic.rune.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.grimoire.context.FormulaContext;
import zcylas.totality.api.magic.grimoire.context.FormulaResolver;
import zcylas.totality.api.magic.grimoire.formula.ArcaneFormula;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractEffectRune;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.entity.magic.LingerEntity;

import java.util.Map;
import java.util.Set;

public class LingerEffect extends AbstractEffectRune {

    public static final LingerEffect INSTANCE = new LingerEffect();

    private static final int BASE_DURATION = 20 * 10; // 10 seconds

    private LingerEffect() { super("linger", "Linger"); }

    @Override public int getManaCost() { return 500; }
    @Override public int getTier()     { return 3; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_LINGER; }
    public String getDescription() {
        return "Creates a lingering field that applies remaining spell effects to nearby entities or blocks.";
    }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("aoe", "sensitive", "accelerate", "decelerate",
                "extend_time", "reduce_time", "dampen");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("aoe",         "Increases the target area.");
        map.put("sensitive",   "Targets blocks instead of entities.");
        map.put("accelerate",  "Casts spells faster.");
        map.put("decelerate",  "Casts spells slower.");
        map.put("extend_time", "Increases the duration.");
        map.put("reduce_time", "Decreases the duration.");
        map.put("dampen",      "Ignores gravity.");
    }

    @Override
    public void onResolve(HitResult hit, Level level, LivingEntity caster,
                          FormulaStats stats, FormulaContext context,
                          FormulaResolver resolver) {
        if (level.isClientSide()) return;

        ArcaneFormula formula = context.getFormula();
        int myIndex = formula.getRunes().indexOf(this);

        // Nothing after Linger — no point spawning
        if (myIndex >= formula.getRunes().size() - 1) return;

        int effectIndex = myIndex + 1;
        int duration = (int)(BASE_DURATION * (1.0 + stats.getDurationModifier() * 0.5));
        duration = Math.max(20, duration);

        Vec3 pos = switch (hit.getType()) {
            case BLOCK -> ((BlockHitResult) hit).getLocation();
            case ENTITY -> ((EntityHitResult) hit).getEntity().position();
            default -> caster.position();
        };

        LingerEntity linger = new LingerEntity(level, caster, formula,
                effectIndex, stats, duration);
        linger.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(linger);

        // Cancel remaining resolution — Linger handles it
        context.cancel();
    }
}
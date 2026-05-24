package zcylas.totality.item.magic.rune.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import zcylas.totality.api.magic.grimoire.context.FormulaContext;
import zcylas.totality.api.magic.grimoire.context.FormulaResolver;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractEffectRune;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.init.ModEffects;

import java.util.Map;
import java.util.Set;

public class GlideEffect extends AbstractEffectRune {

    public static final GlideEffect INSTANCE = new GlideEffect();

    private static final int BASE_DURATION = 30;  // seconds
    private static final int AMP_DURATION  = 15;  // seconds per amp

    private GlideEffect() {
        super("glide", "Glide");
    }

    @Override
    public int getManaCost() { return 100; }

    @Override
    public int getTier() { return 2; }

    @Override
    public Identifier getIcon() { return TotalityGuiSprites.RUNE_GLIDE; }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        if (!(hit.getEntity() instanceof LivingEntity target)) return;
        int durationTicks = (int)((BASE_DURATION + AMP_DURATION * stats.getAmpCount()
                + 15 * stats.getDurationModifier()) * 20);
        durationTicks = Math.max(20, durationTicks); // minimum 1 second;
        target.addEffect(new MobEffectInstance(ModEffects.GLIDE, durationTicks, 0, false, true, true));
    }

    public String getDescription() { return "Grants elytra-like flight to the target."; }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("amplify", "extend_time", "reduce_time");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("amplify",     "Increases the glide duration.");
        map.put("extend_time", "Increases the glide duration.");
        map.put("reduce_time", "Decreases the glide duration.");
    }
}
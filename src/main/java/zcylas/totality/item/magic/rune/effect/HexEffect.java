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

public class HexEffect extends AbstractEffectRune {

    public static final HexEffect INSTANCE = new HexEffect();

    private static final int BASE_DURATION = 30 * 20;  // 30 seconds
    private static final int AMP_DURATION  = 8  * 20;  // +8 seconds per extend

    private HexEffect() { super("hex", "Hex"); }

    @Override public int getManaCost() { return 100; }
    @Override public int getTier()     { return 2; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_HEX; }

    @Override
    public String getDescription() {
        return "Applies the Hex debuff, increasing damage taken and reducing mana regeneration.";
    }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("amplify", "extend_time", "reduce_time");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("amplify",     "Increases the level of Hex applied.");
        map.put("extend_time", "Increases the duration of Hex.");
        map.put("reduce_time", "Decreases the duration of Hex.");
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        if (!(hit.getEntity() instanceof LivingEntity target)) return;

        int duration = (int)(BASE_DURATION + AMP_DURATION * stats.getDurationModifier());
        duration     = Math.max(20, duration);
        int amplifier = Math.max(0, stats.getAmpCount());

        target.addEffect(new MobEffectInstance(
                ModEffects.HEX, duration, amplifier, false, true, true));
    }
}
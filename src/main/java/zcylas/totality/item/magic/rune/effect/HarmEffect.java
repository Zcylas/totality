package zcylas.totality.item.magic.rune.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import zcylas.totality.api.magic.grimoire.context.FormulaContext;
import zcylas.totality.api.magic.grimoire.context.FormulaResolver;
import zcylas.totality.api.magic.grimoire.formula.FormulaStats;
import zcylas.totality.api.magic.grimoire.rune.AbstractEffectRune;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.Map;
import java.util.Set;

public class HarmEffect extends AbstractEffectRune {

    public static final HarmEffect INSTANCE = new HarmEffect();

    private static final float BASE_DAMAGE  = 5.0f;
    private static final float AMP_DAMAGE   = 2.0f;
    private static final int   BASE_POISON  = 5 * 20;  // 5 seconds
    private static final int   AMP_POISON   = 5 * 20;  // +5 seconds per extend

    private HarmEffect() { super("harm", "Harm"); }

    @Override public int getManaCost() { return 15; }
    @Override public int getTier()     { return 1; }
    @Override public Identifier getIcon() { return TotalityGuiSprites.RUNE_HARM; }

    @Override
    public String getDescription() {
        return "Damages a target. Extend Time applies Poison instead. Multiple Harms without delay won't stack due to invincibility frames.";
    }

    @Override
    public Set<String> getCompatibleAugments() {
        return Set.of("amplify", "dampen", "extend_time", "reduce_time", "fortune", "randomize");
    }

    @Override
    public void buildAugmentDescriptions(Map<String, String> map) {
        map.put("amplify",     "Increases damage dealt, or Poison level.");
        map.put("dampen",      "Decreases damage dealt.");
        map.put("extend_time", "Applies Poison instead of damage, increases duration.");
        map.put("reduce_time", "Applies Poison instead of damage, decreases duration.");
        map.put("fortune",     "Increases looting on mob kills.");
        map.put("randomize",   "Randomizes the damage dealt.");
    }

    @Override
    public void onResolveEntity(EntityHitResult hit, Level level,
                                LivingEntity caster, FormulaStats stats,
                                FormulaContext context, FormulaResolver resolver) {
        if (hit.getEntity() instanceof ItemEntity) return;
        if (!(hit.getEntity() instanceof LivingEntity target)) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        boolean usePoison = stats.getDurationModifier() != 0;

        if (usePoison) {
            int duration = (int)(BASE_POISON + AMP_POISON * stats.getDurationModifier());
            duration     = Math.max(20, duration);
            int amplifier = Math.max(0, stats.getAmpCount());
            target.addEffect(new MobEffectInstance(MobEffects.POISON, duration, amplifier));
        } else {
            float damage = BASE_DAMAGE + AMP_DAMAGE * stats.getAmpCount();
            if (stats.isRandomized()) {
                damage = (float)(Math.random() * damage * 2);
            }
            damage = Math.max(0.5f, damage);

            net.minecraft.core.Registry<net.minecraft.world.damagesource.DamageType> registry =
                    serverLevel.registryAccess()
                            .lookupOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
            net.minecraft.core.Holder<net.minecraft.world.damagesource.DamageType> type =
                    registry.getOrThrow(net.minecraft.world.damagesource.DamageTypes.MAGIC);

            zcylas.totality.api.magic.grimoire.damage.SpellDamageSource source =
                    new zcylas.totality.api.magic.grimoire.damage.SpellDamageSource(type, caster, caster);
            source.setLuckLevel(stats.getFortuneLevel());

            target.hurtServer(serverLevel, source, damage);
        }
    }
}
package zcylas.totality.effect;

import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.Totality;
import zcylas.totality.api.core.rpgutils.RpgDisplayUtils;

/**
 * Temporarily increases maximum Health by a flat amount.
 *
 * Matches Skyrim's Fortify Health: adds flat HP to max health for the duration.
 * Uses Minecraft's built-in attribute modifier system on MAX_HEALTH.
 * The modifier is automatically removed when the effect expires.
 *
 * Amplifier stores flat vanilla HP bonus (0-indexed):
 *   amplifier = vanillaBonusHp - 1
 *   e.g. +4 vanilla HP (+20 display) → amplifier 3
 *
 * applyBonus() handles the conversion:
 *   magnitude > 1  = flat display HP (from FortifyTier: 20/40/60/80/100)
 *   magnitude <= 1 = percentage of current max HP (brewed: 0.17f)
 */
public class FortifyHealthEffect extends MobEffect {

    public static final FortifyHealthEffect INSTANCE = new FortifyHealthEffect();

    // Unique identifier for the attribute modifier — must be consistent
    private static final Identifier MODIFIER_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "fortify_health_bonus");

    private FortifyHealthEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF4444);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false; // passive effect — no per-tick application needed
    }

    /**
     * Applies the Fortify Health bonus to the player.
     *
     * magnitude > 1  = flat display HP (e.g. 20.0f from FortifyTier)
     * magnitude <= 1 = percentage of current max vanilla HP (e.g. 0.17f from brew)
     */
    public static void applyBonus(Player player, float magnitude, int durationTicks) {
        // magnitude is always flat display HP
        float vanillaBonus = RpgDisplayUtils.toVanillaHp(Math.round(magnitude));
        int amplifier = Math.max(0, Math.round(vanillaBonus * 10) - 1);

        player.removeEffect(zcylas.totality.init.ModEffects.FORTIFY_HEALTH);
        player.addEffect(new MobEffectInstance(
                zcylas.totality.init.ModEffects.FORTIFY_HEALTH,
                durationTicks, amplifier, false, true, true));
    }

    /**
     * Returns the current vanilla HP bonus from the effect on this player.
     * amplifier is 0-indexed, so bonus = amplifier + 1.
     * Returns 0 if the effect is not active.
     */
    public static float getCurrentVanillaBonus(Player player) {
        MobEffectInstance inst = player.getEffect(
                zcylas.totality.init.ModEffects.FORTIFY_HEALTH);
        if (inst == null) return 0f;
        return (inst.getAmplifier() + 1) / 10f; // ← /10f not +1
    }

    /**
     * Returns the current display HP bonus from the effect on this player.
     */
    public static int getCurrentDisplayBonus(Player player) {
        return zcylas.totality.api.core.rpgutils.RpgDisplayUtils.toDisplayHp(
                getCurrentVanillaBonus(player));
    }
    @Override
    public void onEffectAdded(LivingEntity entity, int amplifier) {
        // Recover: (amplifier + 1) / 10f
        float vanillaBonus = (amplifier + 1) / 10f;
        float currentHp = entity.getHealth();
        var attr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.removeModifier(MODIFIER_ID);
            attr.addPermanentModifier(new AttributeModifier(
                    MODIFIER_ID, vanillaBonus, AttributeModifier.Operation.ADD_VALUE));
            entity.setHealth(currentHp);
        }
    }

    @Override
    public void onEffectRemoved(MobEffectInstance effectInstance, LivingEntity entity) {
        super.onEffectRemoved(effectInstance, entity);
        var attr = entity.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) {
            attr.removeModifier(MODIFIER_ID);
            if (entity.getHealth() > entity.getMaxHealth()) {
                entity.setHealth(entity.getMaxHealth());
            }
        }
    }
}
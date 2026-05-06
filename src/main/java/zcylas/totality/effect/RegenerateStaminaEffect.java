package zcylas.totality.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.init.ModEffects;

/**
 * Temporarily boosts mana regeneration rate.
 * The actual boost is read directly in PlayerManaManager.getRegenPercent()
 * by checking if the player has this effect and reading the amplifier.
 *
 * Amplifier stores the regen boost as integer percentage points:
 *   regenBoost 0.22 → amplifier 22
 *   regenBoost 0.50 → amplifier 50
 */
public class RegenerateStaminaEffect extends MobEffect {

    public static final RegenerateStaminaEffect INSTANCE = new RegenerateStaminaEffect();

    private RegenerateStaminaEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x7FAF3A);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    /**
     * regenBoost = fraction to add to regen (0.22 = 22% faster, 0.50 = 50% faster)
     * Stored as integer percentage in amplifier for PlayerManaManager to read.
     */
    public static void applyBonus(Player player, float regenBoost, int durationTicks) {
        int amplifier = Math.max(0, (int)(regenBoost * 100) - 1);
        player.addEffect(new MobEffectInstance(
                ModEffects.REGENERATE_STAMINA,
                durationTicks, amplifier, false, true, true));
    }
}
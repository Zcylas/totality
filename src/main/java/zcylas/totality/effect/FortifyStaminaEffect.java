package zcylas.totality.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import zcylas.totality.init.ModEffects;

/**
 * Temporarily increases max mana.
 * The actual bonus is read directly in PlayerManaManager.getMaxMana()
 * by checking if the player has this effect and reading the amplifier.
 *
 * Amplifier stores the flat mana bonus:
 *   amplifier = bonus - 1 (MobEffect amplifiers are 0-indexed)
 *   So bonus 20 = amplifier 19, bonus 100 = amplifier 99
 */
public class FortifyStaminaEffect extends MobEffect {

    public static final FortifyStaminaEffect INSTANCE = new FortifyStaminaEffect();

    private FortifyStaminaEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x7FAF3A);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    /**
     * magnitude > 1 = flat mana points (FortifyTier: 20/40/60/80/100)
     * magnitude <= 1 = percentage of max mana (brewed: 0.17)
     *
     * Stores the bonus in the amplifier field so PlayerManaManager can read it.
     */
    public static void applyBonus(Player player, float magnitude, int durationTicks,
                                  int currentMaxMana) {
        int bonus = magnitude > 1
                ? (int) magnitude
                : (int)(currentMaxMana * magnitude);

        // Store bonus as amplifier (0-indexed, so bonus-1)
        player.addEffect(new MobEffectInstance(
                ModEffects.FORTIFY_STAMINA,
                durationTicks, Math.max(0, bonus - 1), false, true, true));
    }
}
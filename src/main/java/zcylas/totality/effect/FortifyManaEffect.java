package zcylas.totality.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
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
public class FortifyManaEffect extends MobEffect {

    public static final FortifyManaEffect INSTANCE = new FortifyManaEffect();

    private FortifyManaEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x4444FF);
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
    public static void applyBonus(Player player, float magnitude, int durationTicks) {
        int bonus = Math.round(magnitude);
        player.addEffect(new MobEffectInstance(
                zcylas.totality.init.ModEffects.FORTIFY_MANA,
                durationTicks, Math.max(0, bonus - 1), false, true, true));
    }
}
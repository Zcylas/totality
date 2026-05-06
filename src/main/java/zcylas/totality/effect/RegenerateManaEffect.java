package zcylas.totality.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Temporarily boosts mana regeneration rate.
 * The actual boost is read directly in PlayerManaManager.getRegenPercent()
 * by checking if the player has this effect and reading the amplifier.
 *
 * Amplifier stores the regen boost as integer percentage points:
 *   regenBoost 0.22 → amplifier 22
 *   regenBoost 0.50 → amplifier 50
 */
public class RegenerateManaEffect extends MobEffect {

    public static final RegenerateManaEffect INSTANCE = new RegenerateManaEffect();

    private RegenerateManaEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x44AAFF);
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
                zcylas.totality.init.ModEffects.REGENERATE_MANA,
                durationTicks, amplifier, false, true, true));
    }
}
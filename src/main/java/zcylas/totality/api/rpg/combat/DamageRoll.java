package zcylas.totality.api.rpg.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import zcylas.totality.api.dice.Dice;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolls multi-die damage server-side and returns a {@link DamageRollResult}.
 *
 * Unlike attack rolls and saving throws, damage rolls do not open the dice screen —
 * results are shown as a lightweight HUD notification via {@link DamageRollNotification}.
 *
 * Usage:
 *   DamageRollResult dmg = DamageRoll.roll(caster, 8, Dice.D6);
 *   DamageRollResult dmg = DamageRoll.roll(caster, 2, Dice.D6, 3); // 2d6 + 3
 */
public final class DamageRoll {

    private DamageRoll() {}

    /**
     * Rolls count×dice with no flat modifier.
     *
     * @param roller the entity whose RNG is used (always server-side)
     * @param count  number of dice (e.g. 8 for 8d6)
     * @param dice   die type (e.g. Dice.D6)
     */
    public static DamageRollResult roll(LivingEntity roller, int count, Dice dice) {
        return roll(roller, count, dice, 0);
    }

    /**
     * Rolls count×dice + modifier.
     *
     * @param modifier flat bonus added after rolling (can be negative)
     */
    public static DamageRollResult roll(LivingEntity roller, int count, Dice dice, int modifier) {
        var random = roller.getRandom();
        List<Integer> rolls = new ArrayList<>(count);
        int sum = 0;

        for (int i = 0; i < count; i++) {
            int result = dice.roll(random);
            rolls.add(result);
            sum += result;
        }

        int total = Math.max(0, sum + modifier); // damage can't go below 0
        return new DamageRollResult(count, dice, rolls, modifier, total);
    }

    /**
     * Convenience: rolls and applies the caster's spellcasting modifier as a flat bonus.
     * Used for cantrips and spells that add the caster's stat to damage.
     */
    public static DamageRollResult rollWithSpellMod(ServerPlayer caster,
                                                    int count,
                                                    Dice dice,
                                                    zcylas.totality.api.rpg.stats.AbilityScore spellcastingAbility) {
        var stats = zcylas.totality.api.rpg.stats.StatsComponents.getStats(caster);
        int mod   = stats != null ? stats.getModifier(spellcastingAbility) : 0;
        return roll(caster, count, dice, mod);
    }
}
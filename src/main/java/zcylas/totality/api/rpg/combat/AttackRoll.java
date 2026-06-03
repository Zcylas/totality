package zcylas.totality.api.rpg.combat;

import net.minecraft.world.entity.LivingEntity;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.mob.stats.MobCombatStatsHolder;
import zcylas.totality.api.rpg.classes.PlayerClassComponent;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatsComponents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side attack roll utility. No UI — rolls instantly and returns the outcome.
 * The dice screen (PendingDiceRollManager) is NOT used here — that's for
 * dialogue, quests, and narrative events only.
 *
 * Results are communicated via DamageRollNotification / CombatTextPayload.
 */
public final class AttackRoll {

    private AttackRoll() {}

    /**
     * Rolls a weapon or spell attack for any living entity attacker.
     *
     * @param attacker     the attacking entity (player or mob)
     * @param target       the defending entity
     * @param abilityScore STR for melee, DEX for ranged, spellcasting ability for spells
     * @param proficient   whether the attacker is proficient
     * @param rollType     NORMAL, ADVANTAGE, or DISADVANTAGE
     * @return             the roll outcome (CRITICAL_SUCCESS, SUCCESS, FAILURE, CRITICAL_FAILURE)
     */
    public static RollOutcome roll(LivingEntity attacker,
                                   LivingEntity target,
                                   AbilityScore abilityScore,
                                   boolean proficient,
                                   RollType rollType) {

        int abilityMod = resolveAbilityMod(attacker, abilityScore);
        int profBonus  = proficient ? resolveProficiency(attacker) : 0;
        int targetAc   = resolveAc(target);

        int roll1 = Dice.D20.roll(attacker.getRandom());
        int roll2 = rollType != RollType.NORMAL ? Dice.D20.roll(attacker.getRandom()) : -1;
        int used  = switch (rollType) {
            case ADVANTAGE    -> Math.max(roll1, roll2);
            case DISADVANTAGE -> Math.min(roll1, roll2);
            case NORMAL       -> roll1;
        };

        int total = used + abilityMod + profBonus;

        if (used == Dice.D20.getSides()) return RollOutcome.CRITICAL_SUCCESS;
        if (used == 1)                   return RollOutcome.CRITICAL_FAILURE;
        return total >= targetAc ? RollOutcome.SUCCESS : RollOutcome.FAILURE;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static int resolveAbilityMod(LivingEntity attacker, AbilityScore abilityScore) {
        if (attacker instanceof ServerPlayer player) {
            PlayerStats stats = StatsComponents.getStats(player);
            return stats != null ? stats.getModifier(abilityScore) : 0;
        }
        if (attacker instanceof MobCombatStatsHolder holder) {
            return holder.totality$getMobCombatStats().getModifier(abilityScore);
        }
        return 0;
    }

    private static int resolveProficiency(LivingEntity attacker) {
        if (attacker instanceof ServerPlayer player)
            return ProficiencyBonus.forPlayer(player);
        if (attacker instanceof MobCombatStatsHolder holder)
            return holder.totality$getMobCombatStats().getProficiencyBonus();
        return 2;
    }

    private static int resolveAc(LivingEntity target) {
        if (target instanceof ServerPlayer player) return ArmorClass.calculate(player);
        if (target instanceof MobCombatStatsHolder holder)
            return holder.totality$getMobCombatStats().getAC();
        return 10;
    }

}
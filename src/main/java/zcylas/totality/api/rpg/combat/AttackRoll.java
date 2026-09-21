package zcylas.totality.api.rpg.combat;

import net.minecraft.world.entity.LivingEntity;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.DiceBonus;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.mob.stats.MobCombatStatsHolder;
import zcylas.totality.api.rpg.classes.PlayerClassComponent;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatsComponents;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Server-side attack roll utility. No UI — rolls instantly and returns the outcome.
 * The dice screen (PendingDiceRollManager) is NOT used here — that's for
 * dialogue, quests, and narrative events only.
 *
 * Results are communicated via CombatRollNotification / CombatTextPayload.
 */
public final class AttackRoll {

    private AttackRoll() {}

    /**
     * The full retained attack-roll result — everything a presentation layer needs to describe the
     * roll without rerolling anything.
     *
     * @param roll1             the first natural d20 roll
     * @param roll2             the second natural d20 roll, or {@code -1} if {@code rollType} is
     *                          {@link RollType#NORMAL} (no second roll was made)
     * @param rollType          NORMAL, ADVANTAGE, or DISADVANTAGE
     * @param usedRoll          the natural roll actually used (picked from roll1/roll2 per
     *                          {@code rollType})
     * @param abilityScore      the ability score this roll used (STR for melee, DEX for ranged,
     *                          the spellcasting ability for spells)
     * @param abilityMod        the attacker's ability modifier applied to this roll
     * @param proficiencyBonus  the attacker's proficiency bonus applied to this roll (0 if not
     *                          proficient)
     * @param targetAc          the defending target's armor class at the moment of the roll
     * @param total             {@code usedRoll + abilityMod + proficiencyBonus + sum(bonuses)}
     * @param outcome           the resolved outcome
     * @param bonuses           labeled attack-roll bonuses that were applied (e.g. Bless d4)
     */
    public record Result(
            int roll1,
            int roll2,
            RollType rollType,
            int usedRoll,
            AbilityScore abilityScore,
            int abilityMod,
            int proficiencyBonus,
            int targetAc,
            int total,
            RollOutcome outcome,
            List<DiceBonus> bonuses
    ) {}

    /**
     * Rolls a weapon or spell attack for any living entity attacker.
     *
     * @param attacker     the attacking entity (player or mob)
     * @param target       the defending entity
     * @param abilityScore STR for melee, DEX for ranged, spellcasting ability for spells
     * @param proficient   whether the attacker is proficient
     * @param rollType     NORMAL, ADVANTAGE, or DISADVANTAGE
     * @return             outcome and any labeled attack-roll bonuses that were applied
     */
    public static Result roll(LivingEntity attacker,
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

        // Collect labeled bonuses (rolls any random dice exactly once)
        List<DiceBonus> atkBonuses = (attacker instanceof ServerPlayer sp)
                ? RollModifierRegistry.resolveAttackBonusList(sp, abilityScore)
                : List.of();
        int attackBonus = atkBonuses.stream().mapToInt(DiceBonus::value).sum();
        int total = used + abilityMod + profBonus + attackBonus;

        // Nat 20 = always a critical hit. Nat 1 = always a miss.
        RollOutcome outcome;
        if (used == Dice.D20.getSides())    outcome = RollOutcome.CRITICAL_SUCCESS;
        else if (used == 1)                 outcome = RollOutcome.CRITICAL_FAILURE;
        else outcome = total >= targetAc ? RollOutcome.SUCCESS : RollOutcome.FAILURE;

        return new Result(roll1, roll2, rollType, used, abilityScore, abilityMod, profBonus, targetAc, total, outcome, atkBonuses);
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
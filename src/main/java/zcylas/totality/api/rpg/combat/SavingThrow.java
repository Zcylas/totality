package zcylas.totality.api.rpg.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.DiceBonus;
import zcylas.totality.api.dice.DiceRollContext;
import zcylas.totality.api.dice.DiceRollResult;
import zcylas.totality.api.dice.PendingDiceRollManager;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatsComponents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Two paths:
 *
 * COMBAT — roll() resolves instantly server-side, no UI.
 *   RollOutcome outcome = SavingThrow.roll(target, AbilityScore.DEX, dc, RollType.NORMAL);
 *
 * NARRATIVE — request() opens the dice screen (dialogue, quests, events).
 *   SavingThrow.request(target, AbilityScore.WIS, dc, "Ancient Curse", RollType.NORMAL, result -> { ... });
 */
public final class SavingThrow {

    private SavingThrow() {}

    // ── Combat path (server-side, no UI) ─────────────────────────────────────

    /**
     * Rolls a saving throw instantly server-side.
     * Use this for all in-combat saving throws (spells, traps, hazards).
     *
     * @param target       the entity making the save (player or mob)
     * @param abilityScore the ability they save with (DEX, CON, WIS, etc.)
     * @param dc           the difficulty class to beat
     * @param rollType     NORMAL, ADVANTAGE, or DISADVANTAGE
     */
    public static RollOutcome roll(LivingEntity target,
                                   AbilityScore abilityScore,
                                   int dc,
                                   RollType rollType) {
        // Apply active roll modifiers (rage advantage, bless, etc.)
        if (target instanceof ServerPlayer sp) {
            rollType = RollModifierRegistry.resolveSave(sp, abilityScore, rollType);
        }
        int abilityMod = resolveAbilityMod(target, abilityScore);
        boolean proficient = isSaveProficient(target, abilityScore);
        int profBonus  = proficient ? resolveProficiency(target) : 0;

        int roll1 = Dice.D20.roll(target.getRandom());
        int roll2 = rollType != RollType.NORMAL ? Dice.D20.roll(target.getRandom()) : -1;
        int used  = switch (rollType) {
            case ADVANTAGE    -> Math.max(roll1, roll2);
            case DISADVANTAGE -> Math.min(roll1, roll2);
            case NORMAL       -> roll1;
        };

        int total = used + abilityMod + profBonus;

        if (used == Dice.D20.getSides()) return RollOutcome.CRITICAL_SUCCESS;
        if (used == 1)                   return RollOutcome.CRITICAL_FAILURE;
        return total >= dc ? RollOutcome.SUCCESS : RollOutcome.FAILURE;
    }

    /**
     * Convenience: computes DC from the caster and rolls immediately.
     */
    public static RollOutcome roll(LivingEntity target,
                                   AbilityScore abilityScore,
                                   LivingEntity caster,
                                   AbilityScore spellcastingAbility,
                                   RollType rollType) {
        int dc = spellSaveDc(caster, spellcastingAbility);
        return roll(target, abilityScore, dc, rollType);
    }

    // ── Narrative path (dice screen — dialogue, quests, events) ──────────────

    /**
     * Opens the dice screen on the target's client.
     * Use this for non-combat skill checks: dialogue, quests, random events.
     *
     * @param target       the player who must save (must be ServerPlayer — mobs can't open screens)
     * @param abilityScore the ability they save with
     * @param dc           the difficulty class
     * @param checkName    display name shown on the dice screen (e.g. "Ancient Curse")
     * @param rollType     NORMAL, ADVANTAGE, or DISADVANTAGE
     * @param callback     fired once the player clicks Roll
     */
    public static UUID request(ServerPlayer target,
                               AbilityScore abilityScore,
                               int dc,
                               String checkName,
                               RollType rollType,
                               Consumer<DiceRollResult> callback) {

        PlayerStats stats  = StatsComponents.getStats(target);
        int abilityMod     = stats != null ? stats.getModifier(abilityScore) : 0;
        boolean proficient = isSaveProficient(target, abilityScore);
        int profBonus      = proficient ? ProficiencyBonus.forPlayer(target) : 0;

        List<DiceBonus> bonuses = new ArrayList<>();
        bonuses.add(new DiceBonus(abilityScore.name() + " Modifier", abilityMod));
        if (proficient) bonuses.add(new DiceBonus("Save Proficiency", profBonus));

        DiceRollContext context = new DiceRollContext(
                checkName,
                abilityScore.name() + " Saving Throw",
                Dice.D20,
                dc,
                rollType,
                bonuses
        );

        return PendingDiceRollManager.request(target, context, callback);
    }

    /**
     * Narrative convenience overload — computes DC from a player caster.
     */
    public static UUID request(ServerPlayer target,
                               AbilityScore abilityScore,
                               ServerPlayer caster,
                               AbilityScore spellcastingAbility,
                               String checkName,
                               RollType rollType,
                               Consumer<DiceRollResult> callback) {
        int dc = spellSaveDc(caster, spellcastingAbility);
        return request(target, abilityScore, dc, checkName, rollType, callback);
    }

    // ── Shared utilities ──────────────────────────────────────────────────────

    /**
     * Computes a caster's Spell Save DC.
     * Formula: 8 + proficiency + spellcasting ability modifier.
     * Works for both players and mobs.
     */
    public static int spellSaveDc(LivingEntity caster, AbilityScore spellcastingAbility) {
        int spellMod  = resolveAbilityMod(caster, spellcastingAbility);
        int profBonus = resolveProficiency(caster);
        return 8 + profBonus + spellMod;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static int resolveAbilityMod(LivingEntity entity, AbilityScore abilityScore) {
        if (entity instanceof ServerPlayer p) {
            PlayerStats stats = StatsComponents.getStats(p);
            return stats != null ? stats.getModifier(abilityScore) : 0;
        }
        // TODO: mob stat component
        return 0;
    }

    private static int resolveProficiency(LivingEntity entity) {
        if (entity instanceof ServerPlayer p) return ProficiencyBonus.forPlayer(p);
        // TODO: mob stat component
        return 2;
    }



    /**
     * Whether the entity is proficient in the given saving throw.
     * TODO: query PlayerClass.get(player).hasSaveProficiency(abilityScore)
     */
    private static boolean isSaveProficient(LivingEntity entity, AbilityScore abilityScore) {
        if (entity instanceof ServerPlayer player) {
            return ClassComponents.get(player).getSaveProficiencies().contains(abilityScore);
        }
        return false;
    }
}
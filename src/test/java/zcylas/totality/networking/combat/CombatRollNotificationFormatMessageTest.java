package zcylas.totality.networking.combat;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.DiceBonus;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.combat.AttackRoll;
import zcylas.totality.api.rpg.combat.DamageBonus;
import zcylas.totality.api.rpg.combat.DamageRollResult;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Behavioral tests for {@link CombatRollNotification#formatMessage}, the pure formatter extracted
 * from {@link CombatRollNotification#send} so notification text can be verified without a live
 * {@code ServerPlayer}/networking stack. These execute for real — they are not sentinels.
 */
class CombatRollNotificationFormatMessageTest {

    @Test
    void normalHitMatchesThePreferredFormatExample() {
        AttackRoll.Result attack = new AttackRoll.Result(
                12, -1, RollType.NORMAL, 12,
                AbilityScore.DEX, 3, 2, 15, 20,
                RollOutcome.SUCCESS, List.of(new DiceBonus("Bless", 3)));
        DamageRollResult damage = new DamageRollResult(1, Dice.D6, List.of(6), 5, 11);

        String message = CombatRollNotification.formatMessage(
                "Iron Sword", attack, damage, AbilityScore.STR, List.of(new DamageBonus(2, "Rage")));

        assertEquals(
                "Iron Sword — HIT\n"
                        + "ATK [12] +3 DEX +2 PROF +3 Bless = 20 vs AC 15\n"
                        + "DMG [6] +3 STR +2 Rage = 11 (55)",
                message);
    }

    @Test
    void missOmitsTheDamageLineEntirely() {
        AttackRoll.Result attack = new AttackRoll.Result(
                7, -1, RollType.NORMAL, 7,
                AbilityScore.DEX, 3, 2, 15, 13,
                RollOutcome.FAILURE, List.of(new DiceBonus("Bless", 1)));

        String message = CombatRollNotification.formatMessage("Iron Sword", attack, null, null, List.of());

        assertEquals(
                "Iron Sword — MISS\n"
                        + "ATK [7] +3 DEX +2 PROF +1 Bless = 13 vs AC 15",
                message);
        assertEquals(2, message.lines().count(), "a miss must produce exactly two semantic lines, no DMG line");
    }

    @Test
    void criticalHitUsesTheCriticalHitLabelAndDoublesDamageDice() {
        AttackRoll.Result attack = new AttackRoll.Result(
                20, -1, RollType.NORMAL, 20,
                AbilityScore.DEX, 3, 2, 15, 27,
                RollOutcome.CRITICAL_SUCCESS, List.of(new DiceBonus("Bless", 2)));
        DamageRollResult damage = new DamageRollResult(2, Dice.D6, List.of(4, 6), 5, 15);

        String message = CombatRollNotification.formatMessage(
                "Iron Sword", attack, damage, AbilityScore.STR, List.of(new DamageBonus(2, "Rage")));

        assertEquals(
                "Iron Sword — CRITICAL HIT\n"
                        + "ATK [20] +3 DEX +2 PROF +2 Bless = 27 vs AC 15\n"
                        + "DMG [4, 6] +3 STR +2 Rage = 15 (75)",
                message);
    }

    @Test
    void naturalOneIsAMissLabelNotACriticalMissLabel() {
        AttackRoll.Result attack = new AttackRoll.Result(
                1, -1, RollType.NORMAL, 1,
                AbilityScore.STR, 3, 2, 15, 6,
                RollOutcome.CRITICAL_FAILURE, List.of());

        String message = CombatRollNotification.formatMessage("Iron Sword", attack, null, null, List.of());

        assertEquals("Iron Sword — MISS\nATK [1] +3 STR +2 PROF = 6 vs AC 15", message);
    }

    @Test
    void advantageUsesTheCompactRollNotationWithoutRerolling() {
        AttackRoll.Result attack = new AttackRoll.Result(
                8, 16, RollType.ADVANTAGE, 16,
                AbilityScore.STR, 3, 2, 15, 21,
                RollOutcome.SUCCESS, List.of());

        String message = CombatRollNotification.formatMessage("Iron Sword", attack, null, null, List.of());

        assertEquals("Iron Sword — HIT\nATK [8, 16 → 16] +3 STR +2 PROF = 21 vs AC 15", message);
    }

    @Test
    void disadvantageUsesTheCompactRollNotationWithoutRerolling() {
        AttackRoll.Result attack = new AttackRoll.Result(
                14, 5, RollType.DISADVANTAGE, 5,
                AbilityScore.STR, 3, 2, 15, 10,
                RollOutcome.FAILURE, List.of());

        String message = CombatRollNotification.formatMessage("Iron Sword", attack, null, null, List.of());

        assertEquals("Iron Sword — MISS\nATK [14, 5 → 5] +3 STR +2 PROF = 10 vs AC 15", message);
    }

    @Test
    void zeroAbilityModAndZeroProficiencyOmitTheirSegments() {
        AttackRoll.Result attack = new AttackRoll.Result(
                12, -1, RollType.NORMAL, 12,
                AbilityScore.STR, 0, 0, 10, 12,
                RollOutcome.SUCCESS, List.of());

        String message = CombatRollNotification.formatMessage("Unarmed Strike", attack, null, null, List.of());

        assertEquals("Unarmed Strike — HIT\nATK [12] = 12 vs AC 10", message);
    }

    @Test
    void zeroDamageAbilityContributionOmitsItsSegmentButKeepsNamedBonuses() {
        AttackRoll.Result attack = new AttackRoll.Result(
                12, -1, RollType.NORMAL, 12,
                AbilityScore.INT, 0, 2, 10, 14,
                RollOutcome.SUCCESS, List.of());
        // damage modifier is entirely from the Rage bonus, no separate ability contribution
        DamageRollResult damage = new DamageRollResult(1, Dice.D6, List.of(4), 2, 6);

        String message = CombatRollNotification.formatMessage("Fire Bolt", attack, damage, AbilityScore.INT, List.of(new DamageBonus(2, "Rage")));

        assertEquals("Fire Bolt — HIT\nATK [12] +2 PROF = 14 vs AC 10\nDMG [4] +2 Rage = 6 (30)", message);
    }

    @Test
    void namedEffectsRetainAuthoredDisplayCaseWhileStructuralLabelsAreUppercase() {
        AttackRoll.Result attack = new AttackRoll.Result(
                12, -1, RollType.NORMAL, 12,
                AbilityScore.DEX, 3, 2, 15, 20,
                RollOutcome.SUCCESS, List.of(new DiceBonus("Bless", 3), new DiceBonus("Divine Favor", 2)));

        String message = CombatRollNotification.formatMessage("Iron Sword", attack, null, null, List.of());

        assertEquals("Iron Sword — HIT\nATK [12] +3 DEX +2 PROF +3 Bless +2 Divine Favor = 20 vs AC 15", message);
        // structural labels present and uppercase; named effects preserved verbatim
        assertEquals(true, message.contains("PROF"));
        assertEquals(true, message.contains("Bless"));
        assertEquals(true, message.contains("Divine Favor"));
        assertEquals(false, message.contains("BLESS"));
        assertEquals(false, message.contains("prof"));
    }
}

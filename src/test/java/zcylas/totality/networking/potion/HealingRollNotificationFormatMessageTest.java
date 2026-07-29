package zcylas.totality.networking.potion;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.item.potion.dnd.HealingRollResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Behavioral tests for {@link HealingRollNotification#formatMessage}, the pure formatter extracted
 * from {@link HealingRollNotification#send} so notification text can be verified without a live
 * {@code ServerPlayer}/networking stack (see that method's own Javadoc). These execute for real —
 * they are not source sentinels.
 */
class HealingRollNotificationFormatMessageTest {

    @Test
    void partiallyClampedRollMatchesThePreferredFormatExample() {
        // Spec example: 2d4 rolled [2, 4], +2 modifier, total 8 (40 display HP), but only 6
        // vanilla HP (30 display) was actually missing/restored.
        HealingRollResult roll = new HealingRollResult(2, Dice.D4, List.of(2, 4), 2, 8);
        String message = HealingRollNotification.formatMessage("Potion of Healing", roll, 6.0f);

        assertEquals("Potion of Healing — 2d4 → [2, 4]\n+2 = 8 (40 HP)  •  Restored 30 HP", message);
    }

    @Test
    void fullyAppliedRollShowsMatchingRolledAndRestoredValues() {
        HealingRollResult roll = new HealingRollResult(2, Dice.D4, List.of(2, 4), 2, 8);
        String message = HealingRollNotification.formatMessage("Potion of Healing", roll, 8.0f);

        assertEquals("Potion of Healing — 2d4 → [2, 4]\n+2 = 8 (40 HP)  •  Restored 40 HP", message);
    }

    @Test
    void zeroModifierOmitsTheAwkwardStandaloneModifierPrefix() {
        HealingRollResult roll = new HealingRollResult(2, Dice.D4, List.of(3, 3), 0, 6);
        String message = HealingRollNotification.formatMessage("Potion of Healing", roll, 6.0f);

        assertEquals("Potion of Healing — 2d4 → [3, 3]\n6 (30 HP)  •  Restored 30 HP", message);
    }

    @Test
    void negativeModifierIsRenderedWithoutADoublePlusSign() {
        // HealingAmount itself rejects a negative bonus at construction, but the formatter is a
        // separate, independently-testable unit — it must still render sensibly if ever handed one.
        HealingRollResult roll = new HealingRollResult(2, Dice.D4, List.of(4, 4), -1, 7);
        String message = HealingRollNotification.formatMessage("Potion of Healing", roll, 5.0f);

        assertEquals("Potion of Healing — 2d4 → [4, 4]\n-1 = 7 (35 HP)  •  Restored 25 HP", message);
    }

    @Test
    void fixedHealingFormatOmitsTheDiceBreakdownEntirely() {
        HealingRollResult roll = new HealingRollResult(0, null, List.of(), 5, 5);
        String message = HealingRollNotification.formatMessage("Potion of Healing", roll, 5.0f);

        assertEquals("Potion of Healing — 5 (25 HP)\nRestored 25 HP", message);
    }

    @Test
    void labelIsTakenVerbatimFromTheSuppliedDisplayName() {
        HealingRollResult roll = new HealingRollResult(0, null, List.of(), 5, 5);
        String message = HealingRollNotification.formatMessage("Renamed Healing Draught", roll, 5.0f);

        assertEquals("Renamed Healing Draught — 5 (25 HP)\nRestored 25 HP", message);
    }

    @Test
    void greaterHealingFormulaExpressionAppearsCorrectlyInTheMessage() {
        HealingRollResult roll = new HealingRollResult(4, Dice.D4, List.of(1, 2, 3, 4), 4, 14);
        String message = HealingRollNotification.formatMessage("Potion of Greater Healing", roll, 14.0f);

        assertEquals("Potion of Greater Healing — 4d4 → [1, 2, 3, 4]\n+4 = 14 (70 HP)  •  Restored 70 HP", message);
    }
}

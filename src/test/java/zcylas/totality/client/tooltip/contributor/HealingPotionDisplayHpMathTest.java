package zcylas.totality.client.tooltip.contributor;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.core.rpgutils.RpgDisplayUtils;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.item.potion.dnd.HealingAmount;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure arithmetic test for the exact figures {@code HealingPotionContributor} shows for the
 * registered "2d4 + 2" formula, confirming presentation-scale HP goes through
 * {@link RpgDisplayUtils#toDisplayHp} (the mod's one authoritative Health conversion) rather than
 * a second hardcoded multiplier. No {@code ItemStack} is involved — this only exercises the same
 * {@link HealingAmount.DiceHealing} record and conversion utility the contributor reads.
 */
class HealingPotionDisplayHpMathTest {

    @Test
    void normalPotionOfHealingMinAndMaxConvertToTheExpectedDisplayHp() {
        HealingAmount amount = HealingAmount.dice(2, Dice.D4, 2);
        assertInstanceOf(HealingAmount.DiceHealing.class, amount);
        HealingAmount.DiceHealing dice = (HealingAmount.DiceHealing) amount;

        int minVanilla = dice.diceCount() * 1 + dice.bonus();
        int maxVanilla = dice.diceCount() * dice.die().getSides() + dice.bonus();
        assertEquals(4, minVanilla);
        assertEquals(10, maxVanilla);

        assertEquals(20, RpgDisplayUtils.toDisplayHp(minVanilla));
        assertEquals(50, RpgDisplayUtils.toDisplayHp(maxVanilla));
    }

    @Test
    void diceLabelMatchesTheAuthoredFormulaNotation() {
        HealingAmount.DiceHealing dice = new HealingAmount.DiceHealing(2, Dice.D4, 2);
        String formula = dice.diceCount() + dice.die().getLabel() + (dice.bonus() > 0 ? " + " + dice.bonus() : "");
        assertEquals("2d4 + 2", formula);
    }
}

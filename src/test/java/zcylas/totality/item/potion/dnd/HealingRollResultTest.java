package zcylas.totality.item.potion.dnd;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.dice.Dice;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure tests for {@link HealingRollResult} — no Minecraft registry/bootstrap involved. */
class HealingRollResultTest {

    @Test
    void diceBasedResultReportsIsDiceBasedTrue() {
        HealingRollResult result = new HealingRollResult(2, Dice.D4, List.of(2, 4), 2, 8);
        assertTrue(result.isDiceBased());
    }

    @Test
    void fixedResultReportsIsDiceBasedFalse() {
        HealingRollResult result = new HealingRollResult(0, null, List.of(), 5, 5);
        assertFalse(result.isDiceBased());
    }

    @Test
    void diceExpressionMatchesConfiguredCountAndDie() {
        HealingRollResult result = new HealingRollResult(2, Dice.D4, List.of(2, 4), 2, 8);
        assertEquals("2d4", result.diceExpression());
    }

    @Test
    void diceExpressionIsEmptyForAFixedResult() {
        HealingRollResult result = new HealingRollResult(0, null, List.of(), 5, 5);
        assertEquals("", result.diceExpression());
    }

    @Test
    void rollsListIsImmutable() {
        List<Integer> mutable = new ArrayList<>(List.of(2, 4));
        HealingRollResult result = new HealingRollResult(2, Dice.D4, mutable, 2, 8);
        assertThrows(UnsupportedOperationException.class, () -> result.rolls().add(1));
    }

    @Test
    void rollsListDefensivelyCopiesTheConstructorArgument() {
        List<Integer> mutable = new ArrayList<>(List.of(2, 4));
        HealingRollResult result = new HealingRollResult(2, Dice.D4, mutable, 2, 8);
        mutable.add(99);
        assertEquals(List.of(2, 4), result.rolls(), "later mutation of the constructor argument must not leak into the retained result");
    }

    @Test
    void constructorRejectsNullRolls() {
        assertThrows(NullPointerException.class, () -> new HealingRollResult(2, Dice.D4, null, 2, 8));
    }
}

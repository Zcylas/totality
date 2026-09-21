package zcylas.totality.item.potion.dnd;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.dice.Dice;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for {@link HealingAmount} — no Minecraft registry/bootstrap involved, since this
 * type only touches {@code Dice} (an independent enum) and {@code RandomSource} (a standalone
 * PRNG), neither of which require {@code Bootstrap.bootStrap()}.
 */
class HealingAmountTest {

    // ── Fixed ─────────────────────────────────────────────────────────────────

    @Test
    void fixedAlwaysReturnsTheConfiguredAmountRegardlessOfSeed() {
        HealingAmount amount = HealingAmount.fixed(7);
        for (long seed = 0; seed < 20; seed++) {
            assertEquals(7, amount.roll(RandomSource.create(seed)));
        }
    }

    @Test
    void fixedRejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> HealingAmount.fixed(-1));
    }

    @Test
    void fixedAllowsZero() {
        assertEquals(0, HealingAmount.fixed(0).roll(RandomSource.create(1L)));
    }

    // ── Dice validation ───────────────────────────────────────────────────────

    @Test
    void diceRejectsZeroCount() {
        assertThrows(IllegalArgumentException.class, () -> HealingAmount.dice(0, Dice.D4, 2));
    }

    @Test
    void diceRejectsNegativeCount() {
        assertThrows(IllegalArgumentException.class, () -> HealingAmount.dice(-2, Dice.D4, 2));
    }

    @Test
    void diceRejectsNullDie() {
        assertThrows(NullPointerException.class, () -> HealingAmount.dice(2, null, 2));
    }

    @Test
    void diceRejectsNegativeBonus() {
        // A negative bonus would let a badly-authored formula silently clamp toward zero instead
        // of failing fast at registration time — reject it outright rather than allow that.
        assertThrows(IllegalArgumentException.class, () -> HealingAmount.dice(2, Dice.D4, -1));
    }

    @Test
    void diceAllowsZeroBonus() {
        assertDoesNotThrow(() -> HealingAmount.dice(2, Dice.D4, 0));
    }

    // ── Normal Potion of Healing: 2d4 + 2 ─────────────────────────────────────

    @Test
    void twoD4PlusTwoIsNeverBelowFourAcrossManySeeds() {
        HealingAmount amount = HealingAmount.dice(2, Dice.D4, 2);
        for (long seed = 0; seed < 500; seed++) {
            int rolled = amount.roll(RandomSource.create(seed));
            assertTrue(rolled >= 4, "roll " + rolled + " (seed " + seed + ") below the 2d4+2 minimum of 4");
        }
    }

    @Test
    void twoD4PlusTwoIsNeverAboveTenAcrossManySeeds() {
        HealingAmount amount = HealingAmount.dice(2, Dice.D4, 2);
        for (long seed = 0; seed < 500; seed++) {
            int rolled = amount.roll(RandomSource.create(seed));
            assertTrue(rolled <= 10, "roll " + rolled + " (seed " + seed + ") above the 2d4+2 maximum of 10");
        }
    }

    @Test
    void twoD4PlusTwoProducesARealSpreadOfValuesNotAConstant() {
        // Proves this is a genuine roll (multiple distinct outcomes), not a formula that always
        // returns the same number. Bound checks above already cover the [4, 10] range itself;
        // this deliberately avoids asserting on any single exact seed hitting the extremes, since
        // that is a probabilistic search and would make the test seed-distribution-dependent.
        HealingAmount amount = HealingAmount.dice(2, Dice.D4, 2);
        java.util.Set<Integer> distinctValues = new java.util.HashSet<>();
        for (long seed = 0; seed < 500; seed++) {
            distinctValues.add(amount.roll(RandomSource.create(seed)));
        }
        assertTrue(distinctValues.size() >= 5,
                "expected at least 5 distinct 2d4+2 outcomes across 500 seeds, got: " + distinctValues);
    }

    @Test
    void twoD4PlusTwoMatchesAnIndependentlyComputedSeededRoll() {
        // Demonstrates the calculation itself (not just the bound), by reproducing it by hand
        // from the same die primitive (Dice.D4.roll) against a freshly-seeded RandomSource.
        long seed = 12345L;
        HealingAmount amount = HealingAmount.dice(2, Dice.D4, 2);
        int actual = amount.roll(RandomSource.create(seed));

        RandomSource expectedRandom = RandomSource.create(seed);
        int expected = Dice.D4.roll(expectedRandom) + Dice.D4.roll(expectedRandom) + 2;

        assertEquals(expected, actual);
    }

    // ── Reusability proof: same base expresses the other D&D healing-potion tiers ────

    @Test
    void greaterHealingFormulaFourD4PlusFourRangesEightToTwenty() {
        HealingAmount amount = HealingAmount.dice(4, Dice.D4, 4);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (long seed = 0; seed < 500; seed++) {
            int rolled = amount.roll(RandomSource.create(seed));
            min = Math.min(min, rolled);
            max = Math.max(max, rolled);
        }
        assertTrue(min >= 8, "Greater Healing (4d4+4) rolled below its minimum of 8: " + min);
        assertTrue(max <= 20, "Greater Healing (4d4+4) rolled above its maximum of 20: " + max);
    }

    @Test
    void superiorHealingFormulaEightD4PlusEightRangesSixteenToForty() {
        HealingAmount amount = HealingAmount.dice(8, Dice.D4, 8);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (long seed = 0; seed < 500; seed++) {
            int rolled = amount.roll(RandomSource.create(seed));
            min = Math.min(min, rolled);
            max = Math.max(max, rolled);
        }
        assertTrue(min >= 16, "Superior Healing (8d4+8) rolled below its minimum of 16: " + min);
        assertTrue(max <= 40, "Superior Healing (8d4+8) rolled above its maximum of 40: " + max);
    }

    @Test
    void supremeHealingFormulaTenD4PlusTwentyRangesThirtyToSixty() {
        HealingAmount amount = HealingAmount.dice(10, Dice.D4, 20);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (long seed = 0; seed < 500; seed++) {
            int rolled = amount.roll(RandomSource.create(seed));
            min = Math.min(min, rolled);
            max = Math.max(max, rolled);
        }
        assertTrue(min >= 30, "Supreme Healing (10d4+20) rolled below its minimum of 30: " + min);
        assertTrue(max <= 60, "Supreme Healing (10d4+20) rolled above its maximum of 60: " + max);
    }

    // ── rollDetailed: retained roll result ────────────────────────────────────

    @Test
    void rollDetailedPreservesIndividualDiceInOriginalRollOrder() {
        long seed = 777L;
        HealingAmount amount = HealingAmount.dice(2, Dice.D4, 2);
        HealingRollResult result = amount.rollDetailed(RandomSource.create(seed));

        RandomSource expectedRandom = RandomSource.create(seed);
        int expectedFirst = Dice.D4.roll(expectedRandom);
        int expectedSecond = Dice.D4.roll(expectedRandom);

        assertEquals(java.util.List.of(expectedFirst, expectedSecond), result.rolls());
    }

    @Test
    void rollDetailedTotalIsSumOfRetainedRollsPlusModifier() {
        HealingAmount amount = HealingAmount.dice(2, Dice.D4, 2);
        for (long seed = 0; seed < 200; seed++) {
            HealingRollResult result = amount.rollDetailed(RandomSource.create(seed));
            int sum = result.rolls().stream().mapToInt(Integer::intValue).sum();
            assertEquals(sum + result.modifier(), result.total(),
                    "seed " + seed + ": total must equal sum(rolls) + modifier, calculated once from the retained rolls");
        }
    }

    @Test
    void rollDetailedModifierMatchesConfiguredBonusExactlyOnce() {
        HealingAmount amount = HealingAmount.dice(2, Dice.D4, 2);
        HealingRollResult result = amount.rollDetailed(RandomSource.create(42L));
        assertEquals(2, result.modifier());
    }

    @Test
    void rollDelegatesToRollDetailedRatherThanRollingASecondTime() {
        // If roll(random) drew from the RandomSource independently of rollDetailed(random) (e.g.
        // rolling twice), a fresh same-seed comparison against the hand-computed first-N-draws
        // value below would very likely diverge. Matching confirms roll() consumes exactly the
        // same single sequence of draws as rollDetailed().
        long seed = 9001L;
        HealingAmount amount = HealingAmount.dice(2, Dice.D4, 2);

        int viaRoll = amount.roll(RandomSource.create(seed));
        int viaRollDetailed = amount.rollDetailed(RandomSource.create(seed)).total();

        assertEquals(viaRollDetailed, viaRoll);
    }

    @Test
    void differentConfiguredFormulasProduceMatchingDiceExpressions() {
        assertEquals("2d4", HealingAmount.dice(2, Dice.D4, 2).rollDetailed(RandomSource.create(1L)).diceExpression());
        assertEquals("4d4", HealingAmount.dice(4, Dice.D4, 4).rollDetailed(RandomSource.create(1L)).diceExpression());
        assertEquals("8d4", HealingAmount.dice(8, Dice.D4, 8).rollDetailed(RandomSource.create(1L)).diceExpression());
        assertEquals("10d4", HealingAmount.dice(10, Dice.D4, 20).rollDetailed(RandomSource.create(1L)).diceExpression());
    }

    @Test
    void fixedRollDetailedHasNoRollsAndModifierEqualsAmount() {
        HealingRollResult result = HealingAmount.fixed(7).rollDetailed(RandomSource.create(1L));
        assertTrue(result.rolls().isEmpty());
        assertEquals(7, result.modifier());
        assertEquals(7, result.total());
        assertFalse(result.isDiceBased());
    }

    @Test
    void diceRollDetailedReportsIsDiceBasedTrue() {
        HealingRollResult result = HealingAmount.dice(2, Dice.D4, 2).rollDetailed(RandomSource.create(1L));
        assertTrue(result.isDiceBased());
    }
}

package zcylas.totality.item.potion.dnd;

import net.minecraft.util.RandomSource;
import zcylas.totality.api.dice.Dice;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A healing-value formula: either a flat fixed amount or count×die + flat bonus (e.g. 2d4 + 2).
 * Independent of Totality's Alchemy API — used by {@link HealingPotionItem}.
 */
public sealed interface HealingAmount {

    /**
     * Roll (or return, for a fixed amount) the healing value for this formula. Never negative.
     * Delegates to {@link #rollDetailed(RandomSource)} rather than drawing from {@code random} a
     * second time, so calling both on the same formula never rolls twice.
     */
    default int roll(RandomSource random) {
        return rollDetailed(random).total();
    }

    /**
     * Rolls this formula exactly once and retains the individual die results (in roll order), the
     * configured modifier, and the calculated total — the single source of truth for both applying
     * healing and formatting a notification about it.
     */
    HealingRollResult rollDetailed(RandomSource random);

    static HealingAmount fixed(int amount) {
        return new Fixed(amount);
    }

    static HealingAmount dice(int diceCount, Dice die, int bonus) {
        return new DiceHealing(diceCount, die, bonus);
    }

    record Fixed(int amount) implements HealingAmount {
        public Fixed {
            if (amount < 0) throw new IllegalArgumentException("amount must be >= 0: " + amount);
        }

        @Override
        public HealingRollResult rollDetailed(RandomSource random) {
            return new HealingRollResult(0, null, List.of(), amount, amount);
        }
    }

    record DiceHealing(int diceCount, Dice die, int bonus) implements HealingAmount {
        public DiceHealing {
            if (diceCount <= 0) throw new IllegalArgumentException("diceCount must be >= 1: " + diceCount);
            Objects.requireNonNull(die, "die");
            if (bonus < 0) throw new IllegalArgumentException("bonus must be >= 0: " + bonus);
        }

        @Override
        public HealingRollResult rollDetailed(RandomSource random) {
            List<Integer> rolls = new ArrayList<>(diceCount);
            int sum = 0;
            for (int i = 0; i < diceCount; i++) {
                int result = die.roll(random);
                rolls.add(result);
                sum += result;
            }
            int total = Math.max(0, sum + bonus);
            return new HealingRollResult(diceCount, die, rolls, bonus, total);
        }
    }
}

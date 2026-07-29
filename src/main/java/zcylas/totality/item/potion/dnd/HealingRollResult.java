package zcylas.totality.item.potion.dnd;

import org.jspecify.annotations.Nullable;
import zcylas.totality.api.dice.Dice;

import java.util.List;
import java.util.Objects;

/**
 * The retained result of a single {@link HealingAmount} roll — individual die results (in roll
 * order), the configured flat modifier, and the calculated total. Independent of Totality's
 * Alchemy API, player classes, networking, and HUD classes; a plain immutable data holder reused
 * by both the healing application path ({@link HealingPotionItem}) and the optional player-facing
 * notification formatter, so the roll is never performed twice.
 *
 * <p>Supports the non-dice {@link HealingAmount.Fixed} form: {@code die} is {@code null} and
 * {@code diceCount} is {@code 0} in that case, {@code rolls} is empty, and {@code modifier} carries
 * the fixed amount itself (see {@link #isDiceBased()}).
 */
public record HealingRollResult(
        int diceCount,
        @Nullable Dice die,
        List<Integer> rolls,
        int modifier,
        int total
) {
    public HealingRollResult {
        Objects.requireNonNull(rolls, "rolls");
        rolls = List.copyOf(rolls);
    }

    /** {@code true} for a dice-based formula (e.g. 2d4 + 2); {@code false} for a fixed amount. */
    public boolean isDiceBased() {
        return die != null && diceCount > 0;
    }

    /** e.g. {@code "2d4"}. Only meaningful when {@link #isDiceBased()}. */
    public String diceExpression() {
        return isDiceBased() ? diceCount + die.name().toLowerCase() : "";
    }
}

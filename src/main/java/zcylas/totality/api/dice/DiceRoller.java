// api/dice/DiceRoller.java
package zcylas.totality.api.dice;

import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side utility for rolling dice and producing DiceRollResults.
 * Always rolls on the server for fairness.
 */
public final class DiceRoller {

    private DiceRoller() {}

    /**
     * Roll the dice described by the context and produce a full result.
     * Handles NORMAL / ADVANTAGE / DISADVANTAGE and determines outcome.
     */
    public static DiceRollResult roll(ServerPlayer player, DiceRollContext ctx) {
        var random = player.getRandom();

        int roll1 = ctx.dice().roll(random);
        int roll2 = ctx.rollType() != RollType.NORMAL ? ctx.dice().roll(random) : -1;

        int usedRoll = switch (ctx.rollType()) {
            case NORMAL       -> roll1;
            case ADVANTAGE    -> Math.max(roll1, roll2);
            case DISADVANTAGE -> Math.min(roll1, roll2);
        };

        int totalBonus = ctx.totalBonus();
        int total      = usedRoll + totalBonus;

        RollOutcome outcome = determineOutcome(usedRoll, total, ctx.dc(), ctx.dice());

        return new DiceRollResult(ctx, roll1, roll2, usedRoll, totalBonus, total, outcome);
    }

    private static RollOutcome determineOutcome(int natural, int total, int dc, Dice dice) {
        // Natural max is always a critical success
        if (natural == dice.getSides()) return RollOutcome.CRITICAL_SUCCESS;
        // Natural 1 is always a critical failure (only meaningful on D20)
        if (natural == 1 && dice == Dice.D20) return RollOutcome.CRITICAL_FAILURE;
        return total >= dc ? RollOutcome.SUCCESS : RollOutcome.FAILURE;
    }
}
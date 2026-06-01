package zcylas.totality.api.rpg.combat;

import zcylas.totality.api.dice.Dice;

import java.util.List;

/**
 * The result of a multi-die damage roll.
 * Holds individual die results for the HUD breakdown display.
 *
 * e.g. 8d6 → [4, 2, 6, 1, 5, 3, 2, 1] = 24 (+3 modifier) = 27
 */
public record DamageRollResult(
        int        count,
        Dice       dice,
        List<Integer> rolls,
        int        modifier,
        int        total
) {
    /**
     * Human-readable breakdown string for the HUD notification.
     * e.g. "8d6 → [4, 2, 6, 1, 5, 3, 2, 1] = 27"
     */
    public String breakdown() {
        StringBuilder sb = new StringBuilder();
        sb.append(count).append(dice.name().toLowerCase()).append(" → ");
        sb.append(rolls);
        if (modifier != 0) {
            sb.append(modifier > 0 ? " +" : " ").append(modifier);
        }
        sb.append(" = ").append(total);
        return sb.toString();
    }

    public String diceBreakdown() {
        StringBuilder sb = new StringBuilder();
        sb.append(count).append(dice.name().toLowerCase()).append(" → ");
        sb.append(rolls);
        if (modifier != 0) sb.append(modifier > 0 ? " +" : " ").append(modifier);
        return sb.toString();
    }
}
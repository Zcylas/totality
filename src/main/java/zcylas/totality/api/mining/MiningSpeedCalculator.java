package zcylas.totality.api.mining;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mining Speed for a PROFILED conventional tool: authored base + additive Efficiency, then
 * multiplicative Haste, then the ordinary-mining speed cap (§6-8 of the V2 balance pass). Pure and
 * player-independent so the tooltip can reuse the exact numbers gameplay uses; the live cadence
 * path additionally folds in an environmental multiplier (Fatigue/water/airborne/BLOCK_BREAK_SPEED)
 * that the tooltip intentionally omits (see {@link PlayerMiningPower}).
 *
 * <p>Efficiency/Haste are read directly (enchant level / effect amplifier) and never through
 * {@code Player.getDestroySpeed}, so vanilla's own Efficiency/Haste math can never be double-applied.
 */
public final class MiningSpeedCalculator {

    private MiningSpeedCalculator() {}

    /** Efficiency I..V additive Mining Speed bonus; index 0 (no enchant) is 0. */
    private static final float[] EFFICIENCY_BONUS = {0f, 0.5f, 1.0f, 2.0f, 3.5f, 5.0f};

    public static float efficiencyBonus(int level) {
        int i = Math.max(0, Math.min(EFFICIENCY_BONUS.length - 1, level));
        return EFFICIENCY_BONUS[i];
    }

    /** {@code hasteLevel} is the effect amplifier + 1 (Haste I -> 1, Haste II -> 2), matching vanilla's own formula. */
    public static float hasteMultiplier(int hasteLevel) {
        return hasteLevel <= 0 ? 1f : 1f + 0.20f * hasteLevel;
    }

    /**
     * @param hasteLevel               null/<=0 when no active Haste contribution should be shown (no player
     *                                 context, or Haste isn't active) — never fabricated.
     * @param environmentalMultiplier  Fatigue/water/airborne/BLOCK_BREAK_SPEED combined; 1f (omitted from the
     *                                 breakdown) for a player-less tooltip evaluation.
     */
    public static StatBreakdown compute(ResolvedMiningSource source, int efficiencyLevel,
                                        @Nullable Integer hasteLevel, float environmentalMultiplier) {
        List<StatBreakdown.Contribution> contributions = new ArrayList<>();
        contributions.add(new StatBreakdown.Contribution("Base Tool", StatBreakdown.Op.BASE, source.miningSpeed()));
        float value = source.miningSpeed();

        if (efficiencyLevel > 0) {
            float bonus = efficiencyBonus(efficiencyLevel);
            contributions.add(new StatBreakdown.Contribution("Efficiency " + RomanNumeral.of(efficiencyLevel), StatBreakdown.Op.ADD, bonus));
            value += bonus;
        }
        if (hasteLevel != null && hasteLevel > 0) {
            float mult = hasteMultiplier(hasteLevel);
            contributions.add(new StatBreakdown.Contribution("Haste " + RomanNumeral.of(hasteLevel), StatBreakdown.Op.MULTIPLY, mult));
            value *= mult;
        }
        if (environmentalMultiplier != 1f) {
            value *= environmentalMultiplier;
        }

        float capped = Math.min(value, MiningTuning.SPEED_CAP);
        if (capped < value) {
            contributions.add(new StatBreakdown.Contribution("Speed Cap", StatBreakdown.Op.MIN, MiningTuning.SPEED_CAP));
        }
        return new StatBreakdown(capped, contributions);
    }
}

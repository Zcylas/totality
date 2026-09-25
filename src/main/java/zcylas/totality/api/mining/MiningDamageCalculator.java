package zcylas.totality.api.mining;

import java.util.ArrayList;
import java.util.List;

/**
 * Normal (non-Power) Mining Damage for a PROFILED conventional tool: authored base + Impact only.
 * STR deliberately never appears here (§2 of the V2 balance pass) — it only re-enters through
 * Power Mining, computed separately in {@link PlayerMiningPower}. Pure and player-independent so
 * the tooltip can reuse the exact same numbers gameplay uses.
 */
public final class MiningDamageCalculator {

    private MiningDamageCalculator() {}

    /** Impact I..V mining-damage bonus; index 0 (no enchant) is 0. */
    private static final float[] IMPACT_BONUS = {0f, 5f, 10f, 15f, 20f, 25f};

    public static float impactBonus(int level) {
        int i = Math.max(0, Math.min(IMPACT_BONUS.length - 1, level));
        return IMPACT_BONUS[i];
    }

    public static StatBreakdown compute(ResolvedMiningSource source, int impactLevel) {
        List<StatBreakdown.Contribution> contributions = new ArrayList<>();
        contributions.add(new StatBreakdown.Contribution("Base Tool", StatBreakdown.Op.BASE, source.miningDamage()));
        float value = source.miningDamage();
        if (impactLevel > 0) {
            float bonus = impactBonus(impactLevel);
            contributions.add(new StatBreakdown.Contribution("Impact " + RomanNumeral.of(impactLevel), StatBreakdown.Op.ADD, bonus));
            value += bonus;
        }
        return new StatBreakdown(value, contributions);
    }
}

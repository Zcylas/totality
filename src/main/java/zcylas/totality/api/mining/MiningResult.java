package zcylas.totality.api.mining;

/**
 * Outcome of {@link BlockBreaking#applyImpact}.
 *
 * @param applied   integrity actually removed
 * @param remaining integrity left afterwards (0 when broken)
 * @param max       the block's max durability
 */
public record MiningResult(Outcome outcome, float applied, float remaining, float max) {

    public enum Outcome {
        /** Integrity was reduced; block still stands. */
        DAMAGED,
        /** Integrity reached 0 and the block was destroyed. */
        BROKEN,
        /** Tier too low: no integrity lost. */
        INEFFECTIVE,
        /** A permission check or the terminal destroy path refused; nothing was bypassed. */
        DENIED,
        /** Air, unbreakable, or otherwise not a valid target. */
        INVALID
    }

    public static MiningResult of(Outcome outcome) {
        return new MiningResult(outcome, 0, 0, 0);
    }
}

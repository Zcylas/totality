package zcylas.totality.api.mining;

/**
 * What happens to a saved Integrity record when the block at its position is read again, or physically transformed
 * (Block Breaking V2, Pass 1). Pure arithmetic and policy, so every rule is unit-testable; {@link BlockDamageStorage}
 * applies {@link #reconcile} lazily whenever a record is touched or swept (old saves never need a whole-world
 * rewrite) and {@link #transform} only from an explicit transformation call.
 */
public final class IntegrityReconciliation {

    private IntegrityReconciliation() {}

    public enum Action {
        /** Record still valid as saved. */
        KEEP,
        /** Record kept at {@link Decision#integrity()} of {@link Decision#max()} (same percentage). */
        RESCALE,
        /** Record discarded: the block is at full Integrity (or gone). */
        DELETE
    }

    public record Decision(Action action, float integrity, float max) {
        static final Decision KEEP = new Decision(Action.KEEP, Float.NaN, Float.NaN);
        static final Decision DELETE = new Decision(Action.DELETE, Float.NaN, Float.NaN);
    }

    /**
     * Lazy reconciliation of a saved record against the block now at its position (every read and sweep). A
     * different block id always deletes the record: a lazy read cannot tell a real transformation from a removal
     * followed by a replacement, so it never transfers Integrity across block ids, even between an authored
     * transformation pair. Transformations migrate only through {@link #transform}, at the moment they happen.
     *
     * @param sameBlock             the block at the position is still the record's block
     * @param currentClassification classification of the block now at the position
     * @param currentMax            that block's maximum Block Durability now
     */
    public static Decision reconcile(float savedIntegrity, float savedMax, boolean sameBlock,
                                     BlockProfile.Classification currentClassification, float currentMax) {
        if (!sameBlock) return Decision.DELETE;                                          // never transfers to another block
        if (currentClassification != BlockProfile.Classification.ORDINARY) return Decision.DELETE;   // no dormant finite damage
        if (!validMax(savedMax) || !Float.isFinite(savedIntegrity)) return Decision.DELETE;         // corrupt: back to full
        if (!validMax(currentMax)) return Decision.DELETE;
        if (savedMax == currentMax) return Decision.KEEP;
        return rescaled(savedIntegrity, savedMax, currentMax);
    }

    /**
     * Migration for a physical transformation performed right now (the caller is converting the block that is at
     * the position into the new block). Integrity carries over, by percentage, only when the record belongs to the
     * block actually being transformed AND the pair is an authored transformation; anything else deletes it.
     *
     * @param recordIsLiveBlock the record's block is the block currently standing at the position, i.e. the block
     *                          that undergoes the transformation (false after a removal, or for a stale record)
     * @param authoredPair      {@code from -> to} is an explicitly authored physical transformation
     */
    public static Decision transform(float savedIntegrity, float savedMax, boolean recordIsLiveBlock, boolean authoredPair,
                                     BlockProfile.Classification newClassification, float newMax) {
        if (!recordIsLiveBlock || !authoredPair) return Decision.DELETE;
        if (newClassification != BlockProfile.Classification.ORDINARY) return Decision.DELETE;
        if (!validMax(savedMax) || !Float.isFinite(savedIntegrity) || !validMax(newMax)) return Decision.DELETE;
        return rescaled(savedIntegrity, savedMax, newMax);
    }

    private static Decision rescaled(float savedIntegrity, float savedMax, float newMax) {
        float integrity = rescale(savedIntegrity, savedMax, newMax);
        return integrity >= newMax ? Decision.DELETE : new Decision(Action.RESCALE, integrity, newMax);
    }

    /**
     * {@code round(newMax × oldIntegrity / oldMax)}, clamped to {@code [min(1, newMax), newMax]} so a damaged block
     * never becomes a 0-Integrity block that is still standing. Callers must pass a valid (positive, finite)
     * {@code oldMax}.
     */
    public static float rescale(float oldIntegrity, float oldMax, float newMax) {
        float scaled = Math.round((double) newMax * oldIntegrity / oldMax);
        return Math.max(Math.min(1f, newMax), Math.min(newMax, scaled));
    }

    /**
     * Folding a record left on a non-owner position of a shared assembly (saved before ownership was shared) into
     * the owner: the more damaged record wins, so no existing damage is lost and none is invented. Compared as
     * current (recovered) fractions of each record's own maximum.
     *
     * @param ownerFraction owner's current Integrity / max, or NaN when the owner has no record
     * @return true when the member's record should replace the owner's
     */
    public static boolean memberWinsFold(float ownerFraction, float memberFraction) {
        if (!Float.isFinite(memberFraction)) return false;
        return Float.isNaN(ownerFraction) || memberFraction < ownerFraction;
    }

    private static boolean validMax(float max) { return Float.isFinite(max) && max > 0f; }
}

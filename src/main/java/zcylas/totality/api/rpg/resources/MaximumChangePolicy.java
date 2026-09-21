package zcylas.totality.api.rpg.resources;

/**
 * How {@code current} reconciles when a resource's resolved maximum changes —
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §11.4, exact values. {@link #CLAMP_CURRENT} is
 * "default and safest" per canonical, and every {@code GENERIC_COMPONENT} resource's default via
 * {@code ResourceGrantPolicy.DEFAULT} unless it explicitly opts into something else. Standard Spell
 * Slots is the first production resource to opt into {@link #PRESERVE_DEFICIT} (Phase 6 correction
 * pass, 2026-09-16) — see {@code StandardSpellSlotResources#register()}.
 */
public enum MaximumChangePolicy {
    /** {@code current = min(current, newMax)}. Default and safest. */
    CLAMP_CURRENT,
    /** Maintains the current/max percentage, with deterministic (floor) rounding. */
    PRESERVE_RATIO,
    /** Maintains the missing amount ({@code oldMax - current}) rather than the ratio. */
    PRESERVE_DEFICIT,
    /** Current above the new maximum moves to {@code overflowUnits} rather than being clamped away. */
    ALLOW_OVERFLOW;

    /**
     * The pure §11.4 reconciliation formula — shared by {@link PlayerResourceService}'s scalar
     * {@code reconcileMaximum} and {@code ClassChangeReconciler}'s partitioned equivalent, so the
     * two never drift apart. Returns only the reconciled {@code current} value; {@link
     * #ALLOW_OVERFLOW}'s "excess moves to an overflow bucket" half is each caller's own
     * responsibility, since where that bucket lives differs between scalar and partitioned state —
     * this method leaves {@code current} untouched for that policy, exactly like the scalar
     * implementation this was extracted from.
     */
    public long reconcileCurrent(long current, long previousMaximumUnits, long newMaximumUnits, long floor) {
        return switch (this) {
            case CLAMP_CURRENT -> Math.min(current, newMaximumUnits);
            case PRESERVE_RATIO -> previousMaximumUnits <= 0 ? Math.min(current, newMaximumUnits)
                    : Math.max(floor, Math.min(newMaximumUnits, Math.floorDiv(Math.multiplyExact(current, newMaximumUnits), previousMaximumUnits)));
            case PRESERVE_DEFICIT -> {
                long deficit = Math.max(0, Math.subtractExact(previousMaximumUnits, current));
                yield Math.max(floor, Math.min(newMaximumUnits, Math.subtractExact(newMaximumUnits, deficit)));
            }
            case ALLOW_OVERFLOW -> current;
        };
    }
}

package zcylas.totality.api.rpg.resources;

/**
 * How {@code current} reconciles when a resource's resolved maximum changes —
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §11.4, exact values. {@link #CLAMP_CURRENT} is
 * "default and safest" per canonical and the only policy any production/test resource in this
 * codebase currently selects.
 */
public enum MaximumChangePolicy {
    /** {@code current = min(current, newMax)}. Default and safest. */
    CLAMP_CURRENT,
    /** Maintains the current/max percentage, with deterministic (floor) rounding. */
    PRESERVE_RATIO,
    /** Maintains the missing amount ({@code oldMax - current}) rather than the ratio. */
    PRESERVE_DEFICIT,
    /** Current above the new maximum moves to {@code overflowUnits} rather than being clamped away. */
    ALLOW_OVERFLOW
}

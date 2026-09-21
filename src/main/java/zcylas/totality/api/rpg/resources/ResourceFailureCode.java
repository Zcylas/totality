package zcylas.totality.api.rpg.resources;

/**
 * Canonical mutation-failure vocabulary — {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §21.1,
 * exact names and order. Distinct from {@link ResourceQueryFailureReason}: that enum explains why a
 * read-only {@link PlayerResourceService#query} could not produce a snapshot; this one explains why
 * a mutation ({@code trySpend}/{@code drain}/{@code restore}/{@code set}/{@code transact}) did not
 * commit. A mutation path may still surface a {@link ResourceQueryFailureReason}-shaped problem
 * (e.g. the resource simply is not registered) — that case maps onto the single closest code here
 * ({@link #UNKNOWN_RESOURCE}) rather than duplicating query's own reason set under a second name.
 */
public enum ResourceFailureCode {
    UNKNOWN_RESOURCE,
    MODEL_MISMATCH,
    RESOURCE_NOT_INSTANTIATED,
    RESOURCE_NOT_ENTITLED,
    RESOURCE_INACTIVE,
    RESOURCE_UNAVAILABLE,
    RESOURCE_LOCKED,
    SYSTEM_DISABLED,
    INVALID_AMOUNT,
    INVALID_TIER,
    INSUFFICIENT_RESOURCE,
    NO_ELIGIBLE_TIER,
    MAXIMUM_ZERO,
    OVERFLOW_NOT_SUPPORTED,
    PARTIAL_OPERATION_FORBIDDEN,
    OPERATION_UNSUPPORTED_BY_AUTHORITY,
    ATOMIC_OPERATION_UNSUPPORTED,
    STALE_REVISION,
    DUPLICATE_REQUEST,
    BLOCKED_BY_RULE,
    MIGRATION_REQUIRED,
    CORRUPT_STATE,
    INTERNAL_ERROR
}

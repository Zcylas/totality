package zcylas.totality.api.rpg.resources;

/**
 * Standard death-handling rules a resource's {@link ResourceLifecyclePolicy} may declare.
 * See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §17.2.
 *
 * No production resource declares a policy other than the default in this Phase 1 patch — see
 * {@link ResourceLifecyclePolicy#DEFAULT}. Real per-resource death behavior is decided when each
 * resource is actually migrated, not invented here.
 */
public enum ResourceDeathPolicy {
    KEEP_CURRENT,
    RESET_TO_MINIMUM,
    RESET_TO_MAXIMUM,
    SET_TO_AUTHORED_VALUE,
    CLEAR_OVERFLOW,
    CUSTOM
}

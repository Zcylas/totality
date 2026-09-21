package zcylas.totality.api.rpg.resources;

/**
 * Standard death-handling rules a resource's {@link ResourceLifecyclePolicy} may declare.
 * See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §17.2.
 *
 * No production resource declares a policy other than the default as of Phase 2A — see
 * {@link ResourceLifecyclePolicy#DEFAULT}'s Javadoc for why this is effectively inert for
 * {@code totality:health}/{@code totality:food} specifically. Real per-resource death behavior is
 * decided when each {@code GENERIC_COMPONENT} resource is actually migrated, not invented here.
 */
public enum ResourceDeathPolicy {
    KEEP_CURRENT,
    RESET_TO_MINIMUM,
    RESET_TO_MAXIMUM,
    SET_TO_AUTHORED_VALUE,
    CLEAR_OVERFLOW,
    CUSTOM
}

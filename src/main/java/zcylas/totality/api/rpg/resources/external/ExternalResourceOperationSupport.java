package zcylas.totality.api.rpg.resources.external;

/**
 * One operation an {@link ExternalPlayerResourceAdapter} declares it supports. An adapter may
 * support query while rejecting every mutation. See
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §6.5.
 *
 * Phase 2A's Health/Food adapters declare only {@link #QUERY} — restore/drain/set are explicitly
 * out of scope for this phase (see the Phase 2A task's scope exclusions). The remaining values are
 * declared now so a later phase's adapters can opt into them without a definition-shape change,
 * matching the precedent set by {@link zcylas.totality.api.rpg.resources.ResourceCapability} in
 * Phase 1 (a complete canonical enum where only a subset has a real consumer yet).
 */
public enum ExternalResourceOperationSupport {
    QUERY,
    RESTORE,
    DRAIN,
    SET
}

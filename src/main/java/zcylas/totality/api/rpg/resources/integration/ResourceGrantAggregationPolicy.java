package zcylas.totality.api.rpg.resources.integration;

/** Canonical {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §16.4, exact values. */
public enum ResourceGrantAggregationPolicy {
    /** One canonical owner grants the resource; other systems may modify it. Recommended for Rage/Ki. */
    SINGLE_OWNER,
    /** Several compatible sources grant access to one shared pool. */
    SHARED_RESOURCE,
    /** Only the highest-priority valid grant controls activation/base structure. */
    HIGHEST_PRIORITY_SOURCE,
    /** Independent pools sharing one definition, keyed by an explicit instance id. Rarely needed. */
    SEPARATE_INSTANCES
}

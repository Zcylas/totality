package zcylas.totality.api.rpg.resources.integration;

/**
 * Whether a {@link ResourceGrant} is persistent, conditional, or temporary — canonical
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §16.2 names this field but states its values only
 * as prose ("Whether the grant is persistent, conditional, or temporary"); transcribed directly as
 * an enum with no invented values.
 */
public enum ResourceGrantMode {
    PERSISTENT,
    CONDITIONAL,
    TEMPORARY
}

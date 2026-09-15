package zcylas.totality.api.rpg.resources.integration;

/** Canonical {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §16.10, exact values. */
public enum ResourceVisibilityPolicy {
    WHEN_GRANTED,
    WHEN_ACTIVE,
    WHEN_AVAILABLE,
    ALWAYS_FOR_OWNER,
    MENU_ONLY,
    HIDDEN,
    CUSTOM
}

package zcylas.totality.api.rpg.resources.presentation;

/**
 * Whether/when a resource participates in the live HUD. See
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §19.4.
 *
 * Phase 2A only ever registers {@link #CORE_CONSTANT} (Health, Food). The remaining values are
 * declared for definition-shape completeness; no contextual/menu HUD selection logic is
 * implemented by this phase (see the scope exclusions in the Phase 2A task).
 */
public enum ResourceHudRole {
    CORE_CONSTANT,
    SURVIVAL_CONSTANT,
    CONTEXTUAL_ACCESS,
    CONTEXTUAL_RELEVANCE,
    MENU_ONLY,
    NONE
}

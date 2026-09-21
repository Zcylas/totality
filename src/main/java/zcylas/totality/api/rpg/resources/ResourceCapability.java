package zcylas.totality.api.rpg.resources;

/**
 * Feature declarations a {@link PlayerResourceDefinition} opts into. Capabilities describe what a
 * resource supports; they do not implement behavior themselves — actual mutation/regeneration/etc.
 * logic is supplied by later-phase strategy code, not by this Phase 1 foundation.
 * See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §9.
 */
public enum ResourceCapability {
    SPENDABLE,
    RESTORABLE,
    DIRECT_DRAIN,
    PASSIVE_REGENERATION,
    PASSIVE_DECAY,
    MAXIMUM_MODIFIERS,
    TEMPORARY_MAXIMUM_MODIFIERS,
    OVERFLOW,
    /** Only valid on {@link ResourceModel#PARTITIONED_POOL} definitions. */
    PARTITIONED_SPENDING,
    COST_MODIFIERS,
    RATE_MODIFIERS,
    CLIENT_PREDICTION,
    OFFLINE_PROGRESSION,
    THRESHOLD_EVENTS,
    HUD_VISIBLE,
    MENU_VISIBLE
}

package zcylas.totality.api.rpg.resources;

/**
 * Declares who owns a registered resource's mutable state.
 * See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §4.2.
 */
public enum ResourceStateAuthority {
    /** State lives in {@link PlayerResourceStateComponent}. Mana, Stamina, Rage, Ki, spell slots, ... */
    GENERIC_COMPONENT,
    /** Another system owns the state (vanilla Health/Food/air); the resource is still registered and queryable. */
    EXTERNAL_ADAPTER
}

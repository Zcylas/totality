package zcylas.totality.api.rpg.resources;

/**
 * The two Phase 1 storage models a {@link PlayerResourceDefinition} may declare.
 * See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §6.
 */
public enum ResourceModel {
    /** A single bounded numeric value (HP, Mana, Stamina, Rage, Ki, ...). */
    SCALAR,
    /** Independent current/max counts keyed by an integer partition (spell slot tiers, Hit Dice sizes, ...). */
    PARTITIONED_POOL
}

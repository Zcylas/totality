package zcylas.totality.api.rpg.resources.integration;

/** Canonical {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §16.7, exact values. */
public enum ResourceRemovalPolicy {
    /** Delete state after the final grant disappears. Default for class/species/lineage/discipline pools. */
    REMOVE_STATE,
    /** Retain current state but make it inactive/unavailable/normally invisible. Opt-in only. */
    PRESERVE_DORMANT,
    /** Keep a dormant instance but reset it to an owner-defined value. Opt-in only. */
    RESET_AND_PRESERVE,
    /** Convert through an explicit owner-supplied migration/transaction. */
    CONVERT,
    /** Requires an owner handler and acceptance tests. */
    CUSTOM
}

package zcylas.totality.api.rpg.resources;

/**
 * How a {@link ResourceCost.Partitioned} spend selects among partitions —
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §14.1, exact V1 values. Only {@link #EXACT_TIER}
 * has a real caller in this codebase (see canonical §14.2: "Player-cast spells use EXACT_TIER...
 * ordinary player spellcasting must not use" the others) — the remaining three are declared now,
 * unimplemented-but-structurally-present, matching the precedent already set by
 * {@link zcylas.totality.api.rpg.resources.external.ExternalResourceOperationSupport} (a complete
 * canonical enum where only a subset has a real consumer yet).
 */
public enum PartitionSelectionPolicy {
    EXACT_TIER,
    AT_LEAST_TIER,
    LOWEST_ELIGIBLE_TIER,
    HIGHEST_ELIGIBLE_TIER
}

package zcylas.totality.api.rpg.resources;

/**
 * How a resource's value should be interpreted. Not every resource is "full is good."
 * See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §8.
 */
public enum ResourcePolarity {
    HIGH_IS_GOOD,
    HIGH_IS_BAD,
    /**
     * A resource whose preferred state is a bounded range rather than the maximum. Requires a
     * {@link ResourceTargetRange}. No resource using this polarity is registered or planned by
     * the current Resource API scope; Temperature specifically is owned by a future
     * Environment/Physiology system, not this API.
     */
    TARGET_RANGE,
    NEUTRAL
}

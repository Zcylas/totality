package zcylas.totality.api.rpg.resources;

import java.util.OptionalLong;

/**
 * The comfortable band for a {@link ResourcePolarity#TARGET_RANGE} resource — a resource whose
 * preferred state is a bounded range rather than a maximum. See
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §8.2.
 *
 * No resource using this shape is registered or planned by the current Resource API scope.
 */
public record ResourceTargetRange(
        long preferredMinimumUnits,
        long preferredMaximumUnits,
        OptionalLong warningMinimumUnits,
        OptionalLong warningMaximumUnits
) {
    public ResourceTargetRange {
        if (preferredMinimumUnits > preferredMaximumUnits) {
            throw new IllegalArgumentException(
                    "preferredMinimumUnits (" + preferredMinimumUnits
                            + ") must be <= preferredMaximumUnits (" + preferredMaximumUnits + ")");
        }
        if (warningMinimumUnits.isPresent() && warningMinimumUnits.getAsLong() > preferredMinimumUnits) {
            throw new IllegalArgumentException(
                    "warningMinimumUnits (" + warningMinimumUnits.getAsLong()
                            + ") must be <= preferredMinimumUnits (" + preferredMinimumUnits + ")");
        }
        if (warningMaximumUnits.isPresent() && warningMaximumUnits.getAsLong() < preferredMaximumUnits) {
            throw new IllegalArgumentException(
                    "warningMaximumUnits (" + warningMaximumUnits.getAsLong()
                            + ") must be >= preferredMaximumUnits (" + preferredMaximumUnits + ")");
        }
    }
}

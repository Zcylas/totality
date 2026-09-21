package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * A read-only, point-in-time view of one resource's authoritative mechanical current/maximum,
 * expressed in the resource's own fixed-point units. See
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §3 ("Presentation and synchronization"), §7.1.
 *
 * Produced only by {@link PlayerResourceService#query}; never constructed as a side effect of a
 * failed lookup (an unresolvable query returns a {@link ResourceQueryResult.Failure} instead of a
 * fabricated zero snapshot — see canonical §5.4/§21).
 */
public record ResourceSnapshot(Identifier resourceId, long currentUnits, long maximumUnits, long unitScale) {

    public ResourceSnapshot {
        Objects.requireNonNull(resourceId, "resourceId");
        if (unitScale < 1) {
            throw new IllegalArgumentException("unitScale must be >= 1, was " + unitScale);
        }
    }

    /** The real mechanical current value (e.g. {@code 13.5} Health), not a display value. */
    public double mechanicalCurrent() {
        return (double) currentUnits / unitScale;
    }

    /** The real mechanical maximum value (e.g. {@code 20.0} Health), not a display value. */
    public double mechanicalMaximum() {
        return (double) maximumUnits / unitScale;
    }
}

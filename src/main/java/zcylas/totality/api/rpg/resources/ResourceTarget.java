package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * The target of a privileged {@link PlayerResourceService#set} correction/migration/admin
 * operation — canonical {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §12.1 names this type as
 * {@code set}'s parameter but never gives its field shape; reconstructed here as the minimal
 * "which resource (and, for a partitioned pool, which partition) to set to what absolute value"
 * needed to make {@code set} concrete, mirroring {@link ResourceAmount}'s own already-established
 * scalar/partitioned factory-method shape.
 */
public record ResourceTarget(Identifier resourceId, OptionalInt partition, long absoluteUnits) {

    public ResourceTarget {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(partition, "partition");
    }

    public static ResourceTarget scalar(Identifier resourceId, long absoluteUnits) {
        return new ResourceTarget(resourceId, OptionalInt.empty(), absoluteUnits);
    }

    public static ResourceTarget partitioned(Identifier resourceId, int partition, long absoluteUnits) {
        return new ResourceTarget(resourceId, OptionalInt.of(partition), absoluteUnits);
    }
}

package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Structured mutation failure — {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §21.1, exact field
 * shape. Canonical's record has no {@code resourceId} field (a single-resource operation's caller
 * already knows which resource it asked about); {@link ResourceTransactionResult} — which spans
 * several resources — carries the failing operation's index alongside this record instead of
 * repeating the field here.
 */
public record ResourceFailure(
        ResourceFailureCode code,
        Identifier reasonId,
        OptionalLong requiredUnits,
        OptionalLong availableUnits,
        OptionalInt partition
) {
    public ResourceFailure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(reasonId, "reasonId");
        Objects.requireNonNull(requiredUnits, "requiredUnits");
        Objects.requireNonNull(availableUnits, "availableUnits");
        Objects.requireNonNull(partition, "partition");
    }

    /** The common shape: a code plus a machine-readable reason, no unit/partition detail. */
    public static ResourceFailure of(ResourceFailureCode code, Identifier reasonId) {
        return new ResourceFailure(code, reasonId, OptionalLong.empty(), OptionalLong.empty(), OptionalInt.empty());
    }

    /** For {@link ResourceFailureCode#INSUFFICIENT_RESOURCE}: the requested cost versus what was actually available. */
    public static ResourceFailure insufficientResource(Identifier reasonId, long requiredUnits, long availableUnits) {
        return new ResourceFailure(ResourceFailureCode.INSUFFICIENT_RESOURCE, reasonId,
                OptionalLong.of(requiredUnits), OptionalLong.of(availableUnits), OptionalInt.empty());
    }

    /** For a partitioned failure (e.g. {@link ResourceFailureCode#NO_ELIGIBLE_TIER}) naming the partition involved. */
    public static ResourceFailure ofPartition(ResourceFailureCode code, Identifier reasonId, int partition) {
        return new ResourceFailure(code, reasonId, OptionalLong.empty(), OptionalLong.empty(), OptionalInt.of(partition));
    }
}

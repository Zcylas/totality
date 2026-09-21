package zcylas.totality.api.rpg.resources;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A resolved structural maximum — {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §10.3. Canonical
 * shows {@code ScalarMaximum}/{@code PartitionedMaximum} as separate top-level records; renamed to
 * nested {@link Scalar}/{@link Partitioned} to match this codebase's established sealed-interface
 * convention (see {@link ResourceQueryResult}), field shape otherwise unchanged.
 *
 * <p>{@code appliedModifiers} is present (never omitted) but always empty in this foundation pass —
 * the full §11 modifier pipeline (permanent/temporary maximum modifiers, priority ordering) is
 * deliberately not built this task; see the implementation report's "deferred" section. A resolver
 * that has no modifiers to apply sets {@code baseUnits == effectiveUnits}.
 */
public sealed interface ResourceMaximum {

    record Scalar(long baseUnits, long effectiveUnits, List<Object> appliedModifiers) implements ResourceMaximum {
        public Scalar {
            Objects.requireNonNull(appliedModifiers, "appliedModifiers");
            appliedModifiers = List.copyOf(appliedModifiers);
        }

        public static Scalar of(long units) {
            return new Scalar(units, units, List.of());
        }
    }

    record Partitioned(Map<Integer, Long> baseByPartition, Map<Integer, Long> effectiveByPartition) implements ResourceMaximum {
        public Partitioned {
            Objects.requireNonNull(baseByPartition, "baseByPartition");
            Objects.requireNonNull(effectiveByPartition, "effectiveByPartition");
            baseByPartition = Map.copyOf(baseByPartition);
            effectiveByPartition = Map.copyOf(effectiveByPartition);
        }
    }
}

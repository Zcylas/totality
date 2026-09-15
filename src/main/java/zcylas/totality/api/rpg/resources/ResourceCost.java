package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * An intentional resource cost passed to {@link PlayerResourceService#trySpend} —
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §12.1/§14.1. Canonical shows
 * {@code PartitionedResourceCost implements ResourceCost} in full but never gives the sealed
 * interface itself or a scalar counterpart a literal definition; {@link Scalar} is the direct,
 * mechanical completion needed so non-partitioned resources (Mana, Stamina, Rage-shaped pools) have
 * a cost shape too, following this codebase's established nested-record-per-sealed-interface
 * convention (see {@link ResourceQueryResult}).
 */
public sealed interface ResourceCost {

    Identifier resourceId();

    record Scalar(Identifier resourceId, long amount) implements ResourceCost {
        public Scalar {
            Objects.requireNonNull(resourceId, "resourceId");
        }
    }

    /** Canonical §14.1, exact field shape. */
    record Partitioned(
            Identifier resourceId,
            int partition,
            long amount,
            PartitionSelectionPolicy selectionPolicy
    ) implements ResourceCost {
        public Partitioned {
            Objects.requireNonNull(resourceId, "resourceId");
            Objects.requireNonNull(selectionPolicy, "selectionPolicy");
        }
    }
}

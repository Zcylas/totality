package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * The result of {@link PlayerResourceService#query}: either a resolved snapshot or a structured,
 * named failure reason. See the class Javadoc on {@link ResourceQueryFailureReason} for why this
 * exists instead of returning {@code null} or a fabricated empty snapshot.
 *
 * <p>Two distinct success shapes exist, matching the two {@link ResourceModel} values a
 * {@link PlayerResourceDefinition} may declare: {@link Success} wraps a scalar {@link ResourceSnapshot}
 * (one current/maximum pair) for {@link ResourceModel#SCALAR} resources, and {@link PartitionedSuccess}
 * wraps a {@link PartitionedResourceSnapshot} (one current/maximum pair per integer partition) for
 * {@link ResourceModel#PARTITIONED_POOL} resources — introduced in Phase 2D alongside the first
 * production {@code PARTITIONED_POOL} resource, {@code totality:spell_slots}. {@link
 * zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapter#snapshot} returns whichever
 * shape matches the definition it was asked about; {@link PlayerResourceService} rejects a mismatch
 * (an adapter returning {@code Success} for a {@code PARTITIONED_POOL} definition, or vice versa) as
 * {@link ResourceQueryFailureReason#CORRUPT_ADAPTER_SNAPSHOT} rather than trusting the wrong shape.
 * Existing scalar-only adapters (Health, Food, Breath, Mana, Stamina) are entirely unaffected — they
 * only ever construct {@link Success} and never see {@link PartitionedSuccess}.
 */
public sealed interface ResourceQueryResult {

    record Success(ResourceSnapshot snapshot) implements ResourceQueryResult {
        public Success {
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    /** The {@link ResourceModel#PARTITIONED_POOL} success shape. See the interface Javadoc above. */
    record PartitionedSuccess(PartitionedResourceSnapshot snapshot) implements ResourceQueryResult {
        public PartitionedSuccess {
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    record Failure(ResourceQueryFailureReason reason, Identifier resourceId) implements ResourceQueryResult {
        public Failure {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(resourceId, "resourceId");
        }
    }

    default boolean isSuccess() {
        return this instanceof Success || this instanceof PartitionedSuccess;
    }
}

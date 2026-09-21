package zcylas.totality.api.rpg.resources;

import java.util.Objects;

/**
 * The result of one {@code trySpend}/{@code drain}/{@code restore}/{@code set} mutation on
 * {@link PlayerResourceService} — canonical {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §21.2.
 * Canonical shows one flat record typed against a single (implicitly scalar) {@code ResourceSnapshot};
 * split here into {@link Success}/{@link PartitionedSuccess}/{@link Failure}, mirroring this
 * codebase's already-established {@link ResourceQueryResult} sealed-interface convention (which
 * faced the identical SCALAR-vs-PARTITIONED_POOL shape problem for queries), so one
 * {@code PlayerResourceService} mutation method can serve both {@link ResourceModel} values without
 * forcing a partitioned pool's before/after into a scalar-shaped record. Field content (before,
 * after, requested, applied, revision) is otherwise unchanged from canonical.
 */
public sealed interface ResourceOperationResult {

    record Success(
            ResourceSnapshot before, ResourceSnapshot after, ResourceAmount requested, long appliedUnits, long revision
    ) implements ResourceOperationResult {
        public Success {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            Objects.requireNonNull(requested, "requested");
        }
    }

    /** The {@link ResourceModel#PARTITIONED_POOL} success shape. See the interface Javadoc above. */
    record PartitionedSuccess(
            PartitionedResourceSnapshot before, PartitionedResourceSnapshot after,
            ResourceAmount requested, long appliedUnits, long revision
    ) implements ResourceOperationResult {
        public PartitionedSuccess {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
            Objects.requireNonNull(requested, "requested");
        }
    }

    record Failure(ResourceFailure failure, ResourceAmount requested, long revision) implements ResourceOperationResult {
        public Failure {
            Objects.requireNonNull(failure, "failure");
            Objects.requireNonNull(requested, "requested");
        }
    }

    default boolean isSuccess() {
        return this instanceof Success || this instanceof PartitionedSuccess;
    }
}

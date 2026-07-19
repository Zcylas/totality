package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * The result of {@link PlayerResourceService#query}: either a resolved {@link ResourceSnapshot}
 * or a structured, named failure reason. See the class Javadoc on {@link ResourceQueryFailureReason}
 * for why this exists instead of returning {@code null} or a fabricated empty snapshot.
 */
public sealed interface ResourceQueryResult {

    record Success(ResourceSnapshot snapshot) implements ResourceQueryResult {
        public Success {
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
        return this instanceof Success;
    }
}

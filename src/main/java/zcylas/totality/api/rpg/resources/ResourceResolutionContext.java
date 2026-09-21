package zcylas.totality.api.rpg.resources;

import java.util.Objects;
import java.util.Optional;

/**
 * Auxiliary context for a {@link ResourceMaximumResolver}/{@code ResourceAvailabilityResolver}
 * call — canonical {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §9.1/§9.2 name this type as a
 * resolver parameter but never give its field shape (the player and definition being resolved are
 * already separate parameters on the resolver method itself). Reconstructed minimally as "why is
 * resolution happening right now," mirroring {@link ResourceContext}'s own {@code cause} field —
 * a resolver that does not need this may ignore it.
 */
public record ResourceResolutionContext(Optional<ResourceCause> cause) {

    public ResourceResolutionContext {
        Objects.requireNonNull(cause, "cause");
    }

    public static final ResourceResolutionContext EMPTY = new ResourceResolutionContext(Optional.empty());

    public static ResourceResolutionContext of(ResourceCause cause) {
        return new ResourceResolutionContext(Optional.of(cause));
    }
}

package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Canonical {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §20.3, exact field shape. */
public record ResourceCause(Identifier type, Optional<Identifier> sourceId, Optional<UUID> actorId) {

    public ResourceCause {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(actorId, "actorId");
    }

    public static ResourceCause of(Identifier type) {
        return new ResourceCause(type, Optional.empty(), Optional.empty());
    }
}

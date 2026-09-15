package zcylas.totality.api.rpg.resources;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Canonical {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §13.2, exact field shape. */
public record ResourceTransaction(List<ResourceOperation> operations, TransactionMode mode, Optional<UUID> requestNonce) {

    public ResourceTransaction {
        Objects.requireNonNull(operations, "operations");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(requestNonce, "requestNonce");
        if (operations.isEmpty()) {
            throw new IllegalArgumentException("a transaction must contain at least one operation");
        }
        operations = List.copyOf(operations);
    }

    public static ResourceTransaction of(ResourceOperation... operations) {
        return new ResourceTransaction(List.of(operations), TransactionMode.ATOMIC_ALL_OR_NOTHING, Optional.empty());
    }
}

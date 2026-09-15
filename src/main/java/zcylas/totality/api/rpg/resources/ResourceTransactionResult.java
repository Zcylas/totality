package zcylas.totality.api.rpg.resources;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The result of {@link PlayerResourceService#transact} — canonical
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §13.2/§21.2 describe the transaction's
 * all-or-nothing commit contract and the general "result must be rich enough for server logic, UI,
 * debugging, tests, and network response" requirement, but never give
 * {@code ResourceTransactionResult} a literal field shape. Kept intentionally lean rather than
 * bundling a full {@link ResourceOperationResult} per operation: on failure, canonical §13.2's own
 * validation-then-commit ordering means nothing was ever applied, so per-operation before/after
 * snapshots would be empty placeholders; on success, a caller that needs the resulting values
 * already has {@link PlayerResourceService#query} to read them. {@code failedOperationIndex} names
 * which operation (0-based, in submission order) caused the failure, since {@link ResourceFailure}
 * itself carries no resource id and a transaction spans several resources.
 */
public record ResourceTransactionResult(
        boolean success,
        Optional<ResourceFailure> failure,
        OptionalInt failedOperationIndex,
        long revision
) {
    public ResourceTransactionResult {
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(failedOperationIndex, "failedOperationIndex");
        if (success && failure.isPresent()) {
            throw new IllegalArgumentException("a successful transaction result must not carry a failure");
        }
        if (!success && failure.isEmpty()) {
            throw new IllegalArgumentException("a failed transaction result must carry a failure");
        }
    }

    public static ResourceTransactionResult success(long revision) {
        return new ResourceTransactionResult(true, Optional.empty(), OptionalInt.empty(), revision);
    }

    public static ResourceTransactionResult failed(ResourceFailure failure, int failedOperationIndex, long revision) {
        return new ResourceTransactionResult(false, Optional.of(failure), OptionalInt.of(failedOperationIndex), revision);
    }

    /** A failure that isn't attributable to one specific operation (e.g. an empty transaction). */
    public static ResourceTransactionResult failedGeneral(ResourceFailure failure, long revision) {
        return new ResourceTransactionResult(false, Optional.of(failure), OptionalInt.empty(), revision);
    }
}

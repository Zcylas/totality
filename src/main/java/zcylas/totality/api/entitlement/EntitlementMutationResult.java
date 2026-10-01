package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * Outcome of one server-side transaction. {@link Status#NO_CHANGE} is the idempotent success case
 * (the requested state already held); {@link Status#REJECTED} carries the reason and changed nothing.
 */
public record EntitlementMutationResult(
        Status status,
        Identifier reasonCode,
        String message,
        List<EntitlementChange> changes,
        UUID transactionId
) {

    public enum Status {
        APPLIED,
        NO_CHANGE,
        REJECTED
    }

    public EntitlementMutationResult {
        changes = List.copyOf(changes);
    }

    public static EntitlementMutationResult applied(List<EntitlementChange> changes, UUID transactionId) {
        return new EntitlementMutationResult(changes.isEmpty() ? Status.NO_CHANGE : Status.APPLIED,
                EntitlementReasons.ALLOWED, "", changes, transactionId);
    }

    public static EntitlementMutationResult noChange(UUID transactionId) {
        return new EntitlementMutationResult(Status.NO_CHANGE, EntitlementReasons.ALLOWED, "", List.of(), transactionId);
    }

    public static EntitlementMutationResult rejected(Identifier reason, String message, UUID transactionId) {
        return new EntitlementMutationResult(Status.REJECTED, reason, message, List.of(), transactionId);
    }

    public boolean isRejected() {
        return status == Status.REJECTED;
    }

    public boolean changed() {
        return status == Status.APPLIED;
    }
}

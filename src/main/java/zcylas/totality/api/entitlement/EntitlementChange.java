package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;

/**
 * One committed mutation, reported to {@link EntitlementEvents#MUTATION} listeners after the transaction
 * commits (canonical §8.1). A single sealed type replaces the canonical list of separate event classes.
 */
public sealed interface EntitlementChange {

    EntitlementKey key();

    record FactAdded(EntitlementKey key, PermanentEntitlementFact fact, PermanentFactRecord record)
            implements EntitlementChange {}

    record FactRevoked(EntitlementKey key, PermanentEntitlementFact fact, PermanentFactRecord record,
                       GrantSourceRef revokedBy, Identifier reasonCode) implements EntitlementChange {}

    record GrantAdded(EntitlementGrant grant) implements EntitlementChange {
        @Override public EntitlementKey key() { return grant.key(); }
    }

    record GrantRemoved(EntitlementGrant grant, RemovalReason reason) implements EntitlementChange {
        @Override public EntitlementKey key() { return grant.key(); }
    }

    record SuspensionAdded(EntitlementSuspension suspension) implements EntitlementChange {
        @Override public EntitlementKey key() { return suspension.key(); }
    }

    record SuspensionRemoved(EntitlementSuspension suspension, RemovalReason reason) implements EntitlementChange {
        @Override public EntitlementKey key() { return suspension.key(); }
    }

    enum RemovalReason {
        /** The provider no longer reports the source as active. */
        SOURCE_ENDED,
        /** Removed by its exact owner. */
        EXPLICIT,
        EXPIRED,
        DEATH
    }
}

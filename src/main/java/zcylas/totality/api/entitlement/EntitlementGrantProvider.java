package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A source-scoped grant provider (canonical §4.2): computes, from the authoritative state of the system
 * that owns the source (class, ancestry, mastery...), which grants should currently exist. Collection must
 * be deterministic and must not mutate the source system. Reconciliation diffs the result against this
 * provider's own current grants only — a provider can never remove another provider's grants.
 */
public interface EntitlementGrantProvider {

    Identifier providerId();

    /** Grant Source types this provider may emit; grants with any other source type are rejected. */
    Set<Identifier> sourceTypeIds();

    void collectGrants(ServerPlayer player, Collector collector);

    /** Builds deterministic, provider-stamped WHILE_SOURCE_ACTIVE grants. */
    final class Collector {
        private final Identifier providerId;
        private final List<EntitlementGrant> grants = new ArrayList<>();

        public Collector(Identifier providerId) {
            this.providerId = providerId;
        }

        /** Grants every action the entitlement type supports. */
        public void grant(EntitlementKey key, GrantSourceRef source) {
            grant(key, source, Set.of(), false);
        }

        public void grant(EntitlementKey key, GrantSourceRef source, Set<Identifier> actions, boolean revealContent) {
            grants.add(EntitlementGrant.sourceBound(providerId, key, source, actions, revealContent));
        }

        public List<EntitlementGrant> grants() {
            return List.copyOf(grants);
        }
    }
}

package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.entitlement.requirement.EntitlementDependencyKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * A player's complete entitlement state: the persisted {@link EntitlementLedger} plus the server runtime
 * grant index (canonical §3.3) — provider-reconciled grants, session grants and runtime suspensions —
 * and derived caches. Only the ledger is saved; everything else is rebuilt from its sources.
 */
public final class PlayerEntitlementState {

    final EntitlementLedger ledger;
    final Map<Identifier, Map<UUID, EntitlementGrant>> providerGrants = new LinkedHashMap<>();
    final Map<UUID, EntitlementGrant> sessionGrants = new LinkedHashMap<>();
    final Map<UUID, EntitlementSuspension> runtimeSuspensions = new LinkedHashMap<>();

    final Map<DecisionCacheKey, EntitlementDecision> decisionCache = new HashMap<>();
    final Map<AccessibleCacheKey, AccessibleSet> accessibleCache = new HashMap<>();
    /** Last accessible set per tracked type, for availability-change events. Null until baselined. */
    Map<Identifier, Set<EntitlementKey>> lastAccessible;
    /** Grants dropped by the death copy, reported once the respawned player exists. */
    final List<EntitlementGrant> pendingDeathRemovals = new ArrayList<>();
    /** Providers whose last collection failed: their previous grants are kept on record but withheld. */
    final Map<Identifier, SourceFailure> failedProviders = new LinkedHashMap<>();
    /** Last failure of each contributor (diagnostics; decisions it applied to failed closed). */
    final Map<Identifier, SourceFailure> contributorFailures = new LinkedHashMap<>();
    /** Hash of the display view last sent to the client; null until the first send. */
    Integer lastViewHash;
    int transactionDepth;

    /** Diagnostic record of a failing provider or contributor. */
    public record SourceFailure(String error, long firstFailureUtc, long lastFailureUtc, int count) {
        SourceFailure again(String latestError, long utc) {
            return new SourceFailure(latestError, firstFailureUtc, utc, count + 1);
        }
    }

    record DecisionCacheKey(EntitlementKey key, Identifier actionId, EntitlementQueryContext.Purpose purpose) {}

    record AccessibleCacheKey(Identifier typeId, Identifier actionId) {}

    record AccessibleSet(Set<EntitlementKey> keys, Set<EntitlementDependencyKey> dependencies, long revision) {}

    public PlayerEntitlementState() {
        this(new EntitlementLedger());
    }

    public PlayerEntitlementState(EntitlementLedger ledger) {
        this.ledger = ledger;
    }

    public EntitlementLedger ledger() {
        return ledger;
    }

    public long revision() {
        return ledger.revision;
    }

    public Map<Identifier, Map<UUID, EntitlementGrant>> providerGrants() {
        Map<Identifier, Map<UUID, EntitlementGrant>> copy = new LinkedHashMap<>();
        providerGrants.forEach((id, grants) -> copy.put(id, Collections.unmodifiableMap(grants)));
        return Collections.unmodifiableMap(copy);
    }

    public Map<UUID, EntitlementGrant> sessionGrants() {
        return Collections.unmodifiableMap(sessionGrants);
    }

    public Map<Identifier, SourceFailure> failedProviders() {
        return Collections.unmodifiableMap(failedProviders);
    }

    public Map<Identifier, SourceFailure> contributorFailures() {
        return Collections.unmodifiableMap(contributorFailures);
    }

    /** Provider grants currently withheld because their provider could not be verified. */
    public Stream<EntitlementGrant> withheldGrants() {
        return failedProviders.keySet().stream()
                .flatMap(id -> providerGrants.getOrDefault(id, Map.of()).values().stream());
    }

    public Map<UUID, EntitlementSuspension> runtimeSuspensions() {
        return Collections.unmodifiableMap(runtimeSuspensions);
    }

    /** Every grant from every store, expired or not. */
    public Stream<EntitlementGrant> allGrants() {
        return Stream.of(
                providerGrants.values().stream().flatMap(m -> m.values().stream()),
                sessionGrants.values().stream(),
                ledger.persistentGrants.values().stream()
        ).flatMap(s -> s);
    }

    public Stream<EntitlementSuspension> allSuspensions() {
        return Stream.concat(ledger.suspensions.values().stream(), runtimeSuspensions.values().stream());
    }

    void bumpRevision() {
        ledger.revision++;
        decisionCache.clear();
        accessibleCache.clear();
    }

    /** Drops cached results that read {@code changed}. @return whether anything was dropped. */
    boolean invalidate(EntitlementDependencyKey changed) {
        boolean dropped = decisionCache.values().removeIf(d -> d.dependenciesRead().stream().anyMatch(k -> k.matches(changed)));
        dropped |= accessibleCache.values().removeIf(s -> s.dependencies().stream().anyMatch(k -> k.matches(changed)));
        return dropped;
    }

    /**
     * The respawn copy (canonical §3.13): the ledger is copied (minus UNTIL_DEATH grants when the player
     * died), session grants and runtime suspensions carry over, provider grants are rebuilt afterwards.
     */
    public PlayerEntitlementState copyForRespawn(boolean died) {
        List<EntitlementGrant> removed = new ArrayList<>();
        PlayerEntitlementState copy = new PlayerEntitlementState(ledger.copy(died, removed));
        copy.sessionGrants.putAll(sessionGrants);
        copy.runtimeSuspensions.putAll(runtimeSuspensions);
        copy.pendingDeathRemovals.addAll(removed);
        return copy;
    }
}

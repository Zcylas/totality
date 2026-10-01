package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.entitlement.requirement.EntitlementDependencyKey;
import zcylas.totality.api.entitlement.requirement.RequirementEvaluation;

import java.util.List;
import java.util.Set;

/**
 * The structured answer to one operation-specific query (canonical §6.3). {@link #allowed()} is only a
 * convenience — callers that need to explain or log a result should keep the full decision.
 *
 * @param failures           full, unredacted requirement failures (server-side only; see
 *                           {@link EntitlementDisplaySnapshot#from} for the client-safe form)
 * @param successfulPaths    every basis that authorized the action
 * @param dependenciesRead   external state the decision depended on (cache invalidation)
 * @param cacheable          false when it read uncacheable or time-limited state
 */
public record EntitlementDecision(
        Kind kind,
        Identifier primaryReasonCode,
        Identifier actionId,
        EntitlementSnapshot snapshot,
        List<RequirementEvaluation> failures,
        List<AuthorizationPath> successfulPaths,
        long playerEntitlementRevision,
        boolean serverAuthoritative,
        Set<EntitlementDependencyKey> dependenciesRead,
        boolean cacheable
) {

    public enum Kind {
        ALLOWED,
        DENIED,
        HIDDEN
    }

    public EntitlementDecision {
        failures = List.copyOf(failures);
        successfulPaths = List.copyOf(successfulPaths);
        dependenciesRead = Set.copyOf(dependenciesRead);
    }

    public boolean allowed() {
        return kind == Kind.ALLOWED;
    }

    public EntitlementKey key() {
        return snapshot.key();
    }

    public EntitlementDecision withPrimaryReason(Identifier reason) {
        return new EntitlementDecision(kind, reason, actionId, snapshot, failures, successfulPaths,
                playerEntitlementRevision, serverAuthoritative, dependenciesRead, cacheable);
    }

    public EntitlementDisplayState displayState() {
        if (kind == Kind.HIDDEN) return EntitlementDisplayState.HIDDEN;
        if (kind == Kind.ALLOWED) {
            return snapshot.temporarilyAccessible()
                    ? EntitlementDisplayState.AVAILABLE_SOURCE_BOUND
                    : EntitlementDisplayState.AVAILABLE_PERMANENT;
        }
        if (primaryReasonCode.equals(EntitlementReasons.TEMPORARILY_SUSPENDED)) return EntitlementDisplayState.SUSPENDED;
        if (primaryReasonCode.equals(EntitlementReasons.KNOWN_BUT_UNAVAILABLE)
                || primaryReasonCode.equals(EntitlementReasons.SOURCE_UNVERIFIED)) return EntitlementDisplayState.KNOWN_UNAVAILABLE;
        if (primaryReasonCode.equals(EntitlementReasons.MISSING_REQUIREMENT)) return EntitlementDisplayState.MISSING_REQUIREMENT;
        return EntitlementDisplayState.LOCKED;
    }
}

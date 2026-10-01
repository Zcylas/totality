package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Optional;

/**
 * Optional per-content definition (canonical §2.4). Content without one uses its type's defaults.
 * Holds only data-safe values — no items, screens, classes or callbacks.
 *
 * @param authorizationPolicyId overrides the type's default policy
 * @param visibleByDefault      overrides the type's visibility default when no visibility rule exists
 * @param retentionPolicy       overrides the type's default retention
 */
public record EntitlementDefinition(
        EntitlementKey key,
        String nameTranslationKey,
        Optional<Identifier> authorizationPolicyId,
        EntitlementRuleSet rules,
        Optional<Boolean> visibleByDefault,
        Optional<EntitlementRetentionPolicy> retentionPolicy
) {

    public EntitlementDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(nameTranslationKey, "nameTranslationKey");
        Objects.requireNonNull(authorizationPolicyId, "authorizationPolicyId");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(visibleByDefault, "visibleByDefault");
        Objects.requireNonNull(retentionPolicy, "retentionPolicy");
    }

    public static EntitlementDefinition of(EntitlementKey key, String nameTranslationKey) {
        return new EntitlementDefinition(key, nameTranslationKey, Optional.empty(), EntitlementRuleSet.EMPTY,
                Optional.empty(), Optional.empty());
    }

    public EntitlementDefinition withRules(EntitlementRuleSet newRules) {
        return new EntitlementDefinition(key, nameTranslationKey, authorizationPolicyId, newRules, visibleByDefault, retentionPolicy);
    }

    public EntitlementDefinition withPolicy(Identifier policyId) {
        return new EntitlementDefinition(key, nameTranslationKey, Optional.of(policyId), rules, visibleByDefault, retentionPolicy);
    }

    public EntitlementDefinition withVisibleByDefault(boolean visible) {
        return new EntitlementDefinition(key, nameTranslationKey, authorizationPolicyId, rules, Optional.of(visible), retentionPolicy);
    }

    public EntitlementDefinition withRetention(EntitlementRetentionPolicy retention) {
        return new EntitlementDefinition(key, nameTranslationKey, authorizationPolicyId, rules, visibleByDefault, Optional.of(retention));
    }
}

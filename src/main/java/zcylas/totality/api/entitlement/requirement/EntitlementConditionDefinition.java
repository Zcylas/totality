package zcylas.totality.api.entitlement.requirement;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.entitlement.EntitlementQueryContext;

import java.util.Objects;
import java.util.Set;

/**
 * A registered, immutable condition: one condition type plus its typed configuration, the authored
 * failure message and its disclosure policy (canonical §5.2). Requirement trees reference it by id.
 */
public record EntitlementConditionDefinition<C>(
        Identifier id,
        EntitlementConditionType<C> type,
        C configuration,
        String failureTranslationKey,
        DisclosurePolicy disclosurePolicy
) {

    public EntitlementConditionDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(failureTranslationKey, "failureTranslationKey");
        Objects.requireNonNull(disclosurePolicy, "disclosurePolicy");
    }

    public boolean test(EntitlementQueryContext context) {
        return type.test(configuration, context);
    }

    public Set<EntitlementDependencyKey> dependencies() {
        return type.dependencies(configuration);
    }
}

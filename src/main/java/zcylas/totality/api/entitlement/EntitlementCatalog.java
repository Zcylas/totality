package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.entitlement.requirement.EntitlementConditionDefinition;
import zcylas.totality.api.entitlement.requirement.EntitlementConditionType;
import zcylas.totality.api.entitlement.requirement.EntitlementRequirement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The code-registered entitlement catalog: types, actions, Grant Source types, authorization policies,
 * condition types and definitions, per-content definitions, grant providers and state contributors
 * (canonical §8.4's registries, kept together so cross-registry validation happens in one place).
 *
 * <p>Production uses {@link #INSTANCE}; tests build isolated catalogs. Registration happens during mod
 * initialization only.
 */
public final class EntitlementCatalog {

    public static final EntitlementCatalog INSTANCE = new EntitlementCatalog();

    private final Map<Identifier, EntitlementTypeDefinition> types = new LinkedHashMap<>();
    private final Set<Identifier> actions = new LinkedHashSet<>();
    private final Set<Identifier> sourceTypes = new LinkedHashSet<>();
    private final Map<Identifier, EntitlementAuthorizationPolicy> policies = new LinkedHashMap<>();
    private final Map<Identifier, EntitlementConditionType<?>> conditionTypes = new LinkedHashMap<>();
    private final Map<Identifier, EntitlementConditionDefinition<?>> conditions = new LinkedHashMap<>();
    private final Map<EntitlementKey, EntitlementDefinition> definitions = new LinkedHashMap<>();
    private final Map<Identifier, EntitlementGrantProvider> providers = new LinkedHashMap<>();
    private final List<EntitlementStateContributor> contributors = new ArrayList<>();

    /** A catalog with the core actions, the debug/admin/legacy source types and the generic policies. */
    public EntitlementCatalog() {
        EntitlementActions.CORE.forEach(actions::add);
        sourceTypes.add(GrantSourceTypes.DEBUG);
        sourceTypes.add(GrantSourceTypes.ADMIN);
        sourceTypes.add(GrantSourceTypes.LEGACY_MIGRATION);
        registerPolicy(EntitlementPolicies.unlockOrGrant());
        registerPolicy(EntitlementPolicies.knownOrGrant());
        registerPolicy(EntitlementPolicies.ownedOrGrant());
        registerPolicy(EntitlementPolicies.requirementsOnly());
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public void registerType(EntitlementTypeDefinition type) {
        requireAbsent(types, type.id(), "entitlement type");
        for (Identifier action : type.supportedActionIds()) {
            if (!actions.contains(action)) {
                throw new IllegalStateException("Type " + type.id() + " supports unregistered action " + action);
            }
        }
        types.put(type.id(), type);
    }

    public void registerAction(Identifier actionId) {
        actions.add(actionId);
    }

    public void registerSourceType(Identifier sourceTypeId) {
        sourceTypes.add(sourceTypeId);
    }

    public void registerPolicy(EntitlementAuthorizationPolicy policy) {
        requireAbsent(policies, policy.id(), "authorization policy");
        policies.put(policy.id(), policy);
    }

    public void registerConditionType(EntitlementConditionType<?> type) {
        requireAbsent(conditionTypes, type.id(), "condition type");
        conditionTypes.put(type.id(), type);
    }

    /**
     * Registers a condition definition. An invalid one (unregistered type, rejected configuration,
     * duplicate id) is <em>not</em> registered: any requirement referencing it then fails closed.
     *
     * @return the rejection reason, or empty when registered
     */
    public <C> Optional<String> registerCondition(EntitlementConditionDefinition<C> definition) {
        Optional<String> problem = Optional.empty();
        if (conditions.containsKey(definition.id())) {
            problem = Optional.of("duplicate condition id");
        } else if (conditionTypes.get(definition.type().id()) != definition.type()) {
            problem = Optional.of("condition type " + definition.type().id() + " is not registered");
        } else {
            problem = definition.type().validate(definition.configuration());
        }
        if (problem.isPresent()) {
            Totality.LOGGER.error("[Entitlement] Rejected condition {}: {}", definition.id(), problem.get());
            return problem;
        }
        conditions.put(definition.id(), definition);
        return Optional.empty();
    }

    public void registerDefinition(EntitlementDefinition definition) {
        if (!types.containsKey(definition.key().typeId())) {
            throw new IllegalStateException("Definition " + definition.key() + " uses unregistered type");
        }
        EntitlementDefinition existing = definitions.get(definition.key());
        if (existing != null && !existing.equals(definition)) {
            throw new IllegalStateException("Conflicting duplicate definition for " + definition.key());
        }
        definitions.put(definition.key(), definition);
    }

    public void registerProvider(EntitlementGrantProvider provider) {
        requireAbsent(providers, provider.providerId(), "grant provider");
        for (Identifier sourceType : provider.sourceTypeIds()) {
            if (!sourceTypes.contains(sourceType)) {
                throw new IllegalStateException("Provider " + provider.providerId() + " uses unregistered source type " + sourceType);
            }
        }
        providers.put(provider.providerId(), provider);
    }

    public void registerContributor(EntitlementStateContributor contributor) {
        contributors.add(contributor);
    }

    private static <K> void requireAbsent(Map<K, ?> map, K id, String what) {
        if (map.containsKey(id)) throw new IllegalStateException("Duplicate " + what + ": " + id);
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    public Optional<EntitlementTypeDefinition> type(Identifier typeId) {
        return Optional.ofNullable(types.get(typeId));
    }

    public Collection<EntitlementTypeDefinition> types() {
        return List.copyOf(types.values());
    }

    public boolean isAction(Identifier actionId) {
        return actions.contains(actionId);
    }

    public boolean isSourceType(Identifier sourceTypeId) {
        return sourceTypes.contains(sourceTypeId);
    }

    public Optional<EntitlementAuthorizationPolicy> policy(Identifier policyId) {
        return Optional.ofNullable(policies.get(policyId));
    }

    public Optional<EntitlementConditionDefinition<?>> condition(Identifier conditionId) {
        return Optional.ofNullable(conditions.get(conditionId));
    }

    public Optional<EntitlementDefinition> definition(EntitlementKey key) {
        return Optional.ofNullable(definitions.get(key));
    }

    public Collection<EntitlementDefinition> definitions() {
        return List.copyOf(definitions.values());
    }

    public Optional<EntitlementGrantProvider> provider(Identifier providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }

    public Collection<EntitlementGrantProvider> providers() {
        return List.copyOf(providers.values());
    }

    public List<EntitlementStateContributor> contributors(Identifier typeId) {
        return contributors.stream().filter(c -> c.appliesTo(typeId)).toList();
    }

    /** Content is registered when its type exists and either an explicit definition exists or the
     *  owning content registry (the type's resolver) recognizes the id. */
    public boolean isRegisteredContent(EntitlementKey key) {
        EntitlementTypeDefinition type = types.get(key.typeId());
        if (type == null) return false;
        if (definitions.containsKey(key)) return true;
        Predicate<Identifier> resolver = type.contentResolver();
        return resolver != null && resolver.test(key.contentId());
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /** Cross-registry load validation (canonical §12.4). Returns every problem; empty means valid. */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        for (EntitlementTypeDefinition type : types.values()) {
            if (!policies.containsKey(type.defaultAuthorizationPolicyId())) {
                problems.add(type.id() + ": default policy " + type.defaultAuthorizationPolicyId() + " is not registered");
            }
        }
        for (EntitlementDefinition definition : definitions.values()) {
            String where = definition.key().toString();
            EntitlementTypeDefinition type = types.get(definition.key().typeId());
            definition.authorizationPolicyId().filter(p -> !policies.containsKey(p))
                    .ifPresent(p -> problems.add(where + ": policy " + p + " is not registered"));
            EntitlementRuleSet rules = definition.rules();
            rules.visibility().ifPresent(r -> validateRequirement(where + " visibility", r, problems));
            rules.acquisition().ifPresent(r -> validateRequirement(where + " acquisition", r, problems));
            rules.persistentEligibility().ifPresent(r -> validateRequirement(where + " persistent eligibility", r, problems));
            rules.actionRequirements().forEach((action, requirement) -> {
                if (type != null && !type.supportsAction(action)) {
                    problems.add(where + ": requirement for unsupported action " + action);
                }
                validateRequirement(where + " " + action, requirement, problems);
            });
        }
        return problems;
    }

    private void validateRequirement(String where, EntitlementRequirement requirement, List<String> problems) {
        switch (requirement) {
            case EntitlementRequirement.AllOf allOf -> allOf.children().forEach(c -> validateRequirement(where, c, problems));
            case EntitlementRequirement.AnyOf anyOf -> {
                if (anyOf.children().isEmpty()) problems.add(where + ": empty any_of");
                anyOf.children().forEach(c -> validateRequirement(where, c, problems));
            }
            case EntitlementRequirement.Not not -> {
                if (not.failureTranslationKey().isBlank()) problems.add(where + ": not without a failure message");
                validateRequirement(where, not.child(), problems);
            }
            case EntitlementRequirement.Condition condition -> {
                if (!conditions.containsKey(condition.conditionId())) {
                    problems.add(where + ": unregistered condition " + condition.conditionId() + " (fails closed)");
                }
            }
        }
    }
}

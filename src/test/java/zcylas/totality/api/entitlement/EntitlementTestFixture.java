package zcylas.totality.api.entitlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.entitlement.requirement.DisclosurePolicy;
import zcylas.totality.api.entitlement.requirement.EntitlementConditionDefinition;
import zcylas.totality.api.entitlement.requirement.EntitlementConditionType;
import zcylas.totality.api.entitlement.requirement.EntitlementDependencyKey;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An isolated catalog + engine + player state for pure unit tests (no running server). Test content types
 * use namespaced ids under {@code test:}; a mutable flag set stands in for external game state read by the
 * {@code test:flag} condition type.
 */
final class EntitlementTestFixture {

    static final Identifier ABILITY = id("ability");
    static final Identifier SPELL = id("spell");
    static final Identifier APP = id("app");
    static final Identifier CLASS_SOURCE = id("class");
    static final Identifier ORIGIN_SOURCE = id("origin");
    static final Identifier ITEM_SOURCE = id("item");
    static final Identifier QUEST_SOURCE = id("quest");
    static final Identifier FLAG_KIND = id("flag");

    final Set<Identifier> content = new HashSet<>();
    final Set<String> flags = new HashSet<>();
    final EntitlementCatalog catalog = new EntitlementCatalog();
    final EntitlementEngine engine = new EntitlementEngine(catalog);
    PlayerEntitlementState state = new PlayerEntitlementState();
    EntitlementClock clock = new EntitlementClock(0, 0, 1_000_000L);

    record FlagConfig(String flag) {}

    final EntitlementConditionType<FlagConfig> flagType = new EntitlementConditionType<>() {
        @Override public Identifier id() { return FLAG_KIND; }
        @Override public MapCodec<FlagConfig> codec() { return Codec.STRING.fieldOf("flag").xmap(FlagConfig::new, FlagConfig::flag); }
        @Override public boolean test(FlagConfig c, EntitlementQueryContext context) { return flags.contains(c.flag()); }
        @Override public Set<EntitlementDependencyKey> dependencies(FlagConfig c) { return Set.of(flagDependency(c.flag())); }
        @Override public Optional<String> validate(FlagConfig c) {
            return c.flag().isBlank() ? Optional.of("blank flag") : Optional.empty();
        }
    };

    /** Like {@link #flagType} but with no change signal: decisions reading it are never cached. */
    final EntitlementConditionType<FlagConfig> volatileFlagType = new EntitlementConditionType<>() {
        @Override public Identifier id() { return EntitlementTestFixture.id("volatile_flag"); }
        @Override public MapCodec<FlagConfig> codec() { return flagType.codec(); }
        @Override public boolean test(FlagConfig c, EntitlementQueryContext context) { return flags.contains(c.flag()); }
        @Override public boolean cacheable() { return false; }
    };

    EntitlementTestFixture() {
        for (Identifier source : List.of(CLASS_SOURCE, ORIGIN_SOURCE, ITEM_SOURCE, QUEST_SOURCE)) {
            catalog.registerSourceType(source);
        }
        catalog.registerType(EntitlementTypeDefinition.builder(ABILITY, id("abilities"))
                .actions(EntitlementActions.USE, EntitlementActions.ACTIVATE, EntitlementActions.EQUIP)
                .permanentUnlock().permanentKnown()
                .retention(EntitlementRetentionPolicy.EXPLICIT_PERMANENT_ACQUISITION)
                .trackAvailability()
                .content(content::contains)
                .build());
        catalog.registerType(EntitlementTypeDefinition.builder(SPELL, id("magic"))
                .actions(EntitlementActions.USE, EntitlementActions.ACTIVATE)
                .retention(EntitlementRetentionPolicy.DOMAIN_OWNED)
                .trackAvailability()
                .content(content::contains)
                .build());
        catalog.registerType(EntitlementTypeDefinition.builder(APP, id("phone"))
                .actions(EntitlementActions.OPEN_SERVICE)
                .permanentUnlock()
                .retention(EntitlementRetentionPolicy.EXPLICIT_PERMANENT_ACQUISITION)
                .clientDisplayView()
                .displayAction(EntitlementActions.OPEN_SERVICE)
                .build());
        catalog.registerConditionType(flagType);
        catalog.registerConditionType(volatileFlagType);
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("test", path);
    }

    static EntitlementDependencyKey flagDependency(String flag) {
        return EntitlementDependencyKey.of(FLAG_KIND, id(flag));
    }

    EntitlementKey ability(String path) {
        content.add(id(path));
        return EntitlementKey.of(ABILITY, id(path));
    }

    EntitlementKey spell(String path) {
        content.add(id(path));
        return EntitlementKey.of(SPELL, id(path));
    }

    /** Registers condition {@code test:<flag>} reading {@link #flags}. */
    Identifier condition(String flag, DisclosurePolicy disclosure) {
        Identifier conditionId = id(flag);
        Optional<String> problem = catalog.registerCondition(new EntitlementConditionDefinition<>(conditionId, flagType,
                new FlagConfig(flag), "test.requirement." + flag, disclosure));
        if (problem.isPresent()) throw new IllegalStateException(problem.get());
        return conditionId;
    }

    /** Registers uncacheable condition {@code test:volatile_<flag>} reading {@link #flags}. */
    Identifier volatileCondition(String flag) {
        Identifier conditionId = id("volatile_" + flag);
        catalog.registerCondition(new EntitlementConditionDefinition<>(conditionId, volatileFlagType,
                new FlagConfig(flag), "test.requirement." + flag, DisclosurePolicy.PUBLIC)).ifPresent(p -> {
            throw new IllegalStateException(p);
        });
        return conditionId;
    }

    EntitlementQueryContext context(EntitlementQueryContext.Purpose purpose) {
        return new EntitlementQueryContext(null, purpose, clock);
    }

    EntitlementDecision server(EntitlementKey key, Identifier action) {
        return engine.evaluate(state, key, action, context(EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT));
    }

    EntitlementDecision ui(EntitlementKey key, Identifier action) {
        return engine.evaluate(state, key, action, context(EntitlementQueryContext.Purpose.UI_PREVIEW));
    }

    boolean allowed(EntitlementKey key) {
        return server(key, EntitlementActions.USE).allowed();
    }

    static GrantSourceRef source(Identifier type, String id) {
        return GrantSourceRef.of(type, id(id));
    }

    /** Reconciles provider {@code providerId} to exactly the given (key, source) grants. */
    EntitlementEngine.ReconciliationResult reconcile(String providerId, Set<Identifier> sourceTypes, List<EntitlementGrant> grants) {
        return engine.reconcileProvider(state, id(providerId), sourceTypes, grants);
    }

    static EntitlementGrant providerGrant(String providerId, EntitlementKey key, GrantSourceRef source) {
        return EntitlementGrant.sourceBound(id(providerId), key, source, Set.of(), false);
    }

    EntitlementMutationResult unlock(EntitlementKey key, GrantSourceRef source) {
        return engine.addPermanentFact(state, key, PermanentEntitlementFact.UNLOCKED, source, id("test_method"), true, clock);
    }
}

package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.api.entitlement.EntitlementTestFixture.*;

/** Correction 1: failing grant providers and state contributors can never authorize, and never erase progression. */
class SafeFailureTest {

    private final EntitlementTestFixture f = new EntitlementTestFixture();
    private final EntitlementKey flight = f.ability("flight");
    private final EntitlementKey dash = f.ability("dash");
    private final EntitlementKey legacy = f.ability("legacy_technique");

    /** A provider whose source system can be made to fail on demand. */
    private static final class SwitchableProvider implements EntitlementGrantProvider {
        private final Identifier id;
        private final Identifier sourceType;
        Consumer<Collector> behaviour = c -> {};

        SwitchableProvider(String id, Identifier sourceType) {
            this.id = id(id);
            this.sourceType = sourceType;
        }

        @Override public Identifier providerId() { return id; }
        @Override public Set<Identifier> sourceTypeIds() { return Set.of(sourceType); }
        @Override public void collectGrants(ServerPlayer player, Collector collector) { behaviour.accept(collector); }
    }

    private static Consumer<EntitlementGrantProvider.Collector> grants(EntitlementKey key, GrantSourceRef source) {
        return c -> c.grant(key, source);
    }

    private static final Consumer<EntitlementGrantProvider.Collector> THROWS = c -> {
        throw new IllegalStateException("class component unreadable");
    };

    private EntitlementEngine.ReconciliationResult reconcile(SwitchableProvider provider) {
        return f.engine.reconcile(f.state, provider, null, f.clock);
    }

    @Test
    void failedProviderWithholdsItsPreviousGrantsWithoutErasingThem() {
        SwitchableProvider classes = new SwitchableProvider("classes", CLASS_SOURCE);
        SwitchableProvider items = new SwitchableProvider("items", ITEM_SOURCE);
        classes.behaviour = grants(flight, source(CLASS_SOURCE, "barbarian"));
        items.behaviour = grants(dash, source(ITEM_SOURCE, "boots"));
        reconcile(classes);
        reconcile(items);
        f.unlock(legacy, source(QUEST_SOURCE, "q"));
        assertTrue(f.allowed(flight));

        classes.behaviour = THROWS;
        long before = f.state.revision();
        EntitlementEngine.ReconciliationResult failed = reconcile(classes);
        assertTrue(failed.failure().isPresent());
        assertTrue(f.state.revision() > before, "becoming unverified changes access, so caches are invalidated");

        EntitlementDecision decision = f.server(flight, EntitlementActions.ACTIVATE);
        assertFalse(decision.allowed(), "an unverifiable source must not authorize protected actions");
        assertEquals(EntitlementReasons.SOURCE_UNVERIFIED, decision.primaryReasonCode());
        assertEquals(EntitlementDisplayState.KNOWN_UNAVAILABLE, f.ui(flight, EntitlementActions.USE).displayState());
        assertEquals(1, f.state.providerGrants().get(id("classes")).size(), "the previous grants are kept on record");
        assertEquals(1, f.state.withheldGrants().count());
        assertTrue(f.allowed(dash), "other providers are unaffected");
        assertTrue(f.allowed(legacy), "permanent progression is never touched");
        assertTrue(f.engine.accessible(f.state, ABILITY, EntitlementActions.USE,
                f.context(EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT)).contains(dash));
        assertFalse(f.engine.accessible(f.state, ABILITY, EntitlementActions.USE,
                f.context(EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT)).contains(flight));
    }

    @Test
    void repeatedFailuresAreRecordedWithoutChurnAndRecoveryRestoresAccess() {
        SwitchableProvider classes = new SwitchableProvider("classes", CLASS_SOURCE);
        classes.behaviour = grants(flight, source(CLASS_SOURCE, "barbarian"));
        reconcile(classes);
        classes.behaviour = THROWS;
        reconcile(classes);
        long revision = f.state.revision();
        f.clock = new EntitlementClock(0, 0, 2_000_000L);
        reconcile(classes);
        reconcile(classes);
        PlayerEntitlementState.SourceFailure failure = f.state.failedProviders().get(id("classes"));
        assertEquals(3, failure.count());
        assertEquals(1_000_000L, failure.firstFailureUtc());
        assertEquals(2_000_000L, failure.lastFailureUtc());
        assertTrue(failure.error().contains("class component unreadable"));
        assertEquals(revision, f.state.revision(), "retrying a still-failing provider does not churn revisions");

        classes.behaviour = grants(flight, source(CLASS_SOURCE, "barbarian"));
        EntitlementEngine.ReconciliationResult recovered = reconcile(classes);
        assertTrue(recovered.mutation().changed(), "recovery is a committed change (availability is recomputed)");
        assertTrue(f.state.failedProviders().isEmpty());
        assertTrue(f.allowed(flight));
    }

    @Test
    void recoveryWithDifferentSourceStateAppliesTheFreshProjection() {
        SwitchableProvider classes = new SwitchableProvider("classes", CLASS_SOURCE);
        classes.behaviour = grants(flight, source(CLASS_SOURCE, "barbarian"));
        reconcile(classes);
        classes.behaviour = THROWS;
        reconcile(classes);
        classes.behaviour = grants(dash, source(CLASS_SOURCE, "monk")); // the class changed meanwhile
        reconcile(classes);
        assertFalse(f.allowed(flight), "the stale grant is never revived");
        assertTrue(f.allowed(dash));
    }

    @Test
    void providerThatFailsOnFirstCollectionGrantsNothing() {
        SwitchableProvider classes = new SwitchableProvider("classes", CLASS_SOURCE);
        classes.behaviour = THROWS;
        reconcile(classes);
        assertFalse(f.allowed(flight));
        assertEquals(EntitlementReasons.LOCKED, f.server(flight, EntitlementActions.USE).primaryReasonCode(),
                "nothing was ever held from it, so nothing is reported as unverified");
    }

    @Test
    void failingContributorFailsTheDecisionClosed() {
        EntitlementKey bank = EntitlementKey.of(APP, id("bank"));
        f.catalog.registerDefinition(EntitlementDefinition.of(bank, "bank"));
        f.unlock(bank, source(QUEST_SOURCE, "banking"));
        f.catalog.registerContributor(new EntitlementStateContributor() {
            @Override public Identifier id() { return EntitlementTestFixture.id("story_lockdown"); }
            @Override public boolean appliesTo(Identifier typeId) { return typeId.equals(APP); }
            @Override public void contribute(EntitlementKey key, EntitlementQueryContext context, Collector collector) {
                throw new IllegalStateException("story state unavailable"); // it might have suspended banking
            }
        });

        EntitlementDecision server = f.server(bank, EntitlementActions.OPEN_SERVICE);
        assertFalse(server.allowed(), "a contributor that could suspend access cannot be ignored");
        assertEquals(EntitlementReasons.SOURCE_UNVERIFIED, server.primaryReasonCode());
        assertFalse(server.cacheable(), "the next query retries");
        assertEquals(EntitlementDecision.Kind.HIDDEN, f.ui(bank, EntitlementActions.OPEN_SERVICE).kind(),
                "clients learn nothing from a failed contributor");
        assertTrue(f.engine.displayView(f.state, f.context(EntitlementQueryContext.Purpose.UI_PREVIEW)).isEmpty());
        assertFalse(f.state.ledger().facts(bank).isEmpty(), "the permanent unlock is intact");
        assertEquals(1, f.state.contributorFailures().size());
    }

    @Test
    void failingEnumerationAddsNoCandidates() {
        f.catalog.registerContributor(new EntitlementStateContributor() {
            @Override public Identifier id() { return EntitlementTestFixture.id("broken_spellbook"); }
            @Override public boolean appliesTo(Identifier typeId) { return typeId.equals(SPELL); }
            @Override public void contribute(EntitlementKey key, EntitlementQueryContext context, Collector collector) {}
            @Override public Set<EntitlementKey> enumerate(Identifier typeId, EntitlementQueryContext context) {
                throw new IllegalStateException("spellbook unreadable");
            }
        });
        f.spell("fireball");
        assertTrue(f.engine.accessible(f.state, SPELL, EntitlementActions.USE,
                f.context(EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT)).isEmpty());
        assertEquals(1, f.state.contributorFailures().size());
    }

    @Test
    void sessionGrantsAreNeverWithheldByAnUnrelatedProviderFailure() {
        EntitlementGrant session = EntitlementGrant.debugSession(flight, id("tester"));
        f.engine.addGrant(f.state, session, f.clock);
        SwitchableProvider classes = new SwitchableProvider("classes", CLASS_SOURCE);
        classes.behaviour = THROWS;
        reconcile(classes);
        assertTrue(f.allowed(flight));
        assertEquals(Optional.empty(), f.state.withheldGrants().findAny());
        assertEquals(List.of(session.grantId()), f.engine.activeGrants(f.state, flight, f.clock).stream()
                .map(EntitlementGrant::grantId).toList());
    }
}

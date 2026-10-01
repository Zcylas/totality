package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.entitlement.requirement.DisclosurePolicy;
import zcylas.totality.api.entitlement.requirement.EntitlementDependencyKey;
import zcylas.totality.api.entitlement.requirement.EntitlementRequirement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.api.entitlement.EntitlementTestFixture.*;

/**
 * Correction 2 (domain-owned enumeration) and correction 4c (availability updates without prior cache).
 */
class DomainEnumerationAndInvalidationTest {

    private final EntitlementTestFixture f = new EntitlementTestFixture();

    /** Stands in for a future Spells or Technology API that owns its own knowledge store. */
    private static final class DomainKnowledge implements EntitlementStateContributor {
        static final EntitlementDependencyKey CHANGED = EntitlementDependencyKey.of(EntitlementTestFixture.id("domain_knowledge"));
        final Identifier type;
        final Set<Identifier> learned = new HashSet<>();

        DomainKnowledge(Identifier type) {
            this.type = type;
        }

        @Override public Identifier id() { return EntitlementTestFixture.id("domain_" + type.getPath()); }
        @Override public boolean appliesTo(Identifier typeId) { return typeId.equals(type); }

        @Override
        public void contribute(EntitlementKey key, EntitlementQueryContext context, Collector collector) {
            if (learned.contains(key.contentId())) collector.known();
        }

        @Override
        public Set<EntitlementKey> enumerate(Identifier typeId, EntitlementQueryContext context) {
            Set<EntitlementKey> keys = new HashSet<>();
            for (Identifier id : learned) keys.add(EntitlementKey.of(type, id));
            return keys;
        }

        @Override public Set<EntitlementDependencyKey> dependencies() { return Set.of(CHANGED); }
    }

    private EntitlementQueryContext server() {
        return f.context(EntitlementQueryContext.Purpose.SERVER_ENFORCEMENT);
    }

    @Test
    void technologyLearnedOnlyThroughItsOwnApiIsEnumeratedWithoutLedgerDuplication() {
        Identifier technology = id("technology");
        f.catalog.registerType(EntitlementTypeDefinition.builder(technology, id("technology_api"))
                .actions(EntitlementActions.USE)
                .policy(EntitlementPolicies.KNOWN_OR_GRANT)
                .retention(EntitlementRetentionPolicy.DOMAIN_OWNED)
                .trackAvailability()
                .content(c -> c.getPath().startsWith("tech_"))
                .build());
        DomainKnowledge research = new DomainKnowledge(technology);
        f.catalog.registerContributor(research);
        research.learned.add(id("tech_steam_engine"));

        EntitlementKey steam = EntitlementKey.of(technology, id("tech_steam_engine"));
        Set<EntitlementKey> accessible = f.engine.accessible(f.state, technology, EntitlementActions.USE, server());
        assertEquals(Set.of(steam), accessible);
        assertTrue(f.state.ledger().keysWithFacts().isEmpty(), "no Entitlement-owned permanent fact");
        assertEquals(0, f.state.allGrants().count(), "no active grant");
        EntitlementDecision decision = f.server(steam, EntitlementActions.USE);
        assertTrue(decision.allowed());
        assertTrue(decision.snapshot().known());
        assertEquals(AuthorizationPath.CONTRIBUTOR, decision.successfulPaths().get(0).pathTypeId());

        // Learning more in the owning API is picked up through its dependency key.
        research.learned.add(id("tech_dynamo"));
        assertEquals(1, f.engine.accessible(f.state, technology, EntitlementActions.USE, server()).size(), "cached");
        f.state.invalidate(DomainKnowledge.CHANGED);
        assertEquals(2, f.engine.accessible(f.state, technology, EntitlementActions.USE, server()).size());
    }

    @Test
    void domainOwnedSpellIsEnumeratedAndStillSubjectToRequirements() {
        EntitlementKey fireball = f.spell("fireball");
        DomainKnowledge spellbook = new DomainKnowledge(SPELL);
        f.catalog.registerContributor(spellbook);
        f.catalog.registerDefinition(EntitlementDefinition.of(fireball, "fireball")
                .withPolicy(EntitlementPolicies.KNOWN_OR_GRANT)
                .withRules(EntitlementRuleSet.EMPTY.withAction(EntitlementActions.USE,
                        EntitlementRequirement.condition(f.condition("not_silenced", DisclosurePolicy.PUBLIC)))));
        spellbook.learned.add(fireball.contentId());
        f.state.invalidate(DomainKnowledge.CHANGED);

        assertFalse(f.engine.accessible(f.state, SPELL, EntitlementActions.USE, server()).contains(fireball),
                "enumeration makes it a candidate; the requirement still decides");
        assertEquals(EntitlementReasons.KNOWN_BUT_UNAVAILABLE, f.server(fireball, EntitlementActions.USE).primaryReasonCode());
        f.flags.add("not_silenced");
        f.state.invalidate(flagDependency("not_silenced"));
        assertTrue(f.engine.accessible(f.state, SPELL, EntitlementActions.USE, server()).contains(fireball));
        assertTrue(f.engine.addPermanentFact(f.state, fireball, PermanentEntitlementFact.KNOWN, source(QUEST_SOURCE, "q"),
                id("x"), true, f.clock).isRejected(), "Entitlement still never stores the domain's knowledge");
    }

    @Test
    void availabilityChangeIsReportedEvenWhenNothingWasCached() {
        EntitlementKey gate = f.ability("gate");
        f.catalog.registerDefinition(EntitlementDefinition.of(gate, "gate").withRules(EntitlementRuleSet.EMPTY
                .withAction(EntitlementActions.USE, EntitlementRequirement.condition(f.volatileCondition("lever")))));
        f.unlock(gate, source(QUEST_SOURCE, "q"));
        f.engine.baselineAvailability(f.state, server());
        assertTrue(f.state.accessibleCache.keySet().stream().noneMatch(k -> k.typeId().equals(ABILITY)),
                "an uncacheable decision leaves no cached ability set");

        f.flags.add("lever");
        assertFalse(f.state.invalidate(flagDependency("lever")), "nothing cached to drop...");
        List<EntitlementEngine.AvailabilityChange> changes = f.engine.recomputeAvailability(f.state, server());
        assertEquals(List.of(new EntitlementEngine.AvailabilityChange(gate, EntitlementActions.USE, true)), changes,
                "...but the gain is still reported from current state");

        f.flags.remove("lever");
        assertEquals(List.of(new EntitlementEngine.AvailabilityChange(gate, EntitlementActions.USE, false)),
                f.engine.recomputeAvailability(f.state, server()));
        assertTrue(f.engine.recomputeAvailability(f.state, server()).isEmpty(), "no change, nothing reported");
    }

    @Test
    void nothingIsReportedBeforeTheBaseline() {
        EntitlementKey flight = f.ability("flight");
        f.unlock(flight, source(QUEST_SOURCE, "q"));
        assertTrue(f.engine.recomputeAvailability(f.state, server()).isEmpty(),
                "restoring state on join is not reported as acquisitions");
    }
}

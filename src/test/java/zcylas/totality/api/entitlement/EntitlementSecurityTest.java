package zcylas.totality.api.entitlement;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.api.entitlement.EntitlementTestFixture.*;

class EntitlementSecurityTest {

    private final EntitlementTestFixture f = new EntitlementTestFixture();
    private final EntitlementKey fireball = f.spell("fireball");
    private final EntitlementKey flight = f.ability("flight");
    private final GrantSourceRef universal = GrantSourceRef.of(GrantSourceTypes.DEBUG, id("universal_spell_access"));

    @Test
    void debugAccessIsUsableButNeverProgression() {
        f.reconcile("debug", Set.of(GrantSourceTypes.DEBUG), List.of(providerGrant("debug", fireball, universal)));
        EntitlementDecision decision = f.server(fireball, EntitlementActions.ACTIVATE);
        assertTrue(decision.allowed());
        assertTrue(decision.snapshot().debugOnly());
        assertFalse(decision.successfulPaths().get(0).progressionEligible());

        UUID grantId = f.state.providerGrants().get(id("debug")).keySet().iterator().next();
        assertTrue(f.engine.convertGrantToPermanent(f.state, grantId, PermanentEntitlementFact.KNOWN, id("study"), f.clock).isRejected());
        assertTrue(f.engine.addPermanentFact(f.state, flight, PermanentEntitlementFact.UNLOCKED, universal, id("x"), true, f.clock)
                .isRejected(), "a debug source can never write a permanent fact");
        assertTrue(f.state.ledger().keysWithFacts().isEmpty());

        // Disabling debug access removes it completely.
        f.reconcile("debug", Set.of(GrantSourceTypes.DEBUG), List.of());
        assertFalse(f.allowed(fireball));
    }

    @Test
    void debugGrantsAreForcedNonProgressionAndSessionOnly() {
        EntitlementGrant claimsProgression = new EntitlementGrant(UUID.randomUUID(), flight, universal, id("tools"), Set.of(),
                GrantLifetime.SESSION, Optional.empty(), 0, false, true);
        assertFalse(claimsProgression.progressionEligible(), "debug provenance overrides the flag");
        EntitlementGrant persisted = new EntitlementGrant(UUID.randomUUID(), flight, universal, id("tools"), Set.of(),
                GrantLifetime.UNTIL_EXPLICITLY_REMOVED, Optional.empty(), 0, false, false);
        assertTrue(f.engine.addGrant(f.state, persisted, f.clock).isRejected(), "debug grants are never saved");
        assertTrue(f.engine.addGrant(f.state, EntitlementGrant.debugSession(flight, id("tester")), f.clock).changed());
        assertTrue(f.state.ledger().persistentGrants().isEmpty());
    }

    @Test
    void domainOwnedSpellKnowledgeIsNeverStoredByEntitlement() {
        EntitlementMutationResult result = f.engine.addPermanentFact(f.state, fireball, PermanentEntitlementFact.KNOWN,
                source(QUEST_SOURCE, "q"), id("x"), true, f.clock);
        assertTrue(result.isRejected(), "the spell type stores no permanent knowledge at all");

        // Even where a type could store facts, a DOMAIN_OWNED definition leaves knowledge to its domain.
        EntitlementKey recipe = f.ability("bread_recipe");
        f.catalog.registerDefinition(EntitlementDefinition.of(recipe, "r").withRetention(EntitlementRetentionPolicy.DOMAIN_OWNED));
        EntitlementMutationResult domain = f.engine.addPermanentFact(f.state, recipe, PermanentEntitlementFact.KNOWN,
                source(QUEST_SOURCE, "q"), id("x"), true, f.clock);
        assertTrue(domain.isRejected());
        assertTrue(domain.message().contains("DOMAIN_OWNED"), domain.message());
        assertTrue(f.state.ledger().keysWithFacts().isEmpty());
    }

    @Test
    void sourceBoundRetentionRejectsPermanentFacts() {
        EntitlementKey rage = f.ability("rage");
        f.catalog.registerDefinition(EntitlementDefinition.of(rage, "r").withRetention(EntitlementRetentionPolicy.SOURCE_BOUND));
        assertTrue(f.unlock(rage, source(QUEST_SOURCE, "q")).isRejected());
    }

    @Test
    void providerOnlyLifetimeCannotBeInjectedDirectly() {
        EntitlementGrant forged = EntitlementGrant.sourceBound(id("class_grants"), flight, source(CLASS_SOURCE, "barbarian"),
                Set.of(), false);
        assertTrue(f.engine.addGrant(f.state, forged, f.clock).isRejected());
        assertFalse(f.allowed(flight));
    }

    @Test
    void duplicateGrantIdsAreIdempotentOrRejected() {
        EntitlementGrant grant = new EntitlementGrant(UUID.randomUUID(), flight, source(QUEST_SOURCE, "trial"), id("quests"),
                Set.of(), GrantLifetime.UNTIL_EXPLICITLY_REMOVED, Optional.empty(), 0, false, true);
        assertTrue(f.engine.addGrant(f.state, grant, f.clock).changed());
        long revision = f.state.revision();
        assertEquals(EntitlementMutationResult.Status.NO_CHANGE, f.engine.addGrant(f.state, grant, f.clock).status());
        assertEquals(revision, f.state.revision());
        EntitlementGrant conflicting = new EntitlementGrant(grant.grantId(), f.ability("other"), grant.source(), grant.providerId(),
                Set.of(), grant.lifetime(), Optional.empty(), 0, false, true);
        assertTrue(f.engine.addGrant(f.state, conflicting, f.clock).isRejected());
    }

    @Test
    void permanentAcquisitionIsIdempotentAndRevocationIsAudited() {
        assertTrue(f.unlock(flight, source(QUEST_SOURCE, "first")).changed());
        PermanentFactRecord original = f.state.ledger().facts(flight).get(PermanentEntitlementFact.UNLOCKED);
        assertEquals(EntitlementMutationResult.Status.NO_CHANGE, f.unlock(flight, source(QUEST_SOURCE, "replay")).status(),
                "a replayed reward neither duplicates nor rewrites provenance");
        assertEquals(original, f.state.ledger().facts(flight).get(PermanentEntitlementFact.UNLOCKED));

        EntitlementMutationResult revoked = f.engine.revokePermanentFact(f.state, flight, PermanentEntitlementFact.UNLOCKED,
                GrantSourceRef.of(GrantSourceTypes.ADMIN, id("console")), id("rollback"), f.clock);
        assertTrue(revoked.changed());
        assertTrue(f.state.ledger().audit().stream().anyMatch(e -> e.operation().equals(EntitlementAuditEntry.OP_FACT_REVOKED)
                && e.key().equals(flight) && e.reasonCode().equals(id("rollback"))));
    }

    @Test
    void auditLogIsBounded() {
        for (int i = 0; i < EntitlementLedger.MAX_AUDIT_ENTRIES + 10; i++) {
            EntitlementKey key = f.ability("a" + i);
            f.unlock(key, source(QUEST_SOURCE, "q"));
        }
        assertEquals(EntitlementLedger.MAX_AUDIT_ENTRIES, f.state.ledger().audit().size());
    }

    @Test
    void suspensionOwnershipIsEnforced() {
        f.unlock(flight, source(QUEST_SOURCE, "q"));
        EntitlementSuspension antiMagic = new EntitlementSuspension(UUID.randomUUID(), flight, source(ITEM_SOURCE, "null_field"),
                Set.of(), id("anti_magic"), Optional.empty(), 0, false, false);
        f.engine.addSuspension(f.state, antiMagic, f.clock);
        assertFalse(f.allowed(flight));
        assertTrue(f.engine.removeSuspension(f.state, antiMagic.suspensionId(), source(QUEST_SOURCE, "q"), f.clock).isRejected());
        assertTrue(f.engine.removeSuspension(f.state, antiMagic.suspensionId(), antiMagic.source(), f.clock).changed());
        assertTrue(f.allowed(flight));
        assertTrue(f.state.ledger().suspensions().isEmpty(), "runtime suspensions are not persisted");
    }
}

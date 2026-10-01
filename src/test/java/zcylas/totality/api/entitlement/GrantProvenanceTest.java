package zcylas.totality.api.entitlement;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.api.entitlement.EntitlementTestFixture.*;

class GrantProvenanceTest {

    private final EntitlementTestFixture f = new EntitlementTestFixture();
    private final EntitlementKey flight = f.ability("flight");
    private final GrantSourceRef species = source(ORIGIN_SOURCE, "viltrumite");
    private final GrantSourceRef ring = new GrantSourceRef(ITEM_SOURCE, id("ring_of_flight"),
            Optional.of(UUID.fromString("00000000-0000-0000-0000-000000000001")));
    private final GrantSourceRef form = source(CLASS_SOURCE, "empowered_form");

    private void setAncestry(GrantSourceRef... sources) {
        f.reconcile("ancestry", Set.of(ORIGIN_SOURCE),
                Arrays.stream(sources).map(s -> providerGrant("ancestry", flight, s)).toList());
    }

    private void setEquipment(GrantSourceRef... sources) {
        f.reconcile("equipment", Set.of(ITEM_SOURCE),
                Arrays.stream(sources).map(s -> providerGrant("equipment", flight, s)).toList());
    }

    private void setForm(GrantSourceRef... sources) {
        f.reconcile("forms", Set.of(CLASS_SOURCE),
                Arrays.stream(sources).map(s -> providerGrant("forms", flight, s)).toList());
    }

    @Test
    void threeSourcesCoexistAndRemovingOneKeepsTheOthers() {
        setAncestry(species);
        setEquipment(ring);
        setForm(form);
        EntitlementDecision decision = f.server(flight, EntitlementActions.USE);
        assertTrue(decision.allowed());
        assertEquals(3, decision.successfulPaths().size(), "three independent authorization paths");

        setEquipment();  // unequip the ring
        decision = f.server(flight, EntitlementActions.USE);
        assertTrue(decision.allowed());
        assertEquals(Set.of(species, form), decision.successfulPaths().stream().map(p -> p.source().orElseThrow())
                .collect(Collectors.toSet()));

        setForm();       // transformation ends
        assertTrue(f.allowed(flight));
        setAncestry();   // ancestry changes: final source gone
        EntitlementDecision gone = f.server(flight, EntitlementActions.USE);
        assertFalse(gone.allowed());
        assertEquals(EntitlementReasons.LOCKED, gone.primaryReasonCode());
    }

    @Test
    void reconciliationIsIdempotentAndDeterministic() {
        setAncestry(species);
        long revision = f.state.revision();
        EntitlementEngine.ReconciliationResult again = f.reconcile("ancestry", Set.of(ORIGIN_SOURCE),
                List.of(providerGrant("ancestry", flight, species)));
        assertTrue(again.changes().isEmpty());
        assertEquals(EntitlementMutationResult.Status.NO_CHANGE, again.mutation().status());
        assertEquals(revision, f.state.revision(), "a no-op reconciliation does not bump the revision");
        // Duplicate grants in one collection collapse to one record.
        f.reconcile("ancestry", Set.of(ORIGIN_SOURCE), List.of(providerGrant("ancestry", flight, species),
                providerGrant("ancestry", flight, species)));
        assertEquals(1, f.state.providerGrants().get(id("ancestry")).size());
    }

    @Test
    void oneProviderCannotDeleteOrForgeAnotherProvidersGrants() {
        setAncestry(species);
        setEquipment(ring);
        // The equipment provider reconciling to "nothing" only clears its own grants.
        setEquipment();
        assertEquals(1, f.state.providerGrants().get(id("ancestry")).size());
        assertTrue(f.allowed(flight));

        // A provider emitting a grant stamped with another provider's id, or a source type it did not declare,
        // is rejected and applies nothing.
        EntitlementEngine.ReconciliationResult forged = f.reconcile("equipment", Set.of(ITEM_SOURCE), List.of(
                providerGrant("ancestry", flight, ring),
                providerGrant("equipment", flight, source(ORIGIN_SOURCE, "kryptonian"))));
        assertEquals(2, forged.rejected().size());
        assertTrue(f.state.providerGrants().get(id("equipment")).isEmpty());

        // Explicit removal of a provider grant by id is refused: only its provider's reconciliation removes it.
        UUID ancestryGrant = f.state.providerGrants().get(id("ancestry")).keySet().iterator().next();
        assertTrue(f.engine.removeGrant(f.state, ancestryGrant, species, f.clock).isRejected());
        assertTrue(f.allowed(flight));
    }

    @Test
    void explicitGrantRemovalRequiresTheExactSource() {
        EntitlementGrant quest = new EntitlementGrant(UUID.randomUUID(), flight, source(QUEST_SOURCE, "trial"),
                id("quests"), Set.of(), GrantLifetime.UNTIL_EXPLICITLY_REMOVED, Optional.empty(), 0, false, true);
        assertTrue(f.engine.addGrant(f.state, quest, f.clock).changed());
        assertTrue(f.engine.removeGrant(f.state, quest.grantId(), source(QUEST_SOURCE, "other_quest"), f.clock).isRejected());
        assertTrue(f.allowed(flight));
        assertTrue(f.state.ledger().audit().stream().anyMatch(e -> e.operation().equals(EntitlementAuditEntry.OP_REJECTED)));
        assertTrue(f.engine.removeGrant(f.state, quest.grantId(), quest.source(), f.clock).changed());
        assertFalse(f.allowed(flight));
    }

    @Test
    void permanentProgressionSurvivesTemporarySourceLossAndTemporaryNeverBecomesPermanent() {
        setEquipment(ring);
        // A long-lived temporary grant never turns into a fact by itself.
        assertTrue(f.state.ledger().facts(flight).isEmpty());
        f.unlock(flight, source(QUEST_SOURCE, "sky_trial"));
        setEquipment();
        EntitlementDecision decision = f.server(flight, EntitlementActions.USE);
        assertTrue(decision.allowed());
        assertFalse(decision.snapshot().temporarilyAccessible());
        assertEquals(EntitlementDisplayState.AVAILABLE_PERMANENT, decision.displayState());
    }

    @Test
    void explicitConversionIsTheOnlyWayToPermanence() {
        setEquipment(ring);
        UUID ringGrant = f.state.providerGrants().get(id("equipment")).keySet().iterator().next();
        EntitlementMutationResult converted = f.engine.convertGrantToPermanent(f.state, ringGrant,
                PermanentEntitlementFact.UNLOCKED, id("manual_study"), f.clock);
        assertTrue(converted.changed());
        assertEquals(ring, f.state.ledger().facts(flight).get(PermanentEntitlementFact.UNLOCKED).primarySource());
        setEquipment();
        assertTrue(f.allowed(flight), "the explicitly converted fact remains after the ring is removed");
    }

    @Test
    void grantMultiplicityIsRedundancyNotStrength() {
        setAncestry(species);
        setEquipment(ring);
        EntitlementDecision decision = f.server(flight, EntitlementActions.USE);
        // The decision reports paths and provenance only; it carries no magnitude to stack.
        assertEquals(2, decision.successfulPaths().size());
        assertEquals(2, decision.snapshot().sources().size());
        assertEquals(EntitlementDecision.Kind.ALLOWED, decision.kind());
    }

    @Test
    void sourceBoundAccessIsReportedAsSuchWithProvenance() {
        setAncestry(species);
        EntitlementDecision decision = f.server(flight, EntitlementActions.USE);
        assertTrue(decision.snapshot().temporarilyAccessible());
        assertTrue(decision.snapshot().activelyGranted());
        assertFalse(decision.snapshot().permanentlyUnlocked());
        assertEquals(EntitlementDisplayState.AVAILABLE_SOURCE_BOUND, decision.displayState());
        assertEquals(species, decision.snapshot().sources().get(0).source());
    }

    @Test
    void grantsForUnregisteredContentAreRejected() {
        EntitlementKey ghost = EntitlementKey.of(ABILITY, id("removed_ability"));
        EntitlementEngine.ReconciliationResult result = f.reconcile("ancestry", Set.of(ORIGIN_SOURCE),
                List.of(providerGrant("ancestry", ghost, species)));
        assertEquals(1, result.rejected().size());
        assertFalse(f.allowed(ghost));
    }
}

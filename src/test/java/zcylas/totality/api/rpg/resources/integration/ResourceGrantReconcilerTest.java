package zcylas.totality.api.rpg.resources.integration;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.*;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;
import zcylas.totality.api.rpg.resources.sync.ResourceScalarWireSnapshot;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link ResourceGrantReconciler} — canonical §16.6/§16.7/§16.12 grant lifecycle — using
 * fake {@link ResourceGrantProvider}s (matching this codebase's established "small fake test
 * adapter rather than a mocking framework" precedent, e.g. {@code PlayerResourceServiceTest}'s
 * {@code FakeAdapter}). {@code player} is passed as {@code null} throughout: no fake provider or
 * resolver in this file dereferences it, matching {@code new PlayerChargesComponent(null)}'s own
 * established nullable-player pattern elsewhere in this codebase. 2026-09-15 pre-Phase-4 foundation
 * pass.
 */
class ResourceGrantReconcilerTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private static ResourceGrant grant(Identifier resourceId, Identifier sourceId, int priority) {
        return grant(resourceId, sourceId, priority, ResourceRemovalPolicy.REMOVE_STATE);
    }

    /** Lets a test declare the grant's own removal policy directly — external-review correction,
     *  2026-09-15: {@link ResourceGrantReconciler} now honors the winning grant's own {@code
     *  removalPolicy} field (canonical §16.2) rather than a per-resource registry default, so a test
     *  exercising a non-default policy must set it on the grant itself, not only in a {@link
     *  ResourceGrantPolicyRegistry} entry. */
    private static ResourceGrant grant(Identifier resourceId, Identifier sourceId, int priority, ResourceRemovalPolicy removalPolicy) {
        return new ResourceGrant(resourceId, sourceId, ResourceGrantSourceType.CLASS, ResourceGrantMode.PERSISTENT,
                new ResourceGrantInitialization.AtMaximum(), removalPolicy, ResourceVisibilityPolicy.WHEN_ACTIVE, priority);
    }

    private static ResourceGrantProvider providerOf(ResourceGrant... grants) {
        return player -> List.of(grants);
    }

    /** Builds a reconciler wired to a scalar resource with a fixed authored maximum of 10, one provider, default (SINGLE_OWNER/REMOVE_STATE) policy. */
    private static ResourceGrantReconciler reconcilerWithProvider(Identifier resourceId, long max, ResourceGrantProvider provider) {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(max).build());
        ResourceGrantRegistry grants = new ResourceGrantRegistry();
        grants.register(provider);
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        return new ResourceGrantReconciler(registry, grants, new ResourceGrantPolicyRegistry(), service);
    }

    @Test
    void aGloballyRegisteredResourceWithNoProviderNeverInstantiates() {
        Identifier resourceId = id("test_grant_no_provider");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(10).build());
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        ResourceGrantReconciler reconciler = new ResourceGrantReconciler(registry, new ResourceGrantRegistry(), new ResourceGrantPolicyRegistry(), service);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceGrantReconciler.ReconciliationResult result = reconciler.reconcile(null, state);

        assertTrue(result.instantiated().isEmpty());
        assertFalse(state.hasState(resourceId));
    }

    @Test
    void aValidGrantCausesInstantiation() {
        Identifier resourceId = id("test_grant_valid");
        Identifier source = id("test_source");
        ResourceGrantReconciler reconciler = reconcilerWithProvider(resourceId, 10, providerOf(grant(resourceId, source, 0)));
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceGrantReconciler.ReconciliationResult result = reconciler.reconcile(null, state);

        assertEquals(List.of(resourceId), result.instantiated());
        assertTrue(state.hasState(resourceId));
        assertEquals(10, state.getScalar(resourceId).orElseThrow().currentUnits(), "AtMaximum initialization must start at the resolved maximum");
    }

    // Note: "querying never silently grants" for the GENERIC_COMPONENT query path itself
    // (queryGenericState never calling instantiateScalar) is already directly covered by
    // PlayerResourceServiceTest#queryDoesNotInstantiateGenericState in the main resources package
    // (queryGenericState is package-visible there, not reachable from this .integration package).
    // The grant-specific half of the same invariant — that reconciliation is the ONLY thing that
    // instantiates state, never a side effect of anything else running — is what
    // aGloballyRegisteredResourceWithNoProviderNeverInstantiates above and every other test in this
    // class already establish by construction (state.hasState() stays false until a provider
    // actually grants).

    @Test
    void duplicateOverlappingGrantSourcesDoNotDuplicateState() {
        Identifier resourceId = id("test_grant_duplicate_sources");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(10).build());
        ResourceGrantRegistry grants = new ResourceGrantRegistry();
        grants.register(providerOf(grant(resourceId, id("source_a"), 0), grant(resourceId, id("source_b"), 0)));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        ResourceGrantPolicyRegistry policies = new ResourceGrantPolicyRegistry();
        policies.register(resourceId, new ResourceGrantPolicy(ResourceGrantAggregationPolicy.SHARED_RESOURCE, ResourceRemovalPolicy.REMOVE_STATE));
        ResourceGrantReconciler reconciler = new ResourceGrantReconciler(registry, grants, policies, service);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceGrantReconciler.ReconciliationResult result = reconciler.reconcile(null, state);

        assertEquals(1, result.instantiated().size(), "two overlapping grants for the same resource must produce exactly one instantiation, never a duplicate");
        assertTrue(state.hasState(resourceId));
    }

    @Test
    void removingOneOfMultipleGrantSourcesDoesNotRemoveStateWhileAnotherRemains() {
        Identifier resourceId = id("test_grant_partial_removal");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(10).build());
        ResourceGrantPolicyRegistry policies = new ResourceGrantPolicyRegistry();
        policies.register(resourceId, new ResourceGrantPolicy(ResourceGrantAggregationPolicy.SHARED_RESOURCE, ResourceRemovalPolicy.REMOVE_STATE));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        // First reconciliation: two sources both grant it.
        ResourceGrantRegistry twoSources = new ResourceGrantRegistry();
        twoSources.register(providerOf(grant(resourceId, id("source_a"), 0)));
        twoSources.register(providerOf(grant(resourceId, id("source_b"), 0)));
        new ResourceGrantReconciler(registry, twoSources, policies, service).reconcile(null, state);
        assertTrue(state.hasState(resourceId));
        long instantiatedCurrent = state.getScalar(resourceId).orElseThrow().currentUnits();

        // Second reconciliation: only source_a's provider is registered now (source_b "lost" its grant).
        ResourceGrantRegistry oneSource = new ResourceGrantRegistry();
        oneSource.register(providerOf(grant(resourceId, id("source_a"), 0)));
        ResourceGrantReconciler.ReconciliationResult result = new ResourceGrantReconciler(registry, oneSource, policies, service).reconcile(null, state);

        assertTrue(result.removed().isEmpty(), "state must survive while at least one valid source remains");
        assertTrue(state.hasState(resourceId));
        assertEquals(instantiatedCurrent, state.getScalar(resourceId).orElseThrow().currentUnits(), "surviving state must not be reinitialized/refilled");
    }

    @Test
    void finalGrantRemovalFollowsRemoveStatePolicy() {
        Identifier resourceId = id("test_grant_final_removal");
        ResourceGrantReconciler reconciler = reconcilerWithProvider(resourceId, 10, providerOf(grant(resourceId, id("source"), 0)));
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        reconciler.reconcile(null, state); // grant it first
        assertTrue(state.hasState(resourceId));

        // Re-reconcile with zero providers — the only source is now gone.
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(10).build());
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        ResourceGrantReconciler.ReconciliationResult result =
                new ResourceGrantReconciler(registry, new ResourceGrantRegistry(), new ResourceGrantPolicyRegistry(), service).reconcile(null, state);

        assertEquals(List.of(resourceId), result.removed());
        assertFalse(state.hasState(resourceId), "REMOVE_STATE (the default) must delete state after the final grant disappears");
    }

    @Test
    void finalGrantRemovalWithPreserveDormantLeavesStateInPlaceButMarksItInactive() {
        // External-review correction, 2026-09-15: the removal policy that actually governs is the
        // winning GRANT's own declared policy (canonical §16.2), not a per-resource registry
        // default — set directly on the grant here, not merely in ResourceGrantPolicyRegistry (see
        // the grant() overload's own Javadoc). PRESERVE_DORMANT must also now mark the retained
        // state inactive: it previously stayed fully spendable forever, a real safety gap.
        Identifier resourceId = id("test_grant_preserve_dormant");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(10).build());
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceGrantRegistry withProvider = new ResourceGrantRegistry();
        withProvider.register(providerOf(grant(resourceId, id("source"), 0, ResourceRemovalPolicy.PRESERVE_DORMANT)));
        new ResourceGrantReconciler(registry, withProvider, new ResourceGrantPolicyRegistry(), service).reconcile(null, state);
        assertTrue(state.hasState(resourceId));
        assertTrue(state.isActive(resourceId), "freshly granted state must start active");

        ResourceGrantReconciler.ReconciliationResult result =
                new ResourceGrantReconciler(registry, new ResourceGrantRegistry(), new ResourceGrantPolicyRegistry(), service).reconcile(null, state);

        assertTrue(result.removed().isEmpty(), "PRESERVE_DORMANT must not report a removal");
        assertTrue(state.hasState(resourceId), "PRESERVE_DORMANT must retain state after the final grant disappears");
        assertFalse(state.isActive(resourceId), "PRESERVE_DORMANT must mark the retained state dormant/inactive");
    }

    @Test
    void reacquiringAGrantAfterPreserveDormantReactivatesWithoutRefillingTheValue() {
        Identifier resourceId = id("test_grant_reactivate");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(10).build());
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceGrantRegistry withProvider = new ResourceGrantRegistry();
        withProvider.register(providerOf(grant(resourceId, id("source"), 0, ResourceRemovalPolicy.PRESERVE_DORMANT)));
        new ResourceGrantReconciler(registry, withProvider, new ResourceGrantPolicyRegistry(), service).reconcile(null, state);
        state.getScalar(resourceId).orElseThrow().setCurrentUnits(4); // simulate spend-down before losing the grant

        // Lose the grant — becomes dormant.
        new ResourceGrantReconciler(registry, new ResourceGrantRegistry(), new ResourceGrantPolicyRegistry(), service).reconcile(null, state);
        assertFalse(state.isActive(resourceId));

        // Regain the grant.
        ResourceGrantReconciler.ReconciliationResult result =
                new ResourceGrantReconciler(registry, withProvider, new ResourceGrantPolicyRegistry(), service).reconcile(null, state);

        assertTrue(result.instantiated().isEmpty(), "reactivation is not a fresh instantiation");
        assertTrue(state.isActive(resourceId), "regranting must reactivate dormant state");
        assertEquals(4, state.getScalar(resourceId).orElseThrow().currentUnits(),
                "canonical §16.6: re-evaluating the same grant must not refill it");
    }

    @Test
    void theWinningGrantsOwnRemovalPolicyGovernsEvenWhenTheResourceRegistryDefaultDiffers() {
        // Multiple sources with differing declared removal policies: the SINGLE_OWNER winner
        // (higher priority) is source_a (REMOVE_STATE). Even though the per-resource
        // ResourceGrantPolicyRegistry default is PRESERVE_DORMANT, the winning grant's own policy
        // must govern once it is the only source left — canonical §16.2 declares removal policy per
        // grant, not per resource.
        Identifier resourceId = id("test_grant_differing_policies");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(10).build());
        ResourceGrantPolicyRegistry policies = new ResourceGrantPolicyRegistry();
        policies.register(resourceId, new ResourceGrantPolicy(ResourceGrantAggregationPolicy.SINGLE_OWNER, ResourceRemovalPolicy.PRESERVE_DORMANT));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceGrantRegistry bothSources = new ResourceGrantRegistry();
        bothSources.register(providerOf(
                grant(resourceId, id("source_a"), 5, ResourceRemovalPolicy.REMOVE_STATE),
                grant(resourceId, id("source_b"), 0, ResourceRemovalPolicy.PRESERVE_DORMANT)
        ));
        new ResourceGrantReconciler(registry, bothSources, policies, service).reconcile(null, state);
        assertTrue(state.hasState(resourceId));

        // Both sources disappear at once.
        ResourceGrantReconciler.ReconciliationResult result =
                new ResourceGrantReconciler(registry, new ResourceGrantRegistry(), policies, service).reconcile(null, state);

        assertEquals(List.of(resourceId), result.removed(), "the winning (higher-priority) grant's REMOVE_STATE must govern");
        assertFalse(state.hasState(resourceId));
    }

    @Test
    void reRunningReconciliationWithNoOwnershipChangeIsIdempotent() {
        Identifier resourceId = id("test_grant_idempotent");
        ResourceGrantReconciler reconciler = reconcilerWithProvider(resourceId, 10, providerOf(grant(resourceId, id("source"), 0)));
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceGrantReconciler.ReconciliationResult first = reconciler.reconcile(null, state);
        state.getScalar(resourceId).orElseThrow().setCurrentUnits(3); // simulate the pool having been spent down
        ResourceGrantReconciler.ReconciliationResult second = reconciler.reconcile(null, state);
        ResourceGrantReconciler.ReconciliationResult third = reconciler.reconcile(null, state);

        assertEquals(List.of(resourceId), first.instantiated());
        assertTrue(second.instantiated().isEmpty(), "re-reconciling an already-granted, already-instantiated resource must not re-instantiate it");
        assertTrue(third.instantiated().isEmpty());
        assertEquals(3, state.getScalar(resourceId).orElseThrow().currentUnits(), "must never refill merely because reconciliation ran again");
    }

    @Test
    void singleOwnerAggregationPicksTheHighestPriorityGrantDeterministically() {
        Identifier resourceId = id("test_grant_single_owner_priority");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(10).build());
        ResourceGrantRegistry grants = new ResourceGrantRegistry();
        grants.register(providerOf(
                grant(resourceId, id("low_priority_source"), 0),
                grant(resourceId, id("high_priority_source"), 5)
        ));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        ResourceGrantReconciler reconciler = new ResourceGrantReconciler(registry, grants, new ResourceGrantPolicyRegistry(), service);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceGrantReconciler.ReconciliationResult result = reconciler.reconcile(null, state);

        // SINGLE_OWNER with competing grants must resolve deterministically (not silently sum) —
        // both candidates here use AtMaximum, so the observable proof is that exactly one
        // instantiation happens, not two, and the resulting state is a single, valid pool.
        assertEquals(List.of(resourceId), result.instantiated());
        assertEquals(10, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    @Test
    void unregisteredResourceGrantedByAProviderIsIgnoredRatherThanCrashing() {
        Identifier resourceId = id("test_grant_unregistered_target");
        PlayerResourceRegistry registry = new PlayerResourceRegistry(); // deliberately empty — resourceId is never registered
        ResourceGrantRegistry grants = new ResourceGrantRegistry();
        grants.register(providerOf(grant(resourceId, id("source"), 0)));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        ResourceGrantReconciler reconciler = new ResourceGrantReconciler(registry, grants, new ResourceGrantPolicyRegistry(), service);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertDoesNotThrow(() -> reconciler.reconcile(null, state));
        assertFalse(state.hasState(resourceId));
    }

    @Test
    void aGrantForAnExternalAdapterResourceNeverCreatesGenericComponentState() {
        // Canonical §16.2: "External universal grants expose the resource without creating
        // Generic-component state." Uses totality:health-shaped EXTERNAL_ADAPTER definition.
        Identifier resourceId = id("test_grant_external_adapter");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        ExternalPlayerResourceAdapterRegistry adapters = new ExternalPlayerResourceAdapterRegistry();
        var adapter = new zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapter() {
            @Override public Identifier id() { return resourceId; }
            @Override public ResourceQueryResult snapshot(net.minecraft.world.entity.player.Player player, PlayerResourceDefinition definition) {
                return new ResourceQueryResult.Success(new ResourceSnapshot(resourceId, 5, 10, 1));
            }
            @Override public java.util.Set<zcylas.totality.api.rpg.resources.external.ExternalResourceOperationSupport> supportedOperations() {
                return java.util.Set.of(zcylas.totality.api.rpg.resources.external.ExternalResourceOperationSupport.QUERY);
            }
            @Override public zcylas.totality.api.rpg.resources.external.ExternalResourceClientMirrorMode clientMirrorMode() {
                return zcylas.totality.api.rpg.resources.external.ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION;
            }
        };
        adapters.register(adapter);
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .externalAdapter(resourceId).unitScale(1).authoredBaseMaximum(10).build());
        registry.freeze(adapters);
        ResourceGrantRegistry grants = new ResourceGrantRegistry();
        grants.register(providerOf(grant(resourceId, id("global_system"), 0)));
        PlayerResourceService service = new PlayerResourceService(registry, adapters);
        ResourceGrantReconciler reconciler = new ResourceGrantReconciler(registry, grants, new ResourceGrantPolicyRegistry(), service);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceGrantReconciler.ReconciliationResult result = reconciler.reconcile(null, state);

        assertTrue(result.instantiated().isEmpty(), "an EXTERNAL_ADAPTER grant must be informational only — no state is ever created for it");
        assertFalse(state.hasState(resourceId));
    }

    // ── Grant initialization validation (external-review correction, 2026-09-15) ───────────────

    private static ResourceGrant grantWithInitialization(
            Identifier resourceId, Identifier sourceId, ResourceGrantInitialization initialization) {
        return new ResourceGrant(resourceId, sourceId, ResourceGrantSourceType.CLASS, ResourceGrantMode.PERSISTENT,
                initialization, ResourceRemovalPolicy.REMOVE_STATE, ResourceVisibilityPolicy.WHEN_ACTIVE, 0);
    }

    @Test
    void atAbsoluteTargetingADifferentResourceIdDefersInstantiationRatherThanMisapplyingTheValue() {
        Identifier resourceId = id("test_grant_absolute_wrong_id");
        Identifier wrongId = id("test_grant_absolute_wrong_id_other");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(10).build());
        ResourceGrantRegistry grants = new ResourceGrantRegistry();
        grants.register(providerOf(grantWithInitialization(resourceId, id("source"),
                new ResourceGrantInitialization.AtAbsolute(ResourceAmount.scalar(wrongId, 5)))));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        ResourceGrantReconciler reconciler = new ResourceGrantReconciler(registry, grants, new ResourceGrantPolicyRegistry(), service);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceGrantReconciler.ReconciliationResult result = reconciler.reconcile(null, state);

        assertTrue(result.instantiated().isEmpty());
        assertFalse(state.hasState(resourceId), "a resourceId-mismatched AtAbsolute amount must not instantiate anything");
    }

    @Test
    void atAbsoluteWithANegativeAmountDefersInstantiation() {
        Identifier resourceId = id("test_grant_absolute_negative");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(10).build());
        ResourceGrantRegistry grants = new ResourceGrantRegistry();
        grants.register(providerOf(grantWithInitialization(resourceId, id("source"),
                new ResourceGrantInitialization.AtAbsolute(ResourceAmount.scalar(resourceId, -5)))));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        ResourceGrantReconciler reconciler = new ResourceGrantReconciler(registry, grants, new ResourceGrantPolicyRegistry(), service);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        reconciler.reconcile(null, state);

        assertFalse(state.hasState(resourceId));
    }

    @Test
    void atAbsoluteAboveTheResolvedMaximumDefersInstantiationRatherThanSilentlyClamping() {
        // Documented decision (see the correction report): an out-of-range authored AtAbsolute
        // amount is rejected-by-deferral, not silently clamped — clamping could mask a real
        // authoring bug, and every other unresolvable case in this method already defers with a
        // logged warning rather than guessing at a corrected value.
        Identifier resourceId = id("test_grant_absolute_above_max");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(10).build());
        ResourceGrantRegistry grants = new ResourceGrantRegistry();
        grants.register(providerOf(grantWithInitialization(resourceId, id("source"),
                new ResourceGrantInitialization.AtAbsolute(ResourceAmount.scalar(resourceId, 999)))));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        ResourceGrantReconciler reconciler = new ResourceGrantReconciler(registry, grants, new ResourceGrantPolicyRegistry(), service);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        reconciler.reconcile(null, state);

        assertFalse(state.hasState(resourceId));
    }

    @Test
    void validAtAbsoluteWithinBoundsInstantiatesAtExactlyThatValue() {
        Identifier resourceId = id("test_grant_absolute_valid");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(10).build());
        ResourceGrantRegistry grants = new ResourceGrantRegistry();
        grants.register(providerOf(grantWithInitialization(resourceId, id("source"),
                new ResourceGrantInitialization.AtAbsolute(ResourceAmount.scalar(resourceId, 4)))));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        ResourceGrantReconciler reconciler = new ResourceGrantReconciler(registry, grants, new ResourceGrantPolicyRegistry(), service);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        reconciler.reconcile(null, state);

        assertTrue(state.hasState(resourceId));
        assertEquals(4, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    @Test
    void atFractionAboveOneClampsToTheResolvedMaximumRatherThanOverAllocating() {
        Identifier resourceId = id("test_grant_fraction_above_one");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(10).build());
        ResourceGrantRegistry grants = new ResourceGrantRegistry();
        grants.register(providerOf(grantWithInitialization(resourceId, id("source"),
                new ResourceGrantInitialization.AtFraction(5, 1))));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        ResourceGrantReconciler reconciler = new ResourceGrantReconciler(registry, grants, new ResourceGrantPolicyRegistry(), service);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        reconciler.reconcile(null, state);

        assertTrue(state.hasState(resourceId));
        assertEquals(10, state.getScalar(resourceId).orElseThrow().currentUnits(), "must clamp to the resolved maximum, not seed 5x it");
    }

    // ── Class-change reconciliation regression (2026-09-16) ─────────────────────────────────────
    // Reproduces, generically (a synthetic resource id, not Rage), the production bug that motivated
    // ClassChangeReconciler: a CLASS-owned resource granted at a HIGH resolved maximum, whose class
    // ownership is later lost (e.g. /totality showclass) and then reacquired at a much LOWER resolved
    // maximum (e.g. a fresh level-1 class after previously being level-18). Before the fix, nothing
    // ever re-ran this reconciler on the ownership-loss path, so the old high current value survived
    // into the new low-maximum state and crashed ResourceScalarWireSnapshot's current<=maximum
    // invariant on the next sync. This proves the reconciler itself — once actually invoked on both
    // the loss and the reacquisition — already produces valid, wire-safe state with no Rage-specific
    // logic anywhere in this test or in ResourceGrantReconciler itself.

    @Test
    void reacquiringAClassOwnedGrantAtALowerResolvedMaximumSeedsFreshValidStateNotAStaleHighValue() {
        Identifier resourceId = id("test_grant_reacquire_lower_max");
        Identifier source = id("test_source");
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        // Phase 1: granted at a high resolved maximum (a high-level class).
        ResourceGrantReconciler highLevelReconciler = reconcilerWithProvider(resourceId, 6, providerOf(grant(resourceId, source, 0)));
        highLevelReconciler.reconcile(null, state);
        assertTrue(state.hasState(resourceId));
        assertEquals(6, state.getScalar(resourceId).orElseThrow().currentUnits());

        // Phase 2: class ownership is lost entirely (e.g. /totality showclass resetting
        // PlayerClassComponent) — no provider grants this resource anymore.
        PlayerResourceRegistry registryAfterLoss = new PlayerResourceRegistry();
        registryAfterLoss.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(6).build());
        PlayerResourceService serviceAfterLoss = new PlayerResourceService(registryAfterLoss, new ExternalPlayerResourceAdapterRegistry());
        ResourceGrantReconciler.ReconciliationResult lossResult = new ResourceGrantReconciler(
                registryAfterLoss, new ResourceGrantRegistry(), new ResourceGrantPolicyRegistry(), serviceAfterLoss)
                .reconcile(null, state);
        assertEquals(List.of(resourceId), lossResult.removed(), "REMOVE_STATE must actually remove the stale high-value state");
        assertFalse(state.hasState(resourceId));

        // Phase 3: the class is reacquired at a much LOWER resolved maximum (a fresh level-1 class).
        PlayerResourceRegistry lowLevelRegistry = new PlayerResourceRegistry();
        PlayerResourceDefinition lowLevelDefinition =
                PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(2).build();
        lowLevelRegistry.register(lowLevelDefinition);
        ResourceGrantRegistry lowLevelGrants = new ResourceGrantRegistry();
        lowLevelGrants.register(providerOf(grant(resourceId, source, 0)));
        PlayerResourceService lowLevelService = new PlayerResourceService(lowLevelRegistry, new ExternalPlayerResourceAdapterRegistry());
        ResourceGrantReconciler.ReconciliationResult reacquireResult =
                new ResourceGrantReconciler(lowLevelRegistry, lowLevelGrants, new ResourceGrantPolicyRegistry(), lowLevelService)
                        .reconcile(null, state);

        assertEquals(List.of(resourceId), reacquireResult.instantiated());
        assertEquals(2, state.getScalar(resourceId).orElseThrow().currentUnits(),
                "must seed at the freshly resolved (lower) maximum, never the stale value from the previous, differently-leveled grant");

        // The exact invariant the production crash violated: proves the reconciled state is safe to
        // serialize onto the wire, not merely "some value less than 6."
        long resolvedMax = ((ResourceMaximum.Scalar) lowLevelService
                .resolveMaximum(null, lowLevelDefinition, ResourceResolutionContext.EMPTY).orElseThrow()).effectiveUnits();
        ResourceSnapshot snapshot = new ResourceSnapshot(
                resourceId, state.getScalar(resourceId).orElseThrow().currentUnits(), resolvedMax, lowLevelDefinition.unitScale());
        assertDoesNotThrow(() -> ResourceScalarWireSnapshot.from(snapshot),
                "the reconciled state must never violate the wire invariant this bug crashed on");
    }

    @Test
    void atFractionThatWouldOverflowDefersInstantiationRatherThanWrapping() {
        Identifier resourceId = id("test_grant_fraction_overflow");
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).authoredBaseMaximum(Long.MAX_VALUE).build());
        ResourceGrantRegistry grants = new ResourceGrantRegistry();
        grants.register(providerOf(grantWithInitialization(resourceId, id("source"),
                new ResourceGrantInitialization.AtFraction(Long.MAX_VALUE, 1))));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        ResourceGrantReconciler reconciler = new ResourceGrantReconciler(registry, grants, new ResourceGrantPolicyRegistry(), service);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        assertDoesNotThrow(() -> reconciler.reconcile(null, state));
        assertFalse(state.hasState(resourceId));
    }
}

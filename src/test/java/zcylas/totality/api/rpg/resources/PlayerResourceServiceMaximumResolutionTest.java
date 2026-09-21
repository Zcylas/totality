package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link PlayerResourceService#resolveMaximum} (canonical §10.2's central resolution
 * path) and {@link PlayerResourceService#reconcileMaximum} (§10.2 step 6 / §11.4 maximum-change
 * reconciliation) — 2026-09-15 pre-Phase-4 foundation pass.
 */
class PlayerResourceServiceMaximumResolutionTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private static PlayerResourceService serviceWithResolver(Identifier resourceId, ResourceMaximumResolver resolver) {
        ResourceMaximumResolverRegistry resolvers = new ResourceMaximumResolverRegistry();
        if (resolver != null) resolvers.register(resourceId, resolver);
        return new PlayerResourceService(new PlayerResourceRegistry(), new ExternalPlayerResourceAdapterRegistry(), resolvers);
    }

    // ── Resolution source selection ─────────────────────────────────────────────────────────

    @Test
    void staticAuthoredMaximumIsUsedWhenNoResolverIsRegistered() {
        Identifier resourceId = id("test_max_authored");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .authoredBaseMaximum(80).build();

        Optional<ResourceMaximum> result = serviceWithResolver(resourceId, null).resolveMaximum(null, definition, ResourceResolutionContext.EMPTY);

        assertTrue(result.isPresent());
        assertEquals(80L, ((ResourceMaximum.Scalar) result.get()).effectiveUnits());
    }

    @Test
    void resolverProvidedMaximumIsUsedWhenRegistered() {
        Identifier resourceId = id("test_max_resolver");
        // Deliberately no authoredBaseMaximum — Ki's exact shape (no static value; a resolver is required).
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).build();
        PlayerResourceService service = serviceWithResolver(resourceId, (player, def, context) -> ResourceMaximum.Scalar.of(42));

        Optional<ResourceMaximum> result = service.resolveMaximum(null, definition, ResourceResolutionContext.EMPTY);

        assertTrue(result.isPresent());
        assertEquals(42L, ((ResourceMaximum.Scalar) result.get()).effectiveUnits());
    }

    @Test
    void authoredMaximumTakesPriorityOverARegisteredResolverWhenBothArePresent() {
        // External-review correction, 2026-09-15: canonical §10.2's numbered resolution order
        // ("1. Definition-authored base, if any. 2. Owning strategy's base maximum.") is a literal
        // priority list, not two unordered alternatives — the original foundation pass had a
        // registered resolver win outright over an authored base, the reverse of what §10.2 says.
        // An authored base, when declared, now wins; a resolver is consulted only as the step-2
        // fallback when no authored base exists (see resolverProvidedMaximumIsUsedWhenRegistered).
        Identifier resourceId = id("test_max_authored_priority");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .authoredBaseMaximum(10).build();
        PlayerResourceService service = serviceWithResolver(resourceId, (player, def, context) -> ResourceMaximum.Scalar.of(99));

        Optional<ResourceMaximum> result = service.resolveMaximum(null, definition, ResourceResolutionContext.EMPTY);

        assertEquals(10L, ((ResourceMaximum.Scalar) result.get()).effectiveUnits());
    }

    @Test
    void missingMaximumProducesEmptyRatherThanFabricatingOne() {
        Identifier resourceId = id("test_max_missing");
        // No authoredBaseMaximum, no resolver — exactly Ki's current production shape.
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).build();

        Optional<ResourceMaximum> result = serviceWithResolver(resourceId, null).resolveMaximum(null, definition, ResourceResolutionContext.EMPTY);

        assertTrue(result.isEmpty());
    }

    @Test
    void missingMaximumProducesTheCanonicalQueryFailureRatherThanAFabricatedSnapshot() {
        Identifier resourceId = id("test_max_missing_query");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).build();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 5);

        ResourceQueryResult result = serviceWithResolver(resourceId, null).queryGenericState(null, definition, state);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.MAXIMUM_UNAVAILABLE, ((ResourceQueryResult.Failure) result).reason());
    }

    @Test
    void missingMaximumProducesTheCanonicalMutationFailureRatherThanAFabricatedResult() {
        Identifier resourceId = id("test_max_missing_mutation");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR).build();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 5);

        ResourceOperationResult result = serviceWithResolver(resourceId, null).trySpendGenericState(
                null, definition, new ResourceCost.Scalar(resourceId, 1), ResourceContext.of(ResourceCause.of(id("test"))), state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.MAXIMUM_ZERO, ((ResourceOperationResult.Failure) result).failure().code());
    }

    @Test
    void resolverEffectiveUnitsAreClampedAboveAbsoluteMinimum() {
        Identifier resourceId = id("test_max_resolver_floor");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .absoluteMinimum(10).build();
        // A misbehaving/edge-case resolver returns a maximum below the definition's own floor.
        PlayerResourceService service = serviceWithResolver(resourceId, (player, def, context) -> ResourceMaximum.Scalar.of(2));

        Optional<ResourceMaximum> result = service.resolveMaximum(null, definition, ResourceResolutionContext.EMPTY);

        assertEquals(10L, ((ResourceMaximum.Scalar) result.get()).effectiveUnits(), "must never resolve below absoluteMinimum");
    }

    // ── Reconciliation (§10.2 step 6 / §11.4) ───────────────────────────────────────────────

    @Test
    void maximumIncreaseWithClampCurrentPreservesCurrentUnchanged() {
        Identifier resourceId = id("test_reconcile_increase");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .authoredBaseMaximum(150).build(); // the NEW maximum, as if a level-up just raised it from 100
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 80); // was 80/100 before the change

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(definition);
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceOperationResult result = service.reconcileMaximumGenericState(
                null, resourceId, definition, 100, MaximumChangePolicy.CLAMP_CURRENT,
                ResourceAmount.scalar(resourceId, 0), state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(80, state.getScalar(resourceId).orElseThrow().currentUnits(), "an increase must never auto-refill under CLAMP_CURRENT");
        assertEquals(150, ((ResourceOperationResult.Success) result).after().maximumUnits());
    }

    @Test
    void maximumDecreaseWithClampCurrentClampsDownToTheNewMaximum() {
        Identifier resourceId = id("test_reconcile_decrease");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .authoredBaseMaximum(50).build(); // new, lower maximum
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 80); // was 80/100

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(definition);
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceOperationResult result = service.reconcileMaximumGenericState(
                null, resourceId, definition, 100, MaximumChangePolicy.CLAMP_CURRENT,
                ResourceAmount.scalar(resourceId, 0), state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(50, state.getScalar(resourceId).orElseThrow().currentUnits(), "must clamp down to the new maximum");
    }

    @Test
    void maximumDecreaseWithClampCurrentLeavesStateSafeToSerializeOntoTheWire() {
        // The exact invariant a class-change-triggered maximum decrease must never violate:
        // ResourceScalarWireSnapshot's own currentUnits <= maximumUnits + overflowUnits check — the
        // check that crashed production when a stale current value outlived a class-owned resource's
        // grant and was later reactivated against a lower resolved maximum. This proves the already-
        // existing reconcileMaximum/CLAMP_CURRENT mechanism produces genuinely wire-safe state, not
        // merely a smaller number.
        Identifier resourceId = id("test_reconcile_decrease_wire_safe");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .authoredBaseMaximum(2).build(); // e.g. a fresh level-1 class after previously being much higher
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 6); // stale current carried over at the old, higher maximum

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(definition);
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceOperationResult reconciled = service.reconcileMaximumGenericState(
                null, resourceId, definition, 6, MaximumChangePolicy.CLAMP_CURRENT,
                ResourceAmount.scalar(resourceId, 0), state);
        assertInstanceOf(ResourceOperationResult.Success.class, reconciled);

        ResourceQueryResult queried = service.queryGenericState(null, definition, state);
        assertInstanceOf(ResourceQueryResult.Success.class, queried);
        ResourceSnapshot snapshot = ((ResourceQueryResult.Success) queried).snapshot();
        assertEquals(2, snapshot.currentUnits());
        assertEquals(2, snapshot.maximumUnits());
        assertDoesNotThrow(() -> zcylas.totality.api.rpg.resources.sync.ResourceScalarWireSnapshot.from(snapshot),
                "reconciled state must never violate the wire invariant this exact bug crashed on");
    }

    @Test
    void preserveRatioMaintainsPercentageAcrossAMaximumChange() {
        Identifier resourceId = id("test_reconcile_ratio");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .authoredBaseMaximum(200).build(); // doubled from 100
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50); // 50% of the old 100

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(definition);
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceOperationResult result = service.reconcileMaximumGenericState(
                null, resourceId, definition, 100, MaximumChangePolicy.PRESERVE_RATIO,
                ResourceAmount.scalar(resourceId, 0), state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(100, state.getScalar(resourceId).orElseThrow().currentUnits(), "50% of the new 200 maximum");
    }

    @Test
    void preserveDeficitMaintainsTheMissingAmountAcrossAMaximumChange() {
        Identifier resourceId = id("test_reconcile_deficit");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .authoredBaseMaximum(150).build();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 80); // missing 20 out of the old 100

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(definition);
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceOperationResult result = service.reconcileMaximumGenericState(
                null, resourceId, definition, 100, MaximumChangePolicy.PRESERVE_DEFICIT,
                ResourceAmount.scalar(resourceId, 0), state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(130, state.getScalar(resourceId).orElseThrow().currentUnits(), "150 new maximum minus the preserved 20 deficit");
    }

    @Test
    void allowOverflowMovesExcessIntoOverflowUnitsWhenTheCapabilityIsDeclared() {
        Identifier resourceId = id("test_reconcile_overflow");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .authoredBaseMaximum(50).capability(ResourceCapability.OVERFLOW).build();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 80); // above the new 50 maximum

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(definition);
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceOperationResult result = service.reconcileMaximumGenericState(
                null, resourceId, definition, 100, MaximumChangePolicy.ALLOW_OVERFLOW,
                ResourceAmount.scalar(resourceId, 0), state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(50, state.getScalar(resourceId).orElseThrow().currentUnits());
        assertEquals(30, state.getScalar(resourceId).orElseThrow().overflowUnits(), "the excess 30 must move to overflow, not vanish");
    }

    @Test
    void allowOverflowWithoutTheDeclaredCapabilityFallsBackToClamping() {
        // Canonical §12.4: "No resource may remain invisibly above maximum without the OVERFLOW
        // capability."
        Identifier resourceId = id("test_reconcile_overflow_undeclared");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .authoredBaseMaximum(50).build(); // OVERFLOW capability NOT declared
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 80);

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(definition);
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceOperationResult result = service.reconcileMaximumGenericState(
                null, resourceId, definition, 100, MaximumChangePolicy.ALLOW_OVERFLOW,
                ResourceAmount.scalar(resourceId, 0), state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(50, state.getScalar(resourceId).orElseThrow().currentUnits());
        assertEquals(0, state.getScalar(resourceId).orElseThrow().overflowUnits());
    }

    @Test
    void noAutomaticRefillOccursMerelyFromReconcilingAnUnchangedMaximum() {
        Identifier resourceId = id("test_reconcile_no_refill");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .authoredBaseMaximum(100).build(); // same as "before"
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 40);

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(definition);
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        service.reconcileMaximumGenericState(
                null, resourceId, definition, 100, MaximumChangePolicy.CLAMP_CURRENT,
                ResourceAmount.scalar(resourceId, 0), state);
        // Re-running with the same "previous" value must remain a no-op — mirrors canonical
        // §16.12's "must be idempotent" requirement applied to maximum reconciliation specifically.
        service.reconcileMaximumGenericState(
                null, resourceId, definition, 100, MaximumChangePolicy.CLAMP_CURRENT,
                ResourceAmount.scalar(resourceId, 0), state);

        assertEquals(40, state.getScalar(resourceId).orElseThrow().currentUnits(), "must never refill just because reconciliation ran again");
    }

    @Test
    void reconcileMaximumOnAnUninstantiatedResourceFailsWithResourceNotInstantiated() {
        Identifier resourceId = id("test_reconcile_uninstantiated");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .authoredBaseMaximum(100).build();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(definition);
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceOperationResult result = service.reconcileMaximumGenericState(
                null, resourceId, definition, 100, MaximumChangePolicy.CLAMP_CURRENT,
                ResourceAmount.scalar(resourceId, 0), state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.RESOURCE_NOT_INSTANTIATED, ((ResourceOperationResult.Failure) result).failure().code());
    }

    // ── PARTITIONED_POOL resolution ─────────────────────────────────────────────────────────

    @Test
    void partitionedResolverMaximumIsUsedCorrectlyPerPartition() {
        Identifier resourceId = id("test_max_partitioned");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.PARTITIONED_POOL).build();
        PlayerResourceService service = serviceWithResolver(resourceId,
                (player, def, context) -> new ResourceMaximum.Partitioned(Map.of(1, 4L, 2, 2L), Map.of(1, 4L, 2, 2L)));

        Optional<ResourceMaximum> result = service.resolveMaximum(null, definition, ResourceResolutionContext.EMPTY);

        assertTrue(result.isPresent());
        ResourceMaximum.Partitioned partitioned = (ResourceMaximum.Partitioned) result.get();
        assertEquals(4L, partitioned.effectiveByPartition().get(1));
        assertEquals(2L, partitioned.effectiveByPartition().get(2));
    }

    @Test
    void partitionedResourceWithoutARegisteredResolverHasNoResolvableMaximum() {
        // Partitioned resources have no single authoredBaseMaximum field to fall back on — a
        // resolver is structurally required for them (unlike scalar resources).
        Identifier resourceId = id("test_max_partitioned_missing");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.PARTITIONED_POOL).build();

        Optional<ResourceMaximum> result = serviceWithResolver(resourceId, null).resolveMaximum(null, definition, ResourceResolutionContext.EMPTY);

        assertTrue(result.isEmpty());
    }

    // ── Partitioned maximum structural safety (external-review correction, 2026-09-15) ─────────

    @Test
    void aResolverReturningANegativeEffectivePartitionValueIsTreatedAsUnresolvable() {
        Identifier resourceId = id("test_max_partitioned_negative_effective");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.PARTITIONED_POOL).build();
        PlayerResourceService service = serviceWithResolver(resourceId,
                (player, def, context) -> new ResourceMaximum.Partitioned(Map.of(1, 4L), Map.of(1, -1L)));

        Optional<ResourceMaximum> result = service.resolveMaximum(null, definition, ResourceResolutionContext.EMPTY);

        assertTrue(result.isEmpty(), "a negative partition maximum must never reach mutation clamping math");
    }

    @Test
    void aResolverReturningANegativeBasePartitionValueIsTreatedAsUnresolvable() {
        Identifier resourceId = id("test_max_partitioned_negative_base");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.PARTITIONED_POOL).build();
        PlayerResourceService service = serviceWithResolver(resourceId,
                (player, def, context) -> new ResourceMaximum.Partitioned(Map.of(1, -4L), Map.of(1, 4L)));

        Optional<ResourceMaximum> result = service.resolveMaximum(null, definition, ResourceResolutionContext.EMPTY);

        assertTrue(result.isEmpty());
    }

    // ── Overflow-safe reconciliation arithmetic (external-review correction, 2026-09-15) ───────

    @Test
    void preserveRatioNearLongMaxValueFailsWithOverflowRatherThanWrapping() {
        Identifier resourceId = id("test_reconcile_ratio_overflow");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .authoredBaseMaximum(Long.MAX_VALUE).build();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, Long.MAX_VALUE / 2);

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(definition);
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceOperationResult result = service.reconcileMaximumGenericState(
                null, resourceId, definition, 2, MaximumChangePolicy.PRESERVE_RATIO,
                ResourceAmount.scalar(resourceId, 0), state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.OVERFLOW_NOT_SUPPORTED, ((ResourceOperationResult.Failure) result).failure().code());
        assertEquals(Long.MAX_VALUE / 2, state.getScalar(resourceId).orElseThrow().currentUnits(), "a rejected overflow must not mutate state");
    }
}

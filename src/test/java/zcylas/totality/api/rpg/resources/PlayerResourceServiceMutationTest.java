package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;
import zcylas.totality.api.rpg.resources.state.PartitionedResourceState;
import zcylas.totality.api.rpg.resources.state.ScalarResourceState;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link PlayerResourceService}'s canonical §12.1 mutation façade
 * ({@code trySpend}/{@code drain}/{@code restore}/{@code set}) against the package-visible
 * {@code *GenericState} cores, matching {@link PlayerResourceServiceTest}'s established pattern of
 * testing {@code GENERIC_COMPONENT} routing directly against a
 * {@code new PlayerResourceStateComponent(null)} without a real {@code ServerPlayer} — 2026-09-15
 * pre-Phase-4 foundation pass. Every test here uses a resource that is neither Mana, Stamina, Rage,
 * nor Spell Slots (all four remain {@code EXTERNAL_ADAPTER}-authority and untouched by this task).
 */
class PlayerResourceServiceMutationTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private static PlayerResourceDefinition scalarDefinition(Identifier resourceId, long min, long max) {
        return PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .absoluteMinimum(min)
                .authoredBaseMaximum(max)
                .build();
    }

    private static PlayerResourceService service() {
        return new PlayerResourceService(new PlayerResourceRegistry(), new ExternalPlayerResourceAdapterRegistry());
    }

    // ── trySpend ─────────────────────────────────────────────────────────────────────────────

    @Test
    void successfulSpendReducesCurrentByExactAmount() {
        Identifier resourceId = id("test_spend_success");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);

        ResourceOperationResult result = service().trySpendGenericState(
                null, definition, new ResourceCost.Scalar(resourceId, 20), ResourceContext.of(ResourceCause.of(id("test"))), state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        ResourceOperationResult.Success success = (ResourceOperationResult.Success) result;
        assertEquals(50, success.before().currentUnits());
        assertEquals(30, success.after().currentUnits());
        assertEquals(20, success.appliedUnits());
        assertEquals(30, state.getScalar(resourceId).orElseThrow().currentUnits(), "real state must reflect the spend");
    }

    @Test
    void exactCostSpendDrainsToExactlyZero() {
        Identifier resourceId = id("test_spend_exact");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 20);

        ResourceOperationResult result = service().trySpendGenericState(
                null, definition, new ResourceCost.Scalar(resourceId, 20), ResourceContext.of(ResourceCause.of(id("test"))), state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(0, ((ResourceOperationResult.Success) result).after().currentUnits());
    }

    @Test
    void insufficientResourceSpendFailsAtomicallyWithoutMutatingState() {
        Identifier resourceId = id("test_spend_insufficient");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 10);

        ResourceOperationResult result = service().trySpendGenericState(
                null, definition, new ResourceCost.Scalar(resourceId, 20), ResourceContext.of(ResourceCause.of(id("test"))), state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.INSUFFICIENT_RESOURCE, ((ResourceOperationResult.Failure) result).failure().code());
        assertEquals(10, state.getScalar(resourceId).orElseThrow().currentUnits(), "a failed spend must not partially mutate state");
    }

    @Test
    void insufficientResourceFailureReportsRequiredAndAvailableUnits() {
        Identifier resourceId = id("test_spend_insufficient_detail");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 10);

        ResourceOperationResult result = service().trySpendGenericState(
                null, definition, new ResourceCost.Scalar(resourceId, 20), ResourceContext.of(ResourceCause.of(id("test"))), state);

        ResourceFailure failure = ((ResourceOperationResult.Failure) result).failure();
        assertEquals(20L, failure.requiredUnits().orElseThrow());
        assertEquals(10L, failure.availableUnits().orElseThrow());
    }

    @Test
    void spendOnUninstantiatedResourceFailsWithResourceNotInstantiated() {
        Identifier resourceId = id("test_spend_uninstantiated");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceOperationResult result = service().trySpendGenericState(
                null, definition, new ResourceCost.Scalar(resourceId, 5), ResourceContext.of(ResourceCause.of(id("test"))), state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.RESOURCE_NOT_INSTANTIATED, ((ResourceOperationResult.Failure) result).failure().code());
    }

    @Test
    void negativeSpendAmountIsRejectedAsInvalidAmount() {
        Identifier resourceId = id("test_spend_negative");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);

        ResourceOperationResult result = service().trySpendGenericState(
                null, definition, new ResourceCost.Scalar(resourceId, -5), ResourceContext.of(ResourceCause.of(id("test"))), state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.INVALID_AMOUNT, ((ResourceOperationResult.Failure) result).failure().code());
        assertEquals(50, state.getScalar(resourceId).orElseThrow().currentUnits(), "an invalid request must not mutate state");
    }

    @Test
    void spendAgainstAModelMismatchedPartitionedCostFails() {
        Identifier resourceId = id("test_spend_model_mismatch");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);

        ResourceOperationResult result = service().trySpendGenericState(null, definition,
                new ResourceCost.Partitioned(resourceId, 1, 5, PartitionSelectionPolicy.EXACT_TIER),
                ResourceContext.of(ResourceCause.of(id("test"))), state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.MODEL_MISMATCH, ((ResourceOperationResult.Failure) result).failure().code());
    }

    // ── trySpend, partitioned ────────────────────────────────────────────────────────────────

    private static PlayerResourceDefinition partitionedDefinition(Identifier resourceId) {
        return PlayerResourceDefinition.builder(resourceId, ResourceModel.PARTITIONED_POOL).build();
    }

    private static PlayerResourceService partitionedServiceWithResolver(Identifier resourceId, java.util.Map<Integer, Long> max) {
        ResourceMaximumResolverRegistry resolvers = new ResourceMaximumResolverRegistry();
        resolvers.register(resourceId, (player, def, context) -> new ResourceMaximum.Partitioned(max, max));
        return new PlayerResourceService(new PlayerResourceRegistry(), new ExternalPlayerResourceAdapterRegistry(), resolvers);
    }

    @Test
    void partitionedSpendReducesOnlyTheTargetedPartition() {
        Identifier resourceId = id("test_partitioned_spend");
        PlayerResourceDefinition definition = partitionedDefinition(resourceId);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        PartitionedResourceState pool = state.instantiatePartitioned(resourceId);
        pool.setCurrent(1, 2);
        pool.setCurrent(2, 3);

        PlayerResourceService service = partitionedServiceWithResolver(resourceId, java.util.Map.of(1, 2L, 2, 3L));
        ResourceOperationResult result = service.trySpendGenericState(null, definition,
                new ResourceCost.Partitioned(resourceId, 1, 1, PartitionSelectionPolicy.EXACT_TIER),
                ResourceContext.of(ResourceCause.of(id("test"))), state);

        assertInstanceOf(ResourceOperationResult.PartitionedSuccess.class, result);
        assertEquals(1, pool.getCurrent(1), "targeted partition must decrease");
        assertEquals(3, pool.getCurrent(2), "untouched partition must be unaffected");
    }

    @Test
    void partitionedSpendOnANonexistentPartitionFailsWithInvalidTier() {
        Identifier resourceId = id("test_partitioned_invalid_tier");
        PlayerResourceDefinition definition = partitionedDefinition(resourceId);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiatePartitioned(resourceId).setCurrent(1, 2);

        PlayerResourceService service = partitionedServiceWithResolver(resourceId, java.util.Map.of(1, 2L));
        ResourceOperationResult result = service.trySpendGenericState(null, definition,
                new ResourceCost.Partitioned(resourceId, 9, 1, PartitionSelectionPolicy.EXACT_TIER),
                ResourceContext.of(ResourceCause.of(id("test"))), state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.INVALID_TIER, ((ResourceOperationResult.Failure) result).failure().code());
        assertEquals(9, ((ResourceOperationResult.Failure) result).failure().partition().orElseThrow());
    }

    @Test
    void nonExactTierSelectionPolicyIsRejectedThisFoundationPass() {
        // Canonical §14.2: only EXACT_TIER is implemented for ordinary player spending this pass.
        Identifier resourceId = id("test_partitioned_policy_unsupported");
        PlayerResourceDefinition definition = partitionedDefinition(resourceId);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiatePartitioned(resourceId).setCurrent(1, 2);

        PlayerResourceService service = partitionedServiceWithResolver(resourceId, java.util.Map.of(1, 2L));
        ResourceOperationResult result = service.trySpendGenericState(null, definition,
                new ResourceCost.Partitioned(resourceId, 1, 1, PartitionSelectionPolicy.AT_LEAST_TIER),
                ResourceContext.of(ResourceCause.of(id("test"))), state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.OPERATION_UNSUPPORTED_BY_AUTHORITY, ((ResourceOperationResult.Failure) result).failure().code());
    }

    // ── drain ────────────────────────────────────────────────────────────────────────────────

    @Test
    void validDrainReducesCurrentByRequestedAmount() {
        Identifier resourceId = id("test_drain_valid");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);

        ResourceOperationResult result = service().applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(resourceId, 30), -1, state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(20, state.getScalar(resourceId).orElseThrow().currentUnits());
        assertEquals(30, ((ResourceOperationResult.Success) result).appliedUnits());
    }

    @Test
    void drainBelowMinimumClampsRatherThanGoingNegative() {
        Identifier resourceId = id("test_drain_clamp");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 10);

        ResourceOperationResult result = service().applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(resourceId, 999), -1, state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(0, state.getScalar(resourceId).orElseThrow().currentUnits(), "must clamp at absoluteMinimum, not go negative");
        assertEquals(10, ((ResourceOperationResult.Success) result).appliedUnits(), "applied must report the actually-applied amount, not the requested one");
    }

    @Test
    void drainWithANonzeroAbsoluteMinimumClampsThere() {
        Identifier resourceId = id("test_drain_nonzero_floor");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 5, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 10);

        ResourceOperationResult result = service().applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(resourceId, 999), -1, state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(5, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    // ── restore ──────────────────────────────────────────────────────────────────────────────

    @Test
    void restoreBelowMaximumIncreasesCurrentByRequestedAmount() {
        Identifier resourceId = id("test_restore_valid");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);

        ResourceOperationResult result = service().applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(resourceId, 20), +1, state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(70, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    @Test
    void restoreBeyondMaximumClampsWithoutOverflowCapability() {
        Identifier resourceId = id("test_restore_clamp");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 90);

        ResourceOperationResult result = service().applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(resourceId, 999), +1, state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(100, state.getScalar(resourceId).orElseThrow().currentUnits(), "must clamp at maximum — OVERFLOW is not declared");
        assertEquals(0, state.getScalar(resourceId).orElseThrow().overflowUnits());
    }

    @Test
    void negativeAmountUnitsIsRejectedForRestoreAndDrainAlike() {
        Identifier resourceId = id("test_delta_negative");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);

        ResourceOperationResult result = service().applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(resourceId, -5), +1, state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.INVALID_AMOUNT, ((ResourceOperationResult.Failure) result).failure().code());
    }

    // ── set (privileged) ─────────────────────────────────────────────────────────────────────

    @Test
    void setInAPrivilegedMigrationContextSucceeds() {
        Identifier resourceId = id("test_set_privileged");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 10);
        ResourceContext migrationContext = ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.MIGRATION));

        ResourceOperationResult result = service().setGenericState(
                null, definition, ResourceTarget.scalar(resourceId, 77), ResourceAmount.scalar(resourceId, 77), state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(77, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    @Test
    void isPrivilegedCauseAcceptsOnlyTheDeclaredAllowList() {
        // Exercises the real, package-visible PlayerResourceService.isPrivilegedCause directly --
        // the public set()/transact() entry points null-check a real ServerPlayer before this gate
        // even runs, which is not constructible under plain JUnit (the same constraint documented
        // throughout this codebase's existing Resource API tests). setGenericState (tested
        // throughout this file) deliberately skips this gate per its own Javadoc, since set()'s
        // gate already ran by the time it is reached.
        assertTrue(PlayerResourceService.isPrivilegedCause(ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.MIGRATION))));
        assertTrue(PlayerResourceService.isPrivilegedCause(ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.ADMIN_COMMAND))));
        assertFalse(PlayerResourceService.isPrivilegedCause(ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.ABILITY_COST))));
        assertFalse(PlayerResourceService.isPrivilegedCause(ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.SPELL_COST))));
        assertFalse(PlayerResourceService.isPrivilegedCause(
                ResourceContext.of(ResourceCause.of(Identifier.fromNamespaceAndPath("totality", "some_unrelated_cause")))));
    }


    @Test
    void setBeyondMaximumClampsJustLikeRestore() {
        Identifier resourceId = id("test_set_clamp");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 10);

        ResourceOperationResult result = service().setGenericState(
                null, definition, ResourceTarget.scalar(resourceId, 999), ResourceAmount.scalar(resourceId, 999), state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(100, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    @Test
    void setOnUninstantiatedResourceFailsWithResourceNotInstantiated() {
        Identifier resourceId = id("test_set_uninstantiated");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceOperationResult result = service().setGenericState(
                null, definition, ResourceTarget.scalar(resourceId, 50), ResourceAmount.scalar(resourceId, 50), state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.RESOURCE_NOT_INSTANTIATED, ((ResourceOperationResult.Failure) result).failure().code());
    }

    // ── Dormant state safety (external-review correction, 2026-09-15) ──────────────────────────

    @Test
    void spendAgainstDormantScalarStateFailsWithResourceInactive() {
        Identifier resourceId = id("test_spend_dormant");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);
        state.setActive(resourceId, false); // e.g. PRESERVE_DORMANT after the last grant disappeared

        ResourceOperationResult result = service().trySpendGenericState(
                null, definition, new ResourceCost.Scalar(resourceId, 10), ResourceContext.of(ResourceCause.of(id("test"))), state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.RESOURCE_INACTIVE, ((ResourceOperationResult.Failure) result).failure().code());
        assertEquals(50, state.getScalar(resourceId).orElseThrow().currentUnits(), "a rejected dormant spend must not mutate state");
    }

    @Test
    void drainAndRestoreAgainstDormantScalarStateFailWithResourceInactive() {
        Identifier resourceId = id("test_drain_restore_dormant");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);
        state.setActive(resourceId, false);

        ResourceOperationResult drainResult = service().applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(resourceId, 10), -1, state);
        ResourceOperationResult restoreResult = service().applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(resourceId, 10), +1, state);

        assertEquals(ResourceFailureCode.RESOURCE_INACTIVE, ((ResourceOperationResult.Failure) drainResult).failure().code());
        assertEquals(ResourceFailureCode.RESOURCE_INACTIVE, ((ResourceOperationResult.Failure) restoreResult).failure().code());
        assertEquals(50, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    @Test
    void spendAgainstDormantPartitionedStateFailsWithResourceInactive() {
        Identifier resourceId = id("test_spend_dormant_partitioned");
        PlayerResourceDefinition definition = partitionedDefinition(resourceId);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiatePartitioned(resourceId).setCurrent(1, 5);
        state.setActive(resourceId, false);

        PlayerResourceService service = partitionedServiceWithResolver(resourceId, java.util.Map.of(1, 5L));
        ResourceOperationResult result = service.trySpendGenericState(null, definition,
                new ResourceCost.Partitioned(resourceId, 1, 1, PartitionSelectionPolicy.EXACT_TIER),
                ResourceContext.of(ResourceCause.of(id("test"))), state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.RESOURCE_INACTIVE, ((ResourceOperationResult.Failure) result).failure().code());
    }

    @Test
    void setIsUnaffectedByDormancyBecauseItAlreadyRequiresPrivilege() {
        // set() already requires a privileged MIGRATION/ADMIN_COMMAND cause for every call — a
        // strict superset of the dormant-state check — so setGenericState (which runs after that
        // gate) deliberately does not re-check active status; an admin/migration correction must be
        // able to touch dormant state (e.g. to revive or explicitly reset it).
        Identifier resourceId = id("test_set_dormant_allowed");
        PlayerResourceDefinition definition = scalarDefinition(resourceId, 0, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 10);
        state.setActive(resourceId, false);

        ResourceOperationResult result = service().setGenericState(
                null, definition, ResourceTarget.scalar(resourceId, 77), ResourceAmount.scalar(resourceId, 77), state);

        assertInstanceOf(ResourceOperationResult.Success.class, result);
        assertEquals(77, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    // ── Overflow-safe arithmetic (external-review correction, 2026-09-15) ──────────────────────

    @Test
    void restoreNearLongMaxValueFailsWithOverflowRatherThanWrapping() {
        Identifier resourceId = id("test_restore_overflow");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .absoluteMinimum(0).authoredBaseMaximum(Long.MAX_VALUE).build();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, Long.MAX_VALUE - 5);

        ResourceOperationResult result = service().applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(resourceId, 999), +1, state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.OVERFLOW_NOT_SUPPORTED, ((ResourceOperationResult.Failure) result).failure().code());
        assertEquals(Long.MAX_VALUE - 5, state.getScalar(resourceId).orElseThrow().currentUnits(), "a rejected overflow must not mutate state");
    }

    @Test
    void drainNearLongMinValueFailsWithOverflowRatherThanWrapping() {
        Identifier resourceId = id("test_drain_overflow");
        PlayerResourceDefinition definition = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .absoluteMinimum(Long.MIN_VALUE).authoredBaseMaximum(100).build();
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, Long.MIN_VALUE + 5);

        ResourceOperationResult result = service().applyClampedDeltaGenericState(
                null, definition, ResourceAmount.scalar(resourceId, 999), -1, state);

        assertInstanceOf(ResourceOperationResult.Failure.class, result);
        assertEquals(ResourceFailureCode.OVERFLOW_NOT_SUPPORTED, ((ResourceOperationResult.Failure) result).failure().code());
    }

    // ── EXTERNAL_ADAPTER mutation safety (§6 of the task) ───────────────────────────────────
    //
    // The public trySpend/drain/restore/set entry points null-check `player` before routing to the
    // EXTERNAL_ADAPTER-vs-GENERIC_COMPONENT branch, and constructing a real component-attached
    // ServerPlayer is not possible under plain JUnit (the same constraint documented throughout
    // this codebase's existing Resource API tests, e.g. Phase 1's NBT/sync-packet gap and Phase
    // 3A's lifecycle-hook-ordering tests). The EXTERNAL_ADAPTER rejection path itself —
    // dispatchExternalMutation/externalMutationUnsupported checking supportedOperations() before
    // ever invoking an adapter — is exercised for the structurally identical query path by
    // PlayerResourceServiceTest#serviceRejectsQueryWhenAdapterDoesNotDeclareQuerySupport, and is
    // otherwise verified by direct code inspection (see the implementation report).
}

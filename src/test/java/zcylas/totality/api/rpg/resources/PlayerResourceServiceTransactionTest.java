package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link PlayerResourceService#transactGenericState} — canonical §13
 * ({@code ATOMIC_ALL_OR_NOTHING}), tested via the package-visible core (same reasoning as
 * {@link PlayerResourceServiceMutationTest}: the public {@link PlayerResourceService#transact}
 * requires a real component-attached {@code ServerPlayer} only to fetch state, which the core
 * bypasses by taking it as an explicit parameter). 2026-09-15 pre-Phase-4 foundation pass.
 */
class PlayerResourceServiceTransactionTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    private static PlayerResourceDefinition scalarDefinition(Identifier resourceId, long max) {
        return PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .absoluteMinimum(0)
                .authoredBaseMaximum(max)
                .build();
    }

    private static PlayerResourceService service() {
        return new PlayerResourceService(new PlayerResourceRegistry(), new ExternalPlayerResourceAdapterRegistry());
    }

    private static ResourceContext ordinaryContext() {
        return ResourceContext.of(ResourceCause.of(id("test")));
    }

    private static ResourceContext migrationContext() {
        return ResourceContext.of(ResourceCause.of(ResourceContext.CauseTypes.MIGRATION));
    }

    @Test
    void successfulMultiOperationTransactionCommitsAllChanges() {
        Identifier mana = id("test_tx_mana");
        Identifier stamina = id("test_tx_stamina");
        PlayerResourceDefinition manaDef = scalarDefinition(mana, 100);
        PlayerResourceDefinition staminaDef = scalarDefinition(stamina, 100);
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(mana, 50);
        state.instantiateScalar(stamina, 30);

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(manaDef);
        registry.register(staminaDef);
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceTransaction transaction = ResourceTransaction.of(
                new ResourceOperation.Spend(new ResourceCost.Scalar(mana, 20)),
                new ResourceOperation.Spend(new ResourceCost.Scalar(stamina, 10))
        );

        ResourceTransactionResult result = service.transactGenericState(null, transaction, ordinaryContext(), state);

        assertTrue(result.success());
        assertEquals(30, state.getScalar(mana).orElseThrow().currentUnits());
        assertEquals(20, state.getScalar(stamina).orElseThrow().currentUnits());
    }

    @Test
    void oneInvalidOperationFailsTheWholeTransactionWithoutPartialMutation() {
        Identifier mana = id("test_tx_partial_mana");
        Identifier stamina = id("test_tx_partial_stamina");
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(mana, 50);
        state.instantiateScalar(stamina, 5); // not enough for the second operation below

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(scalarDefinition(mana, 100));
        registry.register(scalarDefinition(stamina, 100));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceTransaction transaction = ResourceTransaction.of(
                new ResourceOperation.Spend(new ResourceCost.Scalar(mana, 20)),      // would succeed alone
                new ResourceOperation.Spend(new ResourceCost.Scalar(stamina, 10))    // insufficient — must fail the whole transaction
        );

        ResourceTransactionResult result = service.transactGenericState(null, transaction, ordinaryContext(), state);

        assertFalse(result.success());
        assertEquals(ResourceFailureCode.INSUFFICIENT_RESOURCE, result.failure().orElseThrow().code());
        assertEquals(1, result.failedOperationIndex().orElseThrow(), "must name the failing operation's index");
        assertEquals(50, state.getScalar(mana).orElseThrow().currentUnits(), "the first, individually-valid operation must NOT have committed");
        assertEquals(5, state.getScalar(stamina).orElseThrow().currentUnits());
    }

    @Test
    void twoOperationsTargetingTheSameResourceApplySequentially() {
        Identifier resourceId = id("test_tx_same_resource");
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(scalarDefinition(resourceId, 100));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        // Spend 30 (50 -> 20), then restore 5 (20 -> 25): must compose in submission order, not
        // both be validated independently against the original 50.
        ResourceTransaction transaction = ResourceTransaction.of(
                new ResourceOperation.Spend(new ResourceCost.Scalar(resourceId, 30)),
                new ResourceOperation.Restore(ResourceAmount.scalar(resourceId, 5))
        );

        ResourceTransactionResult result = service.transactGenericState(null, transaction, ordinaryContext(), state);

        assertTrue(result.success());
        assertEquals(25, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    @Test
    void sameResourceSequentialSpendsCorrectlyExhaustThePoolInOrder() {
        // A second determinism case: two spends of the same resource where the FIRST alone would
        // succeed but the SECOND would not survive if both were (incorrectly) validated against the
        // original current rather than the working ledger.
        Identifier resourceId = id("test_tx_same_resource_exhaust");
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 10);

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(scalarDefinition(resourceId, 100));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceTransaction transaction = ResourceTransaction.of(
                new ResourceOperation.Spend(new ResourceCost.Scalar(resourceId, 6)),
                new ResourceOperation.Spend(new ResourceCost.Scalar(resourceId, 6)) // 10-6=4, insufficient for a second 6
        );

        ResourceTransactionResult result = service.transactGenericState(null, transaction, ordinaryContext(), state);

        assertFalse(result.success());
        assertEquals(1, result.failedOperationIndex().orElseThrow());
        assertEquals(10, state.getScalar(resourceId).orElseThrow().currentUnits(), "neither spend may commit — the whole transaction failed");
    }

    @Test
    void transactionContainingAnExternalAdapterResourceFailsBeforeAnyMutation() {
        Identifier generic = id("test_tx_mixed_generic");
        Identifier external = id("test_tx_mixed_external");
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(generic, 50);

        var adapter = new zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapter() {
            @Override public Identifier id() { return external; }
            @Override public ResourceQueryResult snapshot(net.minecraft.world.entity.player.Player player, PlayerResourceDefinition definition) {
                return new ResourceQueryResult.Success(new ResourceSnapshot(external, 5, 10, 1));
            }
            @Override public java.util.Set<zcylas.totality.api.rpg.resources.external.ExternalResourceOperationSupport> supportedOperations() {
                return java.util.Set.of(zcylas.totality.api.rpg.resources.external.ExternalResourceOperationSupport.QUERY);
            }
            @Override public zcylas.totality.api.rpg.resources.external.ExternalResourceClientMirrorMode clientMirrorMode() {
                return zcylas.totality.api.rpg.resources.external.ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION;
            }
        };
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        ExternalPlayerResourceAdapterRegistry adapters = new ExternalPlayerResourceAdapterRegistry();
        adapters.register(adapter);
        registry.register(scalarDefinition(generic, 100));
        registry.register(PlayerResourceDefinition.builder(external, ResourceModel.SCALAR)
                .externalAdapter(external).unitScale(1).authoredBaseMaximum(10).build());
        registry.freeze(adapters);
        PlayerResourceService service = new PlayerResourceService(registry, adapters);

        ResourceTransaction transaction = ResourceTransaction.of(
                new ResourceOperation.Spend(new ResourceCost.Scalar(generic, 10)),
                new ResourceOperation.Drain(ResourceAmount.scalar(external, 1))
        );

        ResourceTransactionResult result = service.transactGenericState(null, transaction, ordinaryContext(), state);

        assertFalse(result.success());
        assertEquals(ResourceFailureCode.ATOMIC_OPERATION_UNSUPPORTED, result.failure().orElseThrow().code());
        assertEquals(50, state.getScalar(generic).orElseThrow().currentUnits(), "the GENERIC_COMPONENT operation must not have committed either");
    }

    @Test
    void aSetOperationInAnOrdinaryContextFailsTheWholeTransactionBeforeAnyValidation() {
        Identifier resourceId = id("test_tx_set_unprivileged");
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(scalarDefinition(resourceId, 100));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceTransaction transaction = ResourceTransaction.of(new ResourceOperation.Set(ResourceTarget.scalar(resourceId, 99)));

        ResourceTransactionResult result = service.transactGenericState(null, transaction, ordinaryContext(), state);

        assertFalse(result.success());
        assertEquals(ResourceFailureCode.BLOCKED_BY_RULE, result.failure().orElseThrow().code());
        assertEquals(50, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    @Test
    void aSetOperationInAPrivilegedContextCommitsAlongsideOtherOperations() {
        Identifier resourceId = id("test_tx_set_privileged");
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(scalarDefinition(resourceId, 100));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceTransaction transaction = ResourceTransaction.of(new ResourceOperation.Set(ResourceTarget.scalar(resourceId, 99)));

        ResourceTransactionResult result = service.transactGenericState(null, transaction, migrationContext(), state);

        assertTrue(result.success());
        assertEquals(99, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    @Test
    void transactionAgainstAnUnknownResourceFailsWithUnknownResource() {
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        PlayerResourceService service = service();

        ResourceTransaction transaction = ResourceTransaction.of(
                new ResourceOperation.Spend(new ResourceCost.Scalar(id("test_tx_unknown"), 1)));

        ResourceTransactionResult result = service.transactGenericState(null, transaction, ordinaryContext(), state);

        assertFalse(result.success());
        assertEquals(ResourceFailureCode.UNKNOWN_RESOURCE, result.failure().orElseThrow().code());
        assertEquals(0, result.failedOperationIndex().orElseThrow());
    }

    // ── Ledger validation parity with the single-operation paths (external-review correction, 2026-09-15) ──

    @Test
    void aSpendOperationCarryingAPartitionedCostFailsWithModelMismatchRatherThanThrowing() {
        // Previously an unchecked (ResourceCost.Scalar) cast — a ClassCastException risk, not a
        // structured failure.
        Identifier resourceId = id("test_tx_spend_partitioned_cost");
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(scalarDefinition(resourceId, 100));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceTransaction transaction = ResourceTransaction.of(
                new ResourceOperation.Spend(new ResourceCost.Partitioned(resourceId, 1, 5, PartitionSelectionPolicy.EXACT_TIER)));

        ResourceTransactionResult result = service.transactGenericState(null, transaction, ordinaryContext(), state);

        assertFalse(result.success());
        assertEquals(ResourceFailureCode.MODEL_MISMATCH, result.failure().orElseThrow().code());
        assertEquals(50, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    @Test
    void aNegativeDrainAmountInsideATransactionIsRejectedRatherThanActingAsARestore() {
        // Previously working = Math.max(floor, working - d.amount().units()) silently inverted a
        // negative Drain into a Restore instead of failing, unlike applyClampedDeltaGenericState's
        // own single-operation validation.
        Identifier resourceId = id("test_tx_negative_drain");
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(scalarDefinition(resourceId, 100));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceTransaction transaction = ResourceTransaction.of(
                new ResourceOperation.Drain(ResourceAmount.scalar(resourceId, -5)));

        ResourceTransactionResult result = service.transactGenericState(null, transaction, ordinaryContext(), state);

        assertFalse(result.success());
        assertEquals(ResourceFailureCode.INVALID_AMOUNT, result.failure().orElseThrow().code());
        assertEquals(50, state.getScalar(resourceId).orElseThrow().currentUnits(), "a rejected negative Drain must not increase current");
    }

    @Test
    void aNegativeRestoreAmountInsideATransactionIsRejected() {
        Identifier resourceId = id("test_tx_negative_restore");
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(scalarDefinition(resourceId, 100));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceTransaction transaction = ResourceTransaction.of(
                new ResourceOperation.Restore(ResourceAmount.scalar(resourceId, -5)));

        ResourceTransactionResult result = service.transactGenericState(null, transaction, ordinaryContext(), state);

        assertFalse(result.success());
        assertEquals(ResourceFailureCode.INVALID_AMOUNT, result.failure().orElseThrow().code());
        assertEquals(50, state.getScalar(resourceId).orElseThrow().currentUnits(), "a rejected negative Restore must not decrease current");
    }

    @Test
    void aDrainOperationThatWouldOverflowFailsAtomicallyWithoutMutatingState() {
        Identifier resourceId = id("test_tx_drain_overflow");
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, Long.MIN_VALUE + 5);
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .absoluteMinimum(Long.MIN_VALUE).authoredBaseMaximum(100).build());
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceTransaction transaction = ResourceTransaction.of(
                new ResourceOperation.Drain(ResourceAmount.scalar(resourceId, 999)));

        ResourceTransactionResult result = service.transactGenericState(null, transaction, ordinaryContext(), state);

        assertFalse(result.success());
        assertEquals(ResourceFailureCode.OVERFLOW_NOT_SUPPORTED, result.failure().orElseThrow().code());
        assertEquals(Long.MIN_VALUE + 5, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    // ── Dormant state inside a transaction (external-review correction, 2026-09-15) ────────────

    @Test
    void anUnprivilegedTransactionAgainstDormantStateFailsWithResourceInactive() {
        Identifier resourceId = id("test_tx_dormant_unprivileged");
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);
        state.setActive(resourceId, false);
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(scalarDefinition(resourceId, 100));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceTransaction transaction = ResourceTransaction.of(
                new ResourceOperation.Spend(new ResourceCost.Scalar(resourceId, 10)));

        ResourceTransactionResult result = service.transactGenericState(null, transaction, ordinaryContext(), state);

        assertFalse(result.success());
        assertEquals(ResourceFailureCode.RESOURCE_INACTIVE, result.failure().orElseThrow().code());
        assertEquals(50, state.getScalar(resourceId).orElseThrow().currentUnits());
    }

    @Test
    void aPrivilegedTransactionMayStillTouchDormantState() {
        // A privileged (Set-containing) transaction already passed the migration/admin gate — it
        // may legitimately touch dormant state as part of the same correction, matching set()'s own
        // authority.
        Identifier resourceId = id("test_tx_dormant_privileged");
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(resourceId, 50);
        state.setActive(resourceId, false);
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(scalarDefinition(resourceId, 100));
        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());

        ResourceTransaction transaction = ResourceTransaction.of(new ResourceOperation.Set(ResourceTarget.scalar(resourceId, 77)));

        ResourceTransactionResult result = service.transactGenericState(null, transaction, migrationContext(), state);

        assertTrue(result.success());
        assertEquals(77, state.getScalar(resourceId).orElseThrow().currentUnits());
    }
}

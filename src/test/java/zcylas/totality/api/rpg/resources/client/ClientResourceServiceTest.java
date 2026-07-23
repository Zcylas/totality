package zcylas.totality.api.rpg.resources.client;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceRegistry;
import zcylas.totality.api.rpg.resources.TestResourceBootstrap;
import zcylas.totality.api.rpg.resources.sync.ResourcePartitionWireEntry;
import zcylas.totality.api.rpg.resources.sync.ResourcePartitionedWireSnapshot;
import zcylas.totality.api.rpg.resources.sync.ResourceScalarWireSnapshot;
import zcylas.totality.api.rpg.resources.sync.ResourceSyncProtocol;
import zcylas.totality.networking.resource.ResourceFullSyncPayload;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests 9-23, 29-31 of the Phase 3B-1 task: the client Resource façade's query semantics against a
 * real {@link PlayerResourceRegistry} (via {@link TestResourceBootstrap}) and a synthetic
 * {@link FakeGenericSyncResourceAccess}, plus reader-registration and API-shape safety checks.
 */
class ClientResourceServiceTest {

    private static ResourceScalarWireSnapshot scalar(Identifier id, long current, long max) {
        return new ResourceScalarWireSnapshot(id, 1L, current, max, 0L);
    }

    private static ResourceScalarWireSnapshot scalarWithScale(Identifier id, long unitScale, long current, long max) {
        return new ResourceScalarWireSnapshot(id, unitScale, current, max, 0L);
    }

    private static ClientResourceService serviceWithGenericReader(FakeGenericSyncResourceAccess access) {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        ClientResourceService service = new ClientResourceService(PlayerResourceRegistry.INSTANCE, new ClientResourceReaderRegistry());
        ClientResourceReader reader = new GenericSyncClientResourceReader(access);
        service.registerReader(PlayerResourceIds.MANA, reader);
        service.registerReader(PlayerResourceIds.SPELL_SLOTS, reader);
        service.registerReader(PlayerResourceIds.RAGE, reader);
        return service;
    }

    // 9. Registered scalar generic Resource query succeeds after full sync.
    @Test
    void registeredScalarGenericResourceSucceedsAfterFullSync() {
        FakeGenericSyncResourceAccess access = new FakeGenericSyncResourceAccess();
        ClientResourceService service = serviceWithGenericReader(access);

        access.state.applyFull(new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(scalar(PlayerResourceIds.MANA, 40, 100)), List.of()));

        ClientResourceQueryResult result = service.query(PlayerResourceIds.MANA);
        assertInstanceOf(ClientResourceQueryResult.Scalar.class, result);
        assertEquals(40L, ((ClientResourceQueryResult.Scalar) result).currentUnits());
    }

    // 10. Registered partitioned generic Resource query succeeds after full sync.
    @Test
    void registeredPartitionedGenericResourceSucceedsAfterFullSync() {
        FakeGenericSyncResourceAccess access = new FakeGenericSyncResourceAccess();
        ClientResourceService service = serviceWithGenericReader(access);

        access.state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(), List.of(
                new ResourcePartitionedWireSnapshot(PlayerResourceIds.SPELL_SLOTS, 1L, List.of(
                        new ResourcePartitionWireEntry(1, 2, 4, 0),
                        new ResourcePartitionWireEntry(2, 1, 3, 0))))));

        ClientResourceQueryResult result = service.query(PlayerResourceIds.SPELL_SLOTS);
        assertInstanceOf(ClientResourceQueryResult.Partitioned.class, result);
        assertEquals(2L, ((ClientResourceQueryResult.Partitioned) result).partition(1).orElseThrow().currentUnits());
    }

    // 11. Generic query before first full returns NOT_SYNCHRONIZED_YET.
    @Test
    void genericQueryBeforeFirstFullReturnsNotSynchronizedYet() {
        ClientResourceService service = serviceWithGenericReader(new FakeGenericSyncResourceAccess());

        ClientResourceQueryResult result = service.query(PlayerResourceIds.MANA);
        assertInstanceOf(ClientResourceQueryResult.Unavailable.class, result);
        assertEquals(ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // 12. Registered but omitted generic Resource returns NOT_AVAILABLE_TO_PLAYER.
    // 20. Missing Rage is NOT_AVAILABLE_TO_PLAYER.
    @Test
    void omittedGenericResourceReturnsNotAvailableToPlayer() {
        FakeGenericSyncResourceAccess access = new FakeGenericSyncResourceAccess();
        ClientResourceService service = serviceWithGenericReader(access);

        access.state.applyFull(new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(scalar(PlayerResourceIds.MANA, 40, 100)), List.of()));

        ClientResourceQueryResult result = service.query(PlayerResourceIds.RAGE);
        assertInstanceOf(ClientResourceQueryResult.Unavailable.class, result);
        assertEquals(ClientResourceUnavailableReason.NOT_AVAILABLE_TO_PLAYER,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // 13. Unknown ID returns RESOURCE_UNREGISTERED.
    @Test
    void unknownIdReturnsResourceUnregistered() {
        ClientResourceService service = serviceWithGenericReader(new FakeGenericSyncResourceAccess());

        Identifier unknown = Identifier.fromNamespaceAndPath("totality", "definitely_not_registered");
        ClientResourceQueryResult result = service.query(unknown);
        assertEquals(ClientResourceUnavailableReason.RESOURCE_UNREGISTERED,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // 14. Registered Resource without a configured client source returns CLIENT_SOURCE_NOT_CONFIGURED.
    @Test
    void registeredResourceWithoutReaderReturnsClientSourceNotConfigured() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        ClientResourceService service = new ClientResourceService(PlayerResourceRegistry.INSTANCE, new ClientResourceReaderRegistry());
        // Breath is registered in PlayerResourceRegistry.INSTANCE, but this fresh service was never
        // given a reader for it.
        ClientResourceQueryResult result = service.query(PlayerResourceIds.BREATH);
        assertEquals(ClientResourceUnavailableReason.CLIENT_SOURCE_NOT_CONFIGURED,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // 15. Typed scalar query against partitioned Resource returns MODEL_MISMATCH.
    @Test
    void typedScalarQueryAgainstPartitionedResourceReturnsModelMismatch() {
        ClientResourceService service = serviceWithGenericReader(new FakeGenericSyncResourceAccess());

        ClientResourceQueryResult result = service.queryScalar(PlayerResourceIds.SPELL_SLOTS);
        assertEquals(ClientResourceUnavailableReason.MODEL_MISMATCH,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // 16. Typed partitioned query against scalar Resource returns MODEL_MISMATCH.
    @Test
    void typedPartitionedQueryAgainstScalarResourceReturnsModelMismatch() {
        ClientResourceService service = serviceWithGenericReader(new FakeGenericSyncResourceAccess());

        ClientResourceQueryResult result = service.queryPartitioned(PlayerResourceIds.MANA);
        assertEquals(ClientResourceUnavailableReason.MODEL_MISMATCH,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // 17. Stored wire shape contradicting the definition returns MODEL_MISMATCH.
    @Test
    void storedShapeContradictingDefinitionReturnsModelMismatch() {
        FakeGenericSyncResourceAccess access = new FakeGenericSyncResourceAccess();
        ClientResourceService service = serviceWithGenericReader(access);

        // MANA's canonical definition is SCALAR, but this synthetic full snapshot stores it as a
        // partitioned entry — a contradiction that must never be silently coerced or flattened.
        access.state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(), List.of(
                new ResourcePartitionedWireSnapshot(PlayerResourceIds.MANA, 1L, List.of(
                        new ResourcePartitionWireEntry(1, 1, 2, 0))))));

        ClientResourceQueryResult result = service.query(PlayerResourceIds.MANA);
        assertEquals(ClientResourceUnavailableReason.MODEL_MISMATCH,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // ── Phase 3B-1 external review correction (2026-07-23): canonical unit-scale validation ───────

    // Correction test 13: scalar generic wire unit-scale mismatch returns MODEL_MISMATCH.
    @Test
    void scalarUnitScaleMismatchReturnsModelMismatch() {
        FakeGenericSyncResourceAccess access = new FakeGenericSyncResourceAccess();
        ClientResourceService service = serviceWithGenericReader(access);

        // totality:mana's canonical unitScale is 1 (ProductionResourceDefinitions); this synthetic
        // full snapshot claims unitScale 2 instead — never converted, reinterpreted, or trusted at
        // either scale.
        access.state.applyFull(new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 1L,
                List.of(scalarWithScale(PlayerResourceIds.MANA, 2L, 40, 100)), List.of()));

        ClientResourceQueryResult result = service.query(PlayerResourceIds.MANA);
        assertEquals(ClientResourceUnavailableReason.MODEL_MISMATCH,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // Correction test 14: partitioned generic wire unit-scale mismatch returns MODEL_MISMATCH.
    @Test
    void partitionedUnitScaleMismatchReturnsModelMismatch() {
        FakeGenericSyncResourceAccess access = new FakeGenericSyncResourceAccess();
        ClientResourceService service = serviceWithGenericReader(access);

        // totality:spell_slots's canonical unitScale is 1 (StandardSpellSlotsResourceAdapter.UNIT_SCALE);
        // this synthetic full snapshot claims unitScale 3 instead.
        access.state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(), List.of(
                new ResourcePartitionedWireSnapshot(PlayerResourceIds.SPELL_SLOTS, 3L, List.of(
                        new ResourcePartitionWireEntry(1, 1, 2, 0))))));

        ClientResourceQueryResult result = service.query(PlayerResourceIds.SPELL_SLOTS);
        assertEquals(ClientResourceUnavailableReason.MODEL_MISMATCH,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }

    // Correction test 15: matching generic unit scale still succeeds.
    @Test
    void matchingUnitScaleStillSucceeds() {
        FakeGenericSyncResourceAccess access = new FakeGenericSyncResourceAccess();
        ClientResourceService service = serviceWithGenericReader(access);

        access.state.applyFull(new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 1L,
                List.of(scalarWithScale(PlayerResourceIds.MANA, 1L, 40, 100)), List.of()));

        ClientResourceQueryResult result = service.query(PlayerResourceIds.MANA);
        assertInstanceOf(ClientResourceQueryResult.Scalar.class, result,
                "a wire snapshot whose unitScale matches the canonical definition must still succeed");
        assertEquals(40L, ((ClientResourceQueryResult.Scalar) result).currentUnits());
    }

    // 18. Pending resync preserves the last value and reports PENDING_RESYNC.
    @Test
    void pendingResyncPreservesLastValueAndReportsPendingResync() {
        FakeGenericSyncResourceAccess access = new FakeGenericSyncResourceAccess();
        ClientResourceService service = serviceWithGenericReader(access);

        access.state.applyFull(new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(scalar(PlayerResourceIds.MANA, 40, 100)), List.of()));
        access.gate.requestIfNotPending();

        ClientResourceQueryResult result = service.query(PlayerResourceIds.MANA);
        ClientResourceQueryResult.Scalar scalar = (ClientResourceQueryResult.Scalar) result;
        assertEquals(40L, scalar.currentUnits(), "the last accepted value must not be blanked while a resync is pending");
        assertEquals(ClientResourceTrust.PENDING_RESYNC, scalar.trust());
    }

    // 19. Fresh synchronized value reports FRESH.
    @Test
    void freshSynchronizedValueReportsFresh() {
        FakeGenericSyncResourceAccess access = new FakeGenericSyncResourceAccess();
        ClientResourceService service = serviceWithGenericReader(access);

        access.state.applyFull(new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(scalar(PlayerResourceIds.MANA, 40, 100)), List.of()));

        ClientResourceQueryResult.Scalar scalar = (ClientResourceQueryResult.Scalar) service.query(PlayerResourceIds.MANA);
        assertEquals(ClientResourceTrust.FRESH, scalar.trust());
    }

    // 21. Present Rage 0/0 is a valid scalar success.
    @Test
    void presentRageZeroZeroIsAValidScalarSuccess() {
        FakeGenericSyncResourceAccess access = new FakeGenericSyncResourceAccess();
        ClientResourceService service = serviceWithGenericReader(access);

        access.state.applyFull(new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(scalar(PlayerResourceIds.RAGE, 0, 0)), List.of()));

        ClientResourceQueryResult result = service.query(PlayerResourceIds.RAGE);
        assertInstanceOf(ClientResourceQueryResult.Scalar.class, result,
                "a present 0/0 Rage pool is a valid success, never NOT_AVAILABLE_TO_PLAYER");
        ClientResourceQueryResult.Scalar scalar = (ClientResourceQueryResult.Scalar) result;
        assertEquals(0L, scalar.currentUnits());
        assertEquals(0L, scalar.maximumUnits());
    }

    // 22. Spell-slot keys remain 1-10 in ascending order.
    @Test
    void spellSlotKeysRemainOneToTenAscending() {
        FakeGenericSyncResourceAccess access = new FakeGenericSyncResourceAccess();
        ClientResourceService service = serviceWithGenericReader(access);

        List<ResourcePartitionWireEntry> entries = new java.util.ArrayList<>();
        for (int level = 10; level >= 1; level--) {
            entries.add(new ResourcePartitionWireEntry(level, 0, 1, 0));
        }
        access.state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(), List.of(
                new ResourcePartitionedWireSnapshot(PlayerResourceIds.SPELL_SLOTS, 1L, entries))));

        ClientResourceQueryResult.Partitioned result =
                (ClientResourceQueryResult.Partitioned) service.query(PlayerResourceIds.SPELL_SLOTS);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), List.copyOf(result.partitions().keySet()));
    }

    // 23. All-zero spell slots remain valid.
    @Test
    void allZeroSpellSlotsRemainValid() {
        FakeGenericSyncResourceAccess access = new FakeGenericSyncResourceAccess();
        ClientResourceService service = serviceWithGenericReader(access);

        List<ResourcePartitionWireEntry> entries = new java.util.ArrayList<>();
        for (int level = 1; level <= 10; level++) {
            entries.add(new ResourcePartitionWireEntry(level, 0, 0, 0));
        }
        access.state.applyFull(new ResourceFullSyncPayload(ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(), List.of(
                new ResourcePartitionedWireSnapshot(PlayerResourceIds.SPELL_SLOTS, 1L, entries))));

        ClientResourceQueryResult result = service.query(PlayerResourceIds.SPELL_SLOTS);
        assertInstanceOf(ClientResourceQueryResult.Partitioned.class, result,
                "an all-zero ten-partition state is a valid non-caster success, not NOT_AVAILABLE_TO_PLAYER");
    }

    // 29. Reader registration rejects duplicate IDs.
    @Test
    void readerRegistrationRejectsDuplicateIds() {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        ClientResourceService service = new ClientResourceService(PlayerResourceRegistry.INSTANCE, new ClientResourceReaderRegistry());
        ClientResourceReader reader = new GenericSyncClientResourceReader(new FakeGenericSyncResourceAccess());

        service.registerReader(PlayerResourceIds.MANA, reader);
        assertThrows(IllegalArgumentException.class, () -> service.registerReader(PlayerResourceIds.MANA, reader));
    }

    // 30. Client service cannot mutate Phase 3A synchronization state.
    @Test
    void genericSyncResourceAccessDeclaresNoMutatingMethod() {
        Set<String> forbidden = Set.of("apply", "clear", "request", "set", "mutate");
        for (Method method : GenericSyncResourceAccess.class.getMethods()) {
            String lower = method.getName().toLowerCase();
            for (String badWord : forbidden) {
                assertFalse(lower.contains(badWord),
                        "GenericSyncResourceAccess must expose no mutating method, found: " + method.getName());
            }
        }
    }

    @Test
    void nativeResourceAccessDeclaresNoMutatingMethod() {
        Set<String> forbidden = Set.of("set", "apply", "clear", "mutate");
        for (Method method : NativeResourceAccess.class.getMethods()) {
            String lower = method.getName().toLowerCase();
            for (String badWord : forbidden) {
                assertFalse(lower.contains(badWord),
                        "NativeResourceAccess must expose no mutating method, found: " + method.getName());
            }
        }
    }

    // 31. Lifecycle clear returns generic queries to NOT_SYNCHRONIZED_YET.
    @Test
    void lifecycleClearReturnsGenericQueriesToNotSynchronizedYet() {
        FakeGenericSyncResourceAccess access = new FakeGenericSyncResourceAccess();
        ClientResourceService service = serviceWithGenericReader(access);

        access.state.applyFull(new ResourceFullSyncPayload(
                ResourceSyncProtocol.PROTOCOL_VERSION, 1L, List.of(scalar(PlayerResourceIds.MANA, 40, 100)), List.of()));
        assertInstanceOf(ClientResourceQueryResult.Scalar.class, service.query(PlayerResourceIds.MANA));

        access.state.clear();

        ClientResourceQueryResult result = service.query(PlayerResourceIds.MANA);
        assertEquals(ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET,
                ((ClientResourceQueryResult.Unavailable) result).reason());
    }
}

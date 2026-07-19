package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapter;
import zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapterRegistry;
import zcylas.totality.api.rpg.resources.external.ExternalResourceClientMirrorMode;
import zcylas.totality.api.rpg.resources.external.ExternalResourceOperationSupport;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link PlayerResourceService}'s query-only routing using a small fake adapter (per the
 * Phase 2A task's explicit "use a small fake test adapter rather than adding a mocking framework")
 * and isolated registries, so it never depends on a real Minecraft {@code Player}/{@code ServerPlayer}
 * or on production {@code INSTANCE} state.
 */
class PlayerResourceServiceTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    /** Deterministic fake adapter; records how many times it was asked for a snapshot. */
    private static final class FakeAdapter implements ExternalPlayerResourceAdapter {
        private final Identifier id;
        private final long current;
        private final long max;
        final AtomicInteger snapshotCalls = new AtomicInteger();

        FakeAdapter(Identifier id, long current, long max) {
            this.id = id;
            this.current = current;
            this.max = max;
        }

        @Override public Identifier id() { return id; }

        @Override public ResourceSnapshot snapshot(Player player, PlayerResourceDefinition definition) {
            snapshotCalls.incrementAndGet();
            return new ResourceSnapshot(definition.id(), current, max, definition.unitScale());
        }

        @Override public Set<ExternalResourceOperationSupport> supportedOperations() {
            return Set.of(ExternalResourceOperationSupport.QUERY);
        }

        @Override public ExternalResourceClientMirrorMode clientMirrorMode() {
            return ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION;
        }
    }

    /** An adapter that always returns a fixed, potentially-malformed {@link ResourceSnapshot} (or {@code null}). */
    private static final class FixedSnapshotAdapter implements ExternalPlayerResourceAdapter {
        private final Identifier id;
        private final ResourceSnapshot fixedSnapshot;
        private final Set<ExternalResourceOperationSupport> support;

        FixedSnapshotAdapter(Identifier id, ResourceSnapshot fixedSnapshot, Set<ExternalResourceOperationSupport> support) {
            this.id = id;
            this.fixedSnapshot = fixedSnapshot;
            this.support = support;
        }

        @Override public Identifier id() { return id; }
        @Override public ResourceSnapshot snapshot(Player player, PlayerResourceDefinition definition) { return fixedSnapshot; }
        @Override public Set<ExternalResourceOperationSupport> supportedOperations() { return support; }
        @Override public ExternalResourceClientMirrorMode clientMirrorMode() { return ExternalResourceClientMirrorMode.NATIVE_SYNCHRONIZATION; }
    }

    private static PlayerResourceService serviceWith(Identifier resourceId, FakeAdapter adapter) {
        return serviceWithDefinitionBuilder(resourceId, adapter,
                builder -> builder.authoredBaseMaximum(100));
    }

    private static PlayerResourceService serviceWithFixedSnapshot(
            Identifier resourceId, ResourceSnapshot fixedSnapshot, long absoluteMinimum) {
        FixedSnapshotAdapter adapter = new FixedSnapshotAdapter(
                resourceId, fixedSnapshot, Set.of(ExternalResourceOperationSupport.QUERY));
        return serviceWithDefinitionBuilder(resourceId, adapter,
                builder -> builder.absoluteMinimum(absoluteMinimum).authoredBaseMaximum(absoluteMinimum + 100));
    }

    private static PlayerResourceService serviceWithDefinitionBuilder(
            Identifier resourceId,
            ExternalPlayerResourceAdapter adapter,
            java.util.function.UnaryOperator<PlayerResourceDefinition.Builder> customize) {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        ExternalPlayerResourceAdapterRegistry adapters = new ExternalPlayerResourceAdapterRegistry();
        adapters.register(adapter);
        PlayerResourceDefinition.Builder builder = PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .externalAdapter(adapter.id())
                .unitScale(1);
        registry.register(customize.apply(builder).build());
        registry.freeze(adapters);
        return new PlayerResourceService(registry, adapters);
    }

    /**
     * Reflection is unavoidable here: {@link ExternalPlayerResourceAdapterRegistry#register}
     * itself now enforces QUERY support at registration time (see the correction pass's
     * "Enforce operation-support declarations" section), so the only way to get a non-QUERY
     * adapter into a registry for {@link PlayerResourceService} to independently, defensively
     * reject is to bypass {@code register()} entirely and place it directly via the private field.
     */
    private static void injectAdapterBypassingRegistryValidation(
            ExternalPlayerResourceAdapterRegistry registry, ExternalPlayerResourceAdapter adapter) {
        try {
            Field adaptersField = ExternalPlayerResourceAdapterRegistry.class.getDeclaredField("adapters");
            adaptersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Identifier, ExternalPlayerResourceAdapter> adapters =
                    (Map<Identifier, ExternalPlayerResourceAdapter>) adaptersField.get(registry);
            adapters.put(adapter.id(), adapter);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inject adapter for test setup", e);
        }
    }

    @Test
    void externalDefinitionsRouteToTheirAdapter() {
        FakeAdapter adapter = new FakeAdapter(id("fake_ext"), 42, 100);
        PlayerResourceService service = serviceWith(id("fake_ext"), adapter);

        // Player argument is never dereferenced by this fake adapter — null is honest here, not a
        // hack: it proves the routing itself, independent of any real Player implementation.
        ResourceQueryResult result = service.query(null, id("fake_ext"));

        assertInstanceOf(ResourceQueryResult.Success.class, result);
        ResourceSnapshot snapshot = ((ResourceQueryResult.Success) result).snapshot();
        assertEquals(42, snapshot.currentUnits());
        assertEquals(100, snapshot.maximumUnits());
        assertEquals(1, adapter.snapshotCalls.get());
    }

    @Test
    void unknownResourceProducesAStructuredFailureNotAnException() {
        PlayerResourceService service = new PlayerResourceService(
                new PlayerResourceRegistry(), new ExternalPlayerResourceAdapterRegistry());

        ResourceQueryResult result = service.query(null, id("nonexistent"));

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        ResourceQueryResult.Failure failure = (ResourceQueryResult.Failure) result;
        assertEquals(ResourceQueryFailureReason.RESOURCE_NOT_REGISTERED, failure.reason());
        assertEquals(id("nonexistent"), failure.resourceId());
    }

    @Test
    void genericDefinitionsDoNotRouteToAnAdapter() {
        // Pure routing core, tested directly against a PlayerResourceStateComponent built with a
        // null ServerPlayer (matching Phase 1's own precedent for testing this component without
        // the Minecraft runtime) — proves GENERIC_COMPONENT never reaches an adapter at all.
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition definition = PlayerResourceDefinition
                .builder(id("generic_scalar"), ResourceModel.SCALAR)
                .authoredBaseMaximum(50)
                .build();
        registry.register(definition);

        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(id("generic_scalar"), 30);

        ResourceQueryResult result = service.queryGenericState(definition, state);

        assertInstanceOf(ResourceQueryResult.Success.class, result);
        ResourceSnapshot snapshot = ((ResourceQueryResult.Success) result).snapshot();
        assertEquals(30, snapshot.currentUnits());
        assertEquals(50, snapshot.maximumUnits());
    }

    @Test
    void queryDoesNotInstantiateGenericState() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition definition = PlayerResourceDefinition
                .builder(id("never_instantiated"), ResourceModel.SCALAR)
                .authoredBaseMaximum(50)
                .build();
        registry.register(definition);

        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceQueryResult result = service.queryGenericState(definition, state);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.STATE_NOT_INSTANTIATED,
                ((ResourceQueryResult.Failure) result).reason());
        // The query itself must never have created state as a side effect.
        assertFalse(state.hasState(id("never_instantiated")));
    }

    @Test
    void queryNeverMutatesAdapterOrGenericState() {
        FakeAdapter adapter = new FakeAdapter(id("immutable_ext"), 7, 10);
        PlayerResourceService service = serviceWith(id("immutable_ext"), adapter);

        service.query(null, id("immutable_ext"));
        service.query(null, id("immutable_ext"));

        // Two reads produced two independent snapshot() calls, not one cached/mutated value —
        // and the fake never exposes a mutation path at all, so there is nothing to mutate.
        assertEquals(2, adapter.snapshotCalls.get());
    }

    @Test
    void partitionedGenericDefinitionsProduceAnExplicitUnsupportedModelFailure() {
        // No Phase 2A resource uses PARTITIONED_POOL, but the routing contract must still fail
        // structured (never crash) rather than pretending to support a shape it doesn't yet.
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition definition = PlayerResourceDefinition
                .builder(id("partitioned"), ResourceModel.PARTITIONED_POOL)
                .build();
        registry.register(definition);

        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);

        ResourceQueryResult result = service.queryGenericState(definition, state);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.UNSUPPORTED_MODEL,
                ((ResourceQueryResult.Failure) result).reason());
    }

    // ── Correction pass: no fabricated generic maximum ──────────────────────────────────────

    @Test
    void genericScalarWithAnAuthoredMaximumSucceeds() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition definition = PlayerResourceDefinition
                .builder(id("has_maximum"), ResourceModel.SCALAR)
                .authoredBaseMaximum(80)
                .build();
        registry.register(definition);

        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(id("has_maximum"), 33);

        ResourceQueryResult result = service.queryGenericState(definition, state);

        assertInstanceOf(ResourceQueryResult.Success.class, result);
        assertEquals(80, ((ResourceQueryResult.Success) result).snapshot().maximumUnits());
    }

    @Test
    void genericScalarWithoutAnAuthoredMaximumFailsStructurallyRatherThanFabricatingOne() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        // Deliberately never calls .authoredBaseMaximum(...) — the builder's default is
        // OptionalLong.empty(), exactly the "no resolvable maximum" case under test.
        PlayerResourceDefinition definition = PlayerResourceDefinition
                .builder(id("no_maximum"), ResourceModel.SCALAR)
                .build();
        registry.register(definition);

        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(id("no_maximum"), 5);

        ResourceQueryResult result = service.queryGenericState(definition, state);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.MAXIMUM_UNAVAILABLE,
                ((ResourceQueryResult.Failure) result).reason());
    }

    @Test
    void noFabricatedZeroZeroSuccessWhenMaximumIsUnavailable() {
        // Regression guard for the exact bug the correction pass fixes: the old fallback
        // (authoredBaseMaximum().orElse(absoluteMinimum())) silently returned a SUCCESSFUL
        // snapshot with maximumUnits() == absoluteMinimum() (commonly 0) instead of failing.
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        PlayerResourceDefinition definition = PlayerResourceDefinition
                .builder(id("no_maximum_regression"), ResourceModel.SCALAR)
                .absoluteMinimum(0)
                .build();
        registry.register(definition);

        PlayerResourceService service = new PlayerResourceService(registry, new ExternalPlayerResourceAdapterRegistry());
        PlayerResourceStateComponent state = new PlayerResourceStateComponent(null);
        state.instantiateScalar(id("no_maximum_regression"), 5);

        ResourceQueryResult result = service.queryGenericState(definition, state);

        assertFalse(result instanceof ResourceQueryResult.Success,
                "must not return a fabricated 0-maximum success");
    }

    // ── Correction pass: adapter snapshot validation ────────────────────────────────────────

    @Test
    void nullAdapterSnapshotProducesCorruptAdapterSnapshotFailure() {
        PlayerResourceService service = serviceWithFixedSnapshot(id("null_snapshot"), null, 0);

        ResourceQueryResult result = service.query(null, id("null_snapshot"));

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT,
                ((ResourceQueryResult.Failure) result).reason());
    }

    @Test
    void mismatchedResourceIdInSnapshotProducesCorruptAdapterSnapshotFailure() {
        Identifier queried = id("mismatched_id");
        ResourceSnapshot wrongId = new ResourceSnapshot(id("a_totally_different_resource"), 10, 50, 1);
        PlayerResourceService service = serviceWithFixedSnapshot(queried, wrongId, 0);

        ResourceQueryResult result = service.query(null, queried);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT,
                ((ResourceQueryResult.Failure) result).reason());
        // Never silently reattributed to a different resource: the failure still names the
        // resource that was actually queried, not the mismatched id the adapter returned.
        assertEquals(queried, ((ResourceQueryResult.Failure) result).resourceId());
    }

    @Test
    void mismatchedUnitScaleInSnapshotProducesCorruptAdapterSnapshotFailure() {
        Identifier queried = id("mismatched_scale");
        // Definition is registered with unitScale=1 (serviceWithFixedSnapshot's default), but the
        // adapter returns a snapshot claiming unitScale=1000.
        ResourceSnapshot wrongScale = new ResourceSnapshot(queried, 10, 50, 1000);
        PlayerResourceService service = serviceWithFixedSnapshot(queried, wrongScale, 0);

        ResourceQueryResult result = service.query(null, queried);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT,
                ((ResourceQueryResult.Failure) result).reason());
    }

    @Test
    void maximumBelowAbsoluteMinimumInSnapshotProducesCorruptAdapterSnapshotFailure() {
        Identifier queried = id("max_below_minimum");
        // Definition's absoluteMinimum will be 10 (see serviceWithFixedSnapshot), but the adapter
        // returns a maximum of 5 — structurally impossible.
        ResourceSnapshot invalidMax = new ResourceSnapshot(queried, 7, 5, 1);
        PlayerResourceService service = serviceWithFixedSnapshot(queried, invalidMax, 10);

        ResourceQueryResult result = service.query(null, queried);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.CORRUPT_ADAPTER_SNAPSHOT,
                ((ResourceQueryResult.Failure) result).reason());
    }

    @Test
    void validSnapshotStillSucceedsAfterValidation() {
        Identifier queried = id("valid_snapshot");
        ResourceSnapshot valid = new ResourceSnapshot(queried, 42, 100, 1);
        PlayerResourceService service = serviceWithFixedSnapshot(queried, valid, 0);

        ResourceQueryResult result = service.query(null, queried);

        assertInstanceOf(ResourceQueryResult.Success.class, result);
        assertEquals(42, ((ResourceQueryResult.Success) result).snapshot().currentUnits());
    }

    // ── Correction pass: operation-support enforcement ──────────────────────────────────────

    @Test
    void serviceRejectsQueryWhenAdapterDoesNotDeclareQuerySupport() {
        Identifier resourceId = id("no_query_support");
        ExternalPlayerResourceAdapter noQueryAdapter = new FixedSnapshotAdapter(
                resourceId, new ResourceSnapshot(resourceId, 1, 2, 1),
                Set.of(ExternalResourceOperationSupport.RESTORE));

        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        ExternalPlayerResourceAdapterRegistry adapters = new ExternalPlayerResourceAdapterRegistry();
        // Bypasses register()'s own QUERY-support enforcement (see
        // injectAdapterBypassingRegistryValidation's Javadoc) so PlayerResourceService's
        // independent, defensive check is what is actually under test here.
        injectAdapterBypassingRegistryValidation(adapters, noQueryAdapter);
        registry.register(PlayerResourceDefinition.builder(resourceId, ResourceModel.SCALAR)
                .externalAdapter(resourceId)
                .unitScale(1)
                .authoredBaseMaximum(10)
                .build());

        PlayerResourceService service = new PlayerResourceService(registry, adapters);
        ResourceQueryResult result = service.query(null, resourceId);

        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        assertEquals(ResourceQueryFailureReason.OPERATION_UNSUPPORTED,
                ((ResourceQueryResult.Failure) result).reason());
    }
}

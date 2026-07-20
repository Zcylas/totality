package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.state.OrphanedResourceState;
import zcylas.totality.api.rpg.resources.state.PartitionedResourceState;
import zcylas.totality.api.rpg.resources.state.ResourceState;
import zcylas.totality.api.rpg.resources.state.ScalarResourceState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises {@link PlayerResourceStateComponent} using {@code new PlayerResourceStateComponent(null)}
 * — the same nullable-player constructor pattern already used for client-side component factories
 * elsewhere in this codebase (e.g. {@code new PlayerChargesComponent(null)}), which lets these
 * tests run without a real {@code ServerPlayer}/world.
 *
 * NBT persistence (writeData/readData) and network sync (writeSyncPacket/applySyncPacket) are
 * NOT exercised here — they require real Mojang ValueInput/ValueOutput/RegistryFriendlyByteBuf
 * instances, which this project has no existing harness to construct outside a running game.
 * That code path is verified by compilation plus the manual smoke-test checklist recorded in the
 * readiness audit, not by an automated round-trip test. See the audit's Stage 2 deviation notes.
 */
class PlayerResourceStateComponentTest {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }

    @Test
    void freshComponentIsEmpty() {
        PlayerResourceStateComponent component = new PlayerResourceStateComponent(null);

        assertTrue(component.instantiatedResourceIds().isEmpty());
        assertTrue(component.orphanedResourceIds().isEmpty());
        // Directly demonstrates writeData's loop bounds (states.size() / orphanedStates.size())
        // would be zero — i.e. no unnecessary resource entries for a player with nothing granted.
    }

    @Test
    void queryingUngrantedResourceDoesNotInstantiateIt() {
        PlayerResourceStateComponent component = new PlayerResourceStateComponent(null);

        assertTrue(component.getScalar(id("test_scalar_a")).isEmpty());
        assertFalse(component.hasState(id("test_scalar_a")));
        // The query itself must not have created state as a side effect.
        assertTrue(component.instantiatedResourceIds().isEmpty());
    }

    @Test
    void definitionRegistrationAloneDoesNotInstantiatePlayerState() {
        PlayerResourceRegistry registry = new PlayerResourceRegistry();
        registry.register(PlayerResourceDefinition.builder(id("ki"), ResourceModel.SCALAR)
                .authoredBaseMaximum(100)
                .build());

        PlayerResourceStateComponent component = new PlayerResourceStateComponent(null);

        // A registered definition exists (in an isolated registry), but nothing granted it to
        // this player, so the component must still have no state for it.
        assertTrue(registry.isRegistered(id("ki")));
        assertFalse(component.hasState(id("ki")));
    }

    @Test
    void instantiateScalarCreatesStateExactlyOnce() {
        PlayerResourceStateComponent component = new PlayerResourceStateComponent(null);

        ScalarResourceState first = component.instantiateScalar(id("test_scalar_a"), 100);
        ScalarResourceState second = component.instantiateScalar(id("test_scalar_a"), 999);

        assertSame(first, second, "instantiating an already-instantiated resource must not reinitialize it");
        assertEquals(100, first.currentUnits(), "the second call's initial value must be ignored");
        assertTrue(component.hasState(id("test_scalar_a")));
    }

    @Test
    void instantiatePartitionedCreatesStateExactlyOnce() {
        PlayerResourceStateComponent component = new PlayerResourceStateComponent(null);

        PartitionedResourceState first = component.instantiatePartitioned(id("test_partitioned_resource"));
        first.setCurrent(1, 4);
        PartitionedResourceState second = component.instantiatePartitioned(id("test_partitioned_resource"));

        assertSame(first, second);
        assertEquals(4, second.getCurrent(1));
    }

    @Test
    void instantiatingWithModelMismatchThrows() {
        PlayerResourceStateComponent component = new PlayerResourceStateComponent(null);
        component.instantiateScalar(id("rage"), 5);

        assertThrows(IllegalStateException.class,
                () -> component.instantiatePartitioned(id("rage")));
    }

    @Test
    void removeStateClearsInstantiation() {
        PlayerResourceStateComponent component = new PlayerResourceStateComponent(null);
        component.instantiateScalar(id("test_scalar_b"), 100);
        assertTrue(component.hasState(id("test_scalar_b")));

        component.removeState(id("test_scalar_b"));
        assertFalse(component.hasState(id("test_scalar_b")));
    }

    @Test
    void copyFromDuplicatesStatesIndependently() {
        PlayerResourceStateComponent source = new PlayerResourceStateComponent(null);
        source.instantiateScalar(id("test_scalar_a"), 80);
        source.instantiatePartitioned(id("test_partitioned_resource")).setCurrent(1, 3);

        PlayerResourceStateComponent target = new PlayerResourceStateComponent(null);
        target.copyFrom(source, null);

        assertEquals(80, target.getScalar(id("test_scalar_a")).orElseThrow().currentUnits());
        assertEquals(3, target.getPartitioned(id("test_partitioned_resource")).orElseThrow().getCurrent(1));

        // Independence: mutating the target must not affect the source (or vice versa).
        target.getScalar(id("test_scalar_a")).orElseThrow().setCurrentUnits(1);
        assertEquals(80, source.getScalar(id("test_scalar_a")).orElseThrow().currentUnits());
    }

    @Test
    void copyFromReplacesTargetContentsRatherThanMerging() {
        PlayerResourceStateComponent source = new PlayerResourceStateComponent(null);
        source.instantiateScalar(id("test_scalar_a"), 50);

        PlayerResourceStateComponent target = new PlayerResourceStateComponent(null);
        target.instantiateScalar(id("test_scalar_b"), 100);

        target.copyFrom(source, null);

        assertTrue(target.hasState(id("test_scalar_a")));
        assertFalse(target.hasState(id("test_scalar_b")),
                "copyFrom must reflect the source's exact contents, not merge with prior target state");
    }

    // ── Orphan restoration (pure-logic coverage of the actual production transformation) ────
    //
    // orphanToLiveState(OrphanedResourceState) is a private static method with no ValueInput
    // dependency, so it is reachable via reflection without needing Mojang's NBT I/O types — this
    // exercises the REAL production restoration logic, not a reimplementation of it. The
    // live-to-orphan direction (readLiveFormatAsOrphan) does take a ValueInput and is therefore
    // covered indirectly: OrphanedResourceState's own field population (putCurrent/putOverflow/
    // setScalarRegenerationRemainder), exercised directly in OrphanedResourceStateTest, is exactly
    // what that method does internally for the SCALAR case.

    private static Method orphanToLiveStateMethod() throws NoSuchMethodException {
        Method method = PlayerResourceStateComponent.class
                .getDeclaredMethod("orphanToLiveState", OrphanedResourceState.class);
        method.setAccessible(true);
        return method;
    }

    @Test
    void scalarOrphanRestorationPreservesCurrentOverflowAndRemainder() throws Exception {
        OrphanedResourceState orphan = new OrphanedResourceState(ResourceModel.SCALAR);
        orphan.putCurrent(0, 42);
        orphan.putOverflow(0, 7);
        orphan.setScalarRegenerationRemainder(3);

        ResourceState restored = (ResourceState) orphanToLiveStateMethod().invoke(null, orphan);

        assertInstanceOf(ScalarResourceState.class, restored);
        ScalarResourceState scalar = (ScalarResourceState) restored;
        assertEquals(42, scalar.currentUnits());
        assertEquals(7, scalar.overflowUnits());
        assertEquals(3, scalar.regenerationRemainder(),
                "regeneration remainder must survive the full live -> orphan -> live sequence");
    }

    @Test
    void partitionedOrphanRestorationPreservesOverflowOnlyPartition() throws Exception {
        OrphanedResourceState orphan = new OrphanedResourceState(ResourceModel.PARTITIONED_POOL);
        orphan.putCurrent(1, 4);
        // partition 2 has ONLY an overflow value, no current entry — must not be dropped.
        orphan.putOverflow(2, 9);

        ResourceState restored = (ResourceState) orphanToLiveStateMethod().invoke(null, orphan);

        assertInstanceOf(PartitionedResourceState.class, restored);
        PartitionedResourceState partitioned = (PartitionedResourceState) restored;
        assertEquals(4, partitioned.getCurrent(1));
        assertEquals(0, partitioned.getOverflow(1));
        assertEquals(0, partitioned.getCurrent(2), "partition 2 never had a current value");
        assertEquals(9, partitioned.getOverflow(2),
                "an overflow-only partition must survive orphan restoration");
    }

    // ── Deep-copy of orphaned state (item 3) ───────────────────────────────────────────────
    //
    // orphanedStates has no public mutator (by design — orphans are only ever populated by the
    // NBT read path), so these tests use reflection purely to set up fixtures, not to bypass or
    // reimplement the method under test (copyFrom itself is called normally).

    @SuppressWarnings("unchecked")
    private static Map<Identifier, OrphanedResourceState> orphanedStatesOf(PlayerResourceStateComponent component) throws Exception {
        Field field = PlayerResourceStateComponent.class.getDeclaredField("orphanedStates");
        field.setAccessible(true);
        return (Map<Identifier, OrphanedResourceState>) field.get(component);
    }

    @Test
    void copyFromDeepCopiesOrphanedStates() throws Exception {
        PlayerResourceStateComponent source = new PlayerResourceStateComponent(null);
        OrphanedResourceState orphan = new OrphanedResourceState(ResourceModel.SCALAR);
        orphan.putCurrent(0, 10);
        orphanedStatesOf(source).put(id("stale_resource"), orphan);

        PlayerResourceStateComponent target = new PlayerResourceStateComponent(null);
        target.copyFrom(source, null);

        assertTrue(target.orphanedResourceIds().contains(id("stale_resource")));
        OrphanedResourceState copiedOrphan = orphanedStatesOf(target).get(id("stale_resource"));
        assertNotNull(copiedOrphan);
        assertNotSame(orphan, copiedOrphan, "copyFrom must deep-copy orphaned state, not share the instance");

        copiedOrphan.putCurrent(0, 999);
        assertEquals(10, orphan.getCurrent(0), "mutating the copy must not affect the original");

        orphan.putCurrent(0, 555);
        assertEquals(999, copiedOrphan.getCurrent(0), "mutating the original after copy must not affect the copy");
    }

    @Test
    void copyFromOrphanedStatesReplacesTargetContentsRatherThanMerging() throws Exception {
        PlayerResourceStateComponent source = new PlayerResourceStateComponent(null);
        orphanedStatesOf(source).put(id("from_source"), new OrphanedResourceState(ResourceModel.SCALAR));

        PlayerResourceStateComponent target = new PlayerResourceStateComponent(null);
        orphanedStatesOf(target).put(id("from_target"), new OrphanedResourceState(ResourceModel.SCALAR));

        target.copyFrom(source, null);

        assertTrue(target.orphanedResourceIds().contains(id("from_source")));
        assertFalse(target.orphanedResourceIds().contains(id("from_target")),
                "copyFrom must reflect the source's exact orphan contents, not merge with prior target state");
    }
}

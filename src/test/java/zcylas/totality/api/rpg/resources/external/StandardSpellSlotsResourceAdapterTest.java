package zcylas.totality.api.rpg.resources.external;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.magic.spell.SpellSlotComponent;
import zcylas.totality.api.rpg.resources.PartitionedResourceSnapshot;
import zcylas.totality.api.rpg.resources.ResourceQueryFailureReason;
import zcylas.totality.api.rpg.resources.ResourceQueryResult;

import java.util.Map;
import java.util.NavigableMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure logic for standard spell slots, tested directly against {@link
 * StandardSpellSlotsResourceAdapter#resolve} rather than through a real {@code ServerPlayer}
 * (constructing one outside the Minecraft runtime is impractical — matching {@code
 * ManaResourceAdapterTest}/{@code StaminaResourceAdapterTest}'s established precedent). {@link
 * StandardSpellSlotsResourceAdapter#snapshot} itself (including the client/server routing it adds on
 * top) is exercised end-to-end only by the manual smoke-test checklist in the Phase 2D report, plus
 * the production client-side routing test in {@code PlayerResourceRegistryExternalAdapterFreezeTest}.
 */
class StandardSpellSlotsResourceAdapterTest {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "spell_slots");

    private static PartitionedResourceSnapshot successSnapshot(ResourceQueryResult result) {
        assertInstanceOf(ResourceQueryResult.PartitionedSuccess.class, result);
        return ((ResourceQueryResult.PartitionedSuccess) result).snapshot();
    }

    private static ResourceQueryFailureReason failureReason(ResourceQueryResult result) {
        assertInstanceOf(ResourceQueryResult.Failure.class, result);
        return ((ResourceQueryResult.Failure) result).reason();
    }

    @Test
    void adapterSupportsOnlyQuery() {
        assertTrue(StandardSpellSlotsResourceAdapter.INSTANCE.supportedOperations()
                .contains(ExternalResourceOperationSupport.QUERY));
        assertEquals(1, StandardSpellSlotsResourceAdapter.INSTANCE.supportedOperations().size(),
                "standard spell slots are query-only in Phase 2D — no RESTORE/DRAIN/SET support");
        assertEquals(ExternalResourceClientMirrorMode.LEGACY_BESPOKE_SYNCHRONIZATION,
                StandardSpellSlotsResourceAdapter.INSTANCE.clientMirrorMode());
    }

    @Test
    void adapterIdMatchesTheSpellSlotsResourceId() {
        assertEquals(ID, StandardSpellSlotsResourceAdapter.INSTANCE.id());
        assertEquals(ID, StandardSpellSlotsResourceAdapter.ID);
    }

    // ── resolve(): shape and ordering ───────────────────────────────────────────────────────

    @Test
    void freshComponentProducesAllTenLevelsAtZeroZero() {
        SpellSlotComponent component = new SpellSlotComponent(null);

        PartitionedResourceSnapshot snapshot = successSnapshot(
                StandardSpellSlotsResourceAdapter.resolve(ID, component));

        NavigableMap<Integer, PartitionedResourceSnapshot.ResourcePartitionSnapshot> partitions = snapshot.partitions();
        assertEquals(10, partitions.size(), "all ten levels must be present, including zero-maximum ones");
        assertEquals(java.util.List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), java.util.List.copyOf(partitions.keySet()),
                "partitions must be keys 1..10 in ascending order");
        for (var entry : partitions.entrySet()) {
            assertEquals(0L, entry.getValue().currentUnits(), "level " + entry.getKey());
            assertEquals(0L, entry.getValue().maximumUnits(), "level " + entry.getKey());
        }
    }

    @Test
    void allZeroStateIsAValidSuccessNotUninitialized() {
        // The canonical Phase 2D decision: SpellSlotComponent has no initialization sentinel, so an
        // attached component whose every level is 0/0 (a non-caster, or a caster who hasn't gained
        // slots yet) is a legitimate successful snapshot — never STATE_UNINITIALIZED.
        SpellSlotComponent component = new SpellSlotComponent(null);

        ResourceQueryResult result = StandardSpellSlotsResourceAdapter.resolve(ID, component);

        assertInstanceOf(ResourceQueryResult.PartitionedSuccess.class, result);
    }

    @Test
    void partiallySpentSlotIsReflectedAsMaximumMinusUsed() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 3, 2, 0, 0, 0, 0, 0, 0, 0});
        component.useSlot(1);

        PartitionedResourceSnapshot snapshot = successSnapshot(
                StandardSpellSlotsResourceAdapter.resolve(ID, component));

        assertEquals(3L, snapshot.partition(1).orElseThrow().currentUnits());
        assertEquals(4L, snapshot.partition(1).orElseThrow().maximumUnits());
    }

    @Test
    void fullySpentSlotHasZeroCurrentButNonZeroMaximum() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {2, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        component.useSlot(1);
        component.useSlot(1);

        PartitionedResourceSnapshot snapshot = successSnapshot(
                StandardSpellSlotsResourceAdapter.resolve(ID, component));

        assertEquals(0L, snapshot.partition(1).orElseThrow().currentUnits());
        assertEquals(2L, snapshot.partition(1).orElseThrow().maximumUnits());
    }

    @Test
    void fullSlotHasCurrentEqualToMaximum() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        PartitionedResourceSnapshot snapshot = successSnapshot(
                StandardSpellSlotsResourceAdapter.resolve(ID, component));

        assertEquals(4L, snapshot.partition(1).orElseThrow().currentUnits());
        assertEquals(4L, snapshot.partition(1).orElseThrow().maximumUnits());
    }

    @Test
    void mixedValuesAcrossLevelsIncludingLevelTenAreAllPresent() {
        // A high-epic full caster: level 25+ progression unlocks a 10th-level slot.
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {5, 4, 3, 3, 3, 2, 2, 2, 1, 1});
        component.useSlot(1);
        component.useSlot(9);

        PartitionedResourceSnapshot snapshot = successSnapshot(
                StandardSpellSlotsResourceAdapter.resolve(ID, component));

        assertEquals(4L, snapshot.partition(1).orElseThrow().currentUnits());
        assertEquals(5L, snapshot.partition(1).orElseThrow().maximumUnits());
        assertEquals(0L, snapshot.partition(9).orElseThrow().currentUnits());
        assertEquals(1L, snapshot.partition(9).orElseThrow().maximumUnits());
        assertEquals(1L, snapshot.partition(10).orElseThrow().currentUnits(), "level 10 must be present and unspent");
        assertEquals(1L, snapshot.partition(10).orElseThrow().maximumUnits());
    }

    @Test
    void zeroMaximumLevelsRemainPresentAlongsideNonZeroOnes() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 3, 0, 0, 0, 0, 0, 0, 0, 0});

        PartitionedResourceSnapshot snapshot = successSnapshot(
                StandardSpellSlotsResourceAdapter.resolve(ID, component));

        assertEquals(10, snapshot.partitions().size());
        assertEquals(0L, snapshot.partition(3).orElseThrow().maximumUnits());
        assertEquals(0L, snapshot.partition(10).orElseThrow().maximumUnits());
    }

    // ── resolve(): malformed owner state ─────────────────────────────────────────────────────

    @Test
    void negativeMaximumProducesMalformedOwnerStateFailure() throws Exception {
        SpellSlotComponent component = new SpellSlotComponent(null);
        writeMaxSlotDirectly(component, 0, -1);

        ResourceQueryResult result = StandardSpellSlotsResourceAdapter.resolve(ID, component);

        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, failureReason(result));
    }

    @Test
    void negativeUsedProducesMalformedOwnerStateFailure() throws Exception {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        writeUsedSlotDirectly(component, 0, -1);

        ResourceQueryResult result = StandardSpellSlotsResourceAdapter.resolve(ID, component);

        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, failureReason(result));
    }

    @Test
    void usedGreaterThanMaximumProducesMalformedOwnerStateFailure() throws Exception {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {2, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        writeUsedSlotDirectly(component, 0, 5); // used (5) > maximum (2), bypassing useSlot's own guard

        ResourceQueryResult result = StandardSpellSlotsResourceAdapter.resolve(ID, component);

        assertEquals(ResourceQueryFailureReason.MALFORMED_OWNER_STATE, failureReason(result));
    }

    /**
     * Reflection is unavoidable here: every public {@link SpellSlotComponent} mutator
     * ({@code recalculate}, {@code useSlot}, {@code restoreAll}, {@code restoreSome}) already
     * enforces internally-consistent values, so the only way to construct the malformed-owner-state
     * scenarios above (corrupted persisted NBT, essentially) is to write the private arrays directly.
     */
    private static void writeMaxSlotDirectly(SpellSlotComponent component, int index, int value) throws Exception {
        var field = SpellSlotComponent.class.getDeclaredField("maxSlots");
        field.setAccessible(true);
        ((int[]) field.get(component))[index] = value;
    }

    private static void writeUsedSlotDirectly(SpellSlotComponent component, int index, int value) throws Exception {
        var field = SpellSlotComponent.class.getDeclaredField("usedSlots");
        field.setAccessible(true);
        ((int[]) field.get(component))[index] = value;
    }

    // ── resolve(): purity ────────────────────────────────────────────────────────────────────

    @Test
    void resolveDoesNotMutateTheComponent() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 3, 0, 0, 0, 0, 0, 0, 0, 0});
        component.useSlot(1);

        StandardSpellSlotsResourceAdapter.resolve(ID, component);

        assertEquals(1, component.getUsed(1), "resolve must never write the component it read");
        assertEquals(4, component.getMax(1));
        assertEquals(0, component.getUsed(2));
    }

    @Test
    void repeatedResolveCallsProduceIdenticalResults() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 3, 2, 0, 0, 0, 0, 0, 0, 0});
        component.useSlot(2);

        PartitionedResourceSnapshot first = successSnapshot(StandardSpellSlotsResourceAdapter.resolve(ID, component));
        PartitionedResourceSnapshot second = successSnapshot(StandardSpellSlotsResourceAdapter.resolve(ID, component));

        assertEquals(first.partitions(), second.partitions());
    }

    @Test
    void resourceIdIsPreservedAndUnitScaleIsAlwaysTheCanonicalConstant() {
        SpellSlotComponent component = new SpellSlotComponent(null);

        PartitionedResourceSnapshot snapshot = successSnapshot(
                StandardSpellSlotsResourceAdapter.resolve(ID, component));

        assertEquals(ID, snapshot.resourceId());
        assertEquals(StandardSpellSlotsResourceAdapter.UNIT_SCALE, snapshot.unitScale());
    }

    @Test
    void unitScaleConstantIsExactlyOne() {
        // The legacy owner (SpellSlotComponent) stores discrete slot counts — no fixed-point scaling.
        assertEquals(1L, StandardSpellSlotsResourceAdapter.UNIT_SCALE);
    }

    @Test
    void resolveAlwaysProducesTheCanonicalUnitScaleRegardlessOfAnyDefinitionConfiguration() {
        // resolve(...) no longer takes a unitScale parameter at all — it cannot be swayed by a
        // definition misconfigured with a different scale. This test documents that guarantee
        // directly: there is no way to make resolve() produce anything other than UNIT_SCALE.
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        PartitionedResourceSnapshot snapshot = successSnapshot(
                StandardSpellSlotsResourceAdapter.resolve(ID, component));

        assertEquals(StandardSpellSlotsResourceAdapter.UNIT_SCALE, snapshot.unitScale());
    }

    // ── snapshot(): client-side routing (no real Player construction needed here) ───────────

    @Test
    void nullPlayerIsTreatedAsNotAServerPlayerAndReturnsStateUnavailableOnThisSide() {
        var definition = zcylas.totality.api.rpg.resources.PlayerResourceDefinition
                .builder(ID, zcylas.totality.api.rpg.resources.ResourceModel.PARTITIONED_POOL)
                .externalAdapter(ID)
                .unitScale(1)
                .build();

        ResourceQueryResult result = StandardSpellSlotsResourceAdapter.INSTANCE.snapshot(null, definition);

        assertEquals(ResourceQueryFailureReason.STATE_UNAVAILABLE_ON_THIS_SIDE, failureReason(result));
    }

    // ── boundaries: no Pact Magic / other casting-source data ───────────────────────────────

    @Test
    void snapshotNeverContainsMoreThanTenPartitions() {
        // SpellSlotComponent.MAX_SPELL_LEVEL is 10 and resolve() iterates exactly 1..MAX_SPELL_LEVEL
        // — no Pact Magic tier, item charge, Sorcery Point, or granted-use partition can appear,
        // since nothing else ever contributes an entry to this map.
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 3, 3, 3, 2, 1, 1, 1, 1, 1});

        PartitionedResourceSnapshot snapshot = successSnapshot(
                StandardSpellSlotsResourceAdapter.resolve(ID, component));

        assertEquals(10, snapshot.partitions().size());
        assertEquals(SpellSlotComponent.MAX_SPELL_LEVEL, snapshot.partitions().size());
    }
}

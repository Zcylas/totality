package zcylas.totality.api.rpg.resources.sync;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure coalescing/diffing/revision behavior of {@link PlayerResourceSyncState}, independent of any
 * {@code ServerPlayer}/networking — see {@code ResourceSyncManager} for the impure orchestration
 * layer this feeds.
 */
class PlayerResourceSyncStateTest {

    private static final Identifier MANA = Identifier.fromNamespaceAndPath("totality", "mana");
    private static final Identifier STAMINA = Identifier.fromNamespaceAndPath("totality", "stamina");
    private static final Identifier RAGE = Identifier.fromNamespaceAndPath("totality", "rage");
    private static final Identifier SPELL_SLOTS = Identifier.fromNamespaceAndPath("totality", "spell_slots");

    private static ResourceScalarWireSnapshot scalar(Identifier id, long current, long max) {
        return new ResourceScalarWireSnapshot(id, 1L, current, max, 0L);
    }

    @Test
    void multipleResourcesDirtiedInOneTickCoalesceIntoOneBatch() {
        PlayerResourceSyncState state = new PlayerResourceSyncState();
        state.markDirty(MANA);
        state.markDirty(STAMINA);

        Map<Identifier, PlayerResourceSyncState.ResourceOutcome> outcomes = new LinkedHashMap<>();
        outcomes.put(MANA, new PlayerResourceSyncState.ScalarOutcome(scalar(MANA, 50, 100)));
        outcomes.put(STAMINA, new PlayerResourceSyncState.ScalarOutcome(scalar(STAMINA, 80, 100)));

        PlayerResourceSyncState.DeltaBatch batch = state.computeDeltaAndApply(outcomes);

        assertEquals(2, batch.upsertScalars().size(), "both resources must land in the same batch");
        assertTrue(state.dirtyIds().isEmpty(), "dirty set must be cleared after the batch is computed");
    }

    @Test
    void multipleChangesToOneResourceInOneTickSendOnlyTheFinalValue() {
        PlayerResourceSyncState state = new PlayerResourceSyncState();
        // Simulates several mutations to Mana within one tick before flush ever runs — only the
        // final queried value (as of flush time) is ever supplied to computeDeltaAndApply, since
        // markDirty carries no value itself, only an identity.
        state.markDirty(MANA);
        state.markDirty(MANA);
        state.markDirty(MANA);

        Map<Identifier, PlayerResourceSyncState.ResourceOutcome> outcomes =
                Map.of(MANA, new PlayerResourceSyncState.ScalarOutcome(scalar(MANA, 42, 100)));

        PlayerResourceSyncState.DeltaBatch batch = state.computeDeltaAndApply(outcomes);

        assertEquals(1, batch.upsertScalars().size());
        assertEquals(42L, batch.upsertScalars().get(0).currentUnits());
    }

    @Test
    void oneBatchIncrementsRevisionExactlyOnce() {
        PlayerResourceSyncState state = new PlayerResourceSyncState();
        long before = state.revision();
        state.bumpRevision();
        assertEquals(before + 1, state.revision());
    }

    @Test
    void unchangedOutcomeProducesNoUpsert() {
        PlayerResourceSyncState state = new PlayerResourceSyncState();
        state.markDirty(MANA);
        Map<Identifier, PlayerResourceSyncState.ResourceOutcome> firstOutcomes =
                Map.of(MANA, new PlayerResourceSyncState.ScalarOutcome(scalar(MANA, 50, 100)));
        PlayerResourceSyncState.DeltaBatch firstBatch = state.computeDeltaAndApply(firstOutcomes);
        assertFalse(firstBatch.isEmpty(), "first observation of a resource must always upsert");

        // Marked dirty again, but the freshly queried value is byte-for-byte identical.
        state.markDirty(MANA);
        Map<Identifier, PlayerResourceSyncState.ResourceOutcome> secondOutcomes =
                Map.of(MANA, new PlayerResourceSyncState.ScalarOutcome(scalar(MANA, 50, 100)));
        PlayerResourceSyncState.DeltaBatch secondBatch = state.computeDeltaAndApply(secondOutcomes);

        assertTrue(secondBatch.isEmpty(), "an unchanged value must not generate a delta");
    }

    @Test
    void resourceBecomingAbsentInvalidatesAPreviouslySyncedEntry() {
        PlayerResourceSyncState state = new PlayerResourceSyncState();
        state.markDirty(RAGE);
        state.computeDeltaAndApply(Map.of(RAGE, new PlayerResourceSyncState.ScalarOutcome(scalar(RAGE, 2, 2))));

        state.markDirty(RAGE);
        PlayerResourceSyncState.DeltaBatch batch =
                state.computeDeltaAndApply(Map.of(RAGE, new PlayerResourceSyncState.AbsentOutcome()));

        assertTrue(batch.upsertScalars().isEmpty());
        assertEquals(List.of(RAGE), batch.invalidated());
    }

    @Test
    void missingRageIsOmittedRatherThanEncodedAsAValidZeroPool() {
        // A non-Barbarian querying totality:rage gets STATE_UNINITIALIZED, which the manager
        // reduces to AbsentOutcome — never a fabricated ScalarOutcome(0, 0).
        PlayerResourceSyncState state = new PlayerResourceSyncState();
        state.markDirty(RAGE);
        PlayerResourceSyncState.DeltaBatch batch =
                state.computeDeltaAndApply(Map.of(RAGE, new PlayerResourceSyncState.AbsentOutcome()));

        // Never having been present before, "absent" produces no invalidation either — there is
        // nothing to retract.
        assertTrue(batch.isEmpty(), "a resource that was never synchronized produces no packet content at all");
    }

    @Test
    void validRageZeroZeroRemainsRepresentable() {
        // Distinguishes "no Rage pool" (AbsentOutcome, above) from "a real Rage pool at 0/0" —
        // the latter is a legitimate ScalarOutcome and must upsert normally.
        PlayerResourceSyncState state = new PlayerResourceSyncState();
        state.markDirty(RAGE);
        PlayerResourceSyncState.DeltaBatch batch =
                state.computeDeltaAndApply(Map.of(RAGE, new PlayerResourceSyncState.ScalarOutcome(scalar(RAGE, 0, 0))));

        assertEquals(1, batch.upsertScalars().size());
        assertEquals(0L, batch.upsertScalars().get(0).currentUnits());
        assertEquals(0L, batch.upsertScalars().get(0).maximumUnits());
    }

    @Test
    void tenSpellSlotPartitionsSurviveAFullBatch() {
        PlayerResourceSyncState state = new PlayerResourceSyncState();
        java.util.List<ResourcePartitionWireEntry> entries = new java.util.ArrayList<>();
        for (int level = 1; level <= 10; level++) {
            entries.add(new ResourcePartitionWireEntry(level, 0, level, 0));
        }
        ResourcePartitionedWireSnapshot snapshot = new ResourcePartitionedWireSnapshot(SPELL_SLOTS, 1L, entries);

        PlayerResourceSyncState.FullBatch batch = state.applyFull(
                Map.of(SPELL_SLOTS, new PlayerResourceSyncState.PartitionedOutcome(snapshot)));

        assertEquals(1, batch.partitioned().size());
        assertEquals(10, batch.partitioned().get(0).partitions().size());
    }

    @Test
    void fullBatchClearsDirtyAndPendingFlagAndDoesNotBumpRevisionItself() {
        PlayerResourceSyncState state = new PlayerResourceSyncState();
        state.markDirty(MANA);
        state.scheduleFullSnapshot();
        long before = state.revision();

        state.applyFull(Map.of(MANA, new PlayerResourceSyncState.ScalarOutcome(scalar(MANA, 1, 1))));

        assertFalse(state.isFullSnapshotPending());
        assertTrue(state.dirtyIds().isEmpty());
        assertEquals(before, state.revision(), "applyFull itself must not bump revision — the caller does, once");
    }

    @Test
    void hasPendingWorkReflectsDirtyAndFullFlagIndependently() {
        PlayerResourceSyncState state = new PlayerResourceSyncState();
        assertFalse(state.hasPendingWork());

        state.markDirty(MANA);
        assertTrue(state.hasPendingWork());

        state.computeDeltaAndApply(Map.of(MANA, new PlayerResourceSyncState.ScalarOutcome(scalar(MANA, 1, 1))));
        assertFalse(state.hasPendingWork());

        state.scheduleFullSnapshot();
        assertTrue(state.hasPendingWork());
    }

    // ── Correction 4: shared generic-sync eligibility ───────────────────────────────────────────
    // ResourceSyncManager.sendDelta filters state.dirtyIds() through isEligibleForGenericSync
    // before ever building the outcomes map handed to computeDeltaAndApply — an ineligible/
    // unregistered dirty id is simply never added to that map. These tests exercise
    // computeDeltaAndApply's side of that contract directly: a dirty id absent from the supplied
    // outcomes must never be upserted, but must still be cleared from the dirty set so it cannot
    // linger as pending forever.

    @Test
    void dirtyIdMissingFromQueriedOutcomesIsDiscardedButDirtyIsStillCleared() {
        PlayerResourceSyncState state = new PlayerResourceSyncState();
        state.markDirty(MANA);
        state.markDirty(STAMINA); // simulates an ineligible/unregistered id filtered out upstream

        Map<Identifier, PlayerResourceSyncState.ResourceOutcome> outcomes =
                Map.of(MANA, new PlayerResourceSyncState.ScalarOutcome(scalar(MANA, 50, 100)));
        // STAMINA deliberately omitted — mirrors an ineligible id never being queried at all.

        PlayerResourceSyncState.DeltaBatch batch = state.computeDeltaAndApply(outcomes);

        assertEquals(1, batch.upsertScalars().size());
        assertEquals(MANA, batch.upsertScalars().get(0).resourceId());
        assertTrue(state.dirtyIds().isEmpty(), "dirty bookkeeping must be cleared even for a discarded id");
    }

    @Test
    void batchWhereEveryDirtyIdIsMissingFromOutcomesIsEmpty() {
        PlayerResourceSyncState state = new PlayerResourceSyncState();
        state.markDirty(MANA);
        state.markDirty(STAMINA);

        // Simulates every dirty id being ineligible/unregistered — the caller (ResourceSyncManager.
        // sendDelta) would have built an empty outcomes map and must send no packet / bump no
        // revision as a result.
        PlayerResourceSyncState.DeltaBatch batch = state.computeDeltaAndApply(Map.of());

        assertTrue(batch.isEmpty());
        assertTrue(state.dirtyIds().isEmpty());
    }

    // ── Correction 5: maximum-only change notification ──────────────────────────────────────────

    @Test
    void maximumOnlyDifferenceWithUnchangedCurrentStillProducesAnUpsert() {
        PlayerResourceSyncState state = new PlayerResourceSyncState();
        state.markDirty(MANA);
        state.computeDeltaAndApply(Map.of(MANA, new PlayerResourceSyncState.ScalarOutcome(scalar(MANA, 80, 100))));

        // 80/100 becomes 80/150 — current is identical, only the maximum changed.
        state.markDirty(MANA);
        PlayerResourceSyncState.DeltaBatch batch =
                state.computeDeltaAndApply(Map.of(MANA, new PlayerResourceSyncState.ScalarOutcome(scalar(MANA, 80, 150))));

        assertFalse(batch.isEmpty(), "a maximum-only change must still be observed as a real difference");
        assertEquals(150L, batch.upsertScalars().get(0).maximumUnits());
        assertEquals(80L, batch.upsertScalars().get(0).currentUnits());
    }

    // ── Protocol hardening: deterministic delta ordering ────────────────────────────────────────

    @Test
    void deltaUpsertsAndInvalidationsAreSortedByResourceIdRegardlessOfDirtyOrder() {
        Identifier aaa = Identifier.fromNamespaceAndPath("totality", "aaa_first");
        Identifier mmm = Identifier.fromNamespaceAndPath("totality", "mmm_middle");
        Identifier zzz = Identifier.fromNamespaceAndPath("totality", "zzz_last");

        PlayerResourceSyncState state = new PlayerResourceSyncState();
        // Mark dirty in deliberately reverse/scrambled order.
        state.markDirty(zzz);
        state.markDirty(aaa);
        state.markDirty(mmm);

        PlayerResourceSyncState.DeltaBatch batch = state.computeDeltaAndApply(Map.of(
                zzz, new PlayerResourceSyncState.ScalarOutcome(scalar(zzz, 1, 10)),
                aaa, new PlayerResourceSyncState.ScalarOutcome(scalar(aaa, 1, 10)),
                mmm, new PlayerResourceSyncState.ScalarOutcome(scalar(mmm, 1, 10))));

        List<Identifier> order = batch.upsertScalars().stream().map(ResourceScalarWireSnapshot::resourceId).toList();
        assertEquals(List.of(aaa, mmm, zzz), order, "upsert order must be deterministic by resource id, not dirty-mark order");
    }

    @Test
    void deltaInvalidationsAreSortedByResourceId() {
        Identifier aaa = Identifier.fromNamespaceAndPath("totality", "aaa_first");
        Identifier zzz = Identifier.fromNamespaceAndPath("totality", "zzz_last");

        PlayerResourceSyncState state = new PlayerResourceSyncState();
        state.markDirty(zzz);
        state.markDirty(aaa);
        state.computeDeltaAndApply(Map.of(
                zzz, new PlayerResourceSyncState.ScalarOutcome(scalar(zzz, 1, 10)),
                aaa, new PlayerResourceSyncState.ScalarOutcome(scalar(aaa, 1, 10))));

        state.markDirty(zzz);
        state.markDirty(aaa);
        PlayerResourceSyncState.DeltaBatch batch = state.computeDeltaAndApply(Map.of(
                zzz, new PlayerResourceSyncState.AbsentOutcome(),
                aaa, new PlayerResourceSyncState.AbsentOutcome()));

        assertEquals(List.of(aaa, zzz), batch.invalidated());
    }
}

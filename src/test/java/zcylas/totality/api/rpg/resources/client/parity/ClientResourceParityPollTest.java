package zcylas.totality.api.rpg.resources.client.parity;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.client.ClientResourceTrust;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers Phase 3B-2B test-plan items 17-25: the pure poll orchestrator, driven entirely through
 *  fakes — no Minecraft client is booted anywhere in this file. */
class ClientResourceParityPollTest {

    private static ClientResourceParitySummary.Partitioned tenLevels(int remaining, int max) {
        List<ClientResourceParitySummary.Partitioned.Partition> partitions = new java.util.ArrayList<>();
        for (int level = 1; level <= 10; level++) {
            partitions.add(new ClientResourceParitySummary.Partitioned.Partition(level, remaining, max, 0));
        }
        return ClientResourceParitySummary.Partitioned.of(partitions, 1);
    }

    @Test
    void onePollComparesExactlyFourResourceIds() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        generic.setScalar(PlayerResourceIds.MANA, 100, 100, ClientResourceTrust.FRESH);
        generic.setScalar(PlayerResourceIds.STAMINA, 100, 100, ClientResourceTrust.FRESH);
        generic.setPartitioned(PlayerResourceIds.SPELL_SLOTS, partitionedResult(tenLevels(0, 0)));
        generic.setScalar(PlayerResourceIds.RAGE, 0, 0, ClientResourceTrust.FRESH);

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);

        assertTrue(tracker.latest(PlayerResourceIds.MANA).isPresent());
        assertTrue(tracker.latest(PlayerResourceIds.STAMINA).isPresent());
        assertTrue(tracker.latest(PlayerResourceIds.SPELL_SLOTS).isPresent());
        assertTrue(tracker.latest(PlayerResourceIds.RAGE).isPresent());
    }

    @Test
    void pollDispatchesScalarComparatorForMana() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        generic.setScalar(PlayerResourceIds.MANA, 40, 100, ClientResourceTrust.FRESH);
        legacy.setMana(42, 100);

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);

        // A current-value disagreement is exactly what ClientResourceScalarParityComparator
        // classifies as an ordinary mismatch (never MODEL_MISMATCH/EXPECTED_SEMANTIC_DIFFERENCE).
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH,
                tracker.latest(PlayerResourceIds.MANA).orElseThrow().classification());
    }

    @Test
    void pollDispatchesScalarComparatorForStamina() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        generic.setScalar(PlayerResourceIds.STAMINA, 40, 100, ClientResourceTrust.FRESH);
        legacy.setStamina(40, 100);

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);

        assertEquals(ClientResourceParityClassification.EXACT_MATCH,
                tracker.latest(PlayerResourceIds.STAMINA).orElseThrow().classification());
    }

    @Test
    void pollDispatchesSpellSlotComparatorForSlots() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        generic.setPartitioned(PlayerResourceIds.SPELL_SLOTS, partitionedResult(tenLevels(3, 5)));
        legacy.setSpellSlots(tenLevels(3, 5));

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);

        assertEquals(ClientResourceParityClassification.EXACT_MATCH,
                tracker.latest(PlayerResourceIds.SPELL_SLOTS).orElseThrow().classification());
    }

    @Test
    void pollDispatchesRagePolicyForRage() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        // Generic absent + not expected + legacy 0/0 is EXPECTED_SEMANTIC_DIFFERENCE only via
        // ClientRageParityPolicy — a plain scalar comparator would never produce this classification
        // for an Unavailable generic side, proving the Rage policy (not the scalar comparator) is
        // the one actually dispatched.
        generic.setScalarUnavailable(PlayerResourceIds.RAGE, zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason.NOT_AVAILABLE_TO_PLAYER);
        legacy.setRage(new ClientResourceParitySummary.Scalar(0, 0, 0, 1));
        legacy.setRageExpected(false);

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);

        assertEquals(ClientResourceParityClassification.EXPECTED_SEMANTIC_DIFFERENCE,
                tracker.latest(PlayerResourceIds.RAGE).orElseThrow().classification());
    }

    @Test
    void pollSubmitsFreshSummariesToTracker() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        generic.setScalar(PlayerResourceIds.MANA, 77, 120, ClientResourceTrust.FRESH);
        legacy.setMana(77, 120);

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);

        var observation = tracker.latest(PlayerResourceIds.MANA).orElseThrow();
        var genericSummary = (ClientResourceParitySummary.Scalar) observation.genericSummary();
        var legacySummary = (ClientResourceParitySummary.Scalar) observation.legacySummary();
        assertEquals(77, genericSummary.currentUnits());
        assertEquals(120, genericSummary.maximumUnits());
        assertEquals(77, legacySummary.currentUnits());
        assertEquals(120, legacySummary.maximumUnits());
    }

    @Test
    void pollPassesPendingResyncToTracker() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        generic.setScalar(PlayerResourceIds.MANA, 40, 100, ClientResourceTrust.PENDING_RESYNC);
        legacy.setMana(42, 100);

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);

        assertTrue(tracker.latest(PlayerResourceIds.MANA).orElseThrow().graceFrozen());
    }

    @Test
    void fullPollingRecoversAPersistentMismatchWhenValuesMatch() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        generic.setScalar(PlayerResourceIds.MANA, 40, 100, ClientResourceTrust.FRESH);
        legacy.setMana(42, 100);

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);
        ClientResourceParityPoll.pollOnce(tracker, 1, generic, legacy);
        var persistent = pollAt(tracker, generic, legacy, 2);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, persistent);

        // Values now agree — the very next full poll (never advanceDeadline) must recover.
        legacy.setMana(40, 100);
        ClientResourceParityPoll.pollOnce(tracker, 3, generic, legacy);
        assertEquals(ClientResourceParityClassification.EXACT_MATCH,
                tracker.latest(PlayerResourceIds.MANA).orElseThrow().classification());
    }

    @Test
    void fullPollingAdvancesAContinuousMismatchWithoutUsingAdvanceDeadlineAsThePrimaryPath() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        generic.setScalar(PlayerResourceIds.STAMINA, 40, 100, ClientResourceTrust.FRESH);
        legacy.setStamina(45, 100);

        // Every tick is a fresh, full pollOnce call — advanceDeadline is never invoked anywhere in
        // this test, proving the T/T+1/T+2 escalation works purely through repeated full polling.
        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH,
                tracker.latest(PlayerResourceIds.STAMINA).orElseThrow().classification());
        ClientResourceParityPoll.pollOnce(tracker, 1, generic, legacy);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH,
                tracker.latest(PlayerResourceIds.STAMINA).orElseThrow().classification());
        ClientResourceParityPoll.pollOnce(tracker, 2, generic, legacy);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH,
                tracker.latest(PlayerResourceIds.STAMINA).orElseThrow().classification());
    }

    @Test
    void rageUnavailableLegacyPollIsObservedAsModelMismatch() {
        // External-review correction (2026-07-25): a structurally missing legacy Rage source must
        // never be silently skipped — it is observed as a fresh MODEL_MISMATCH. The generic side
        // must be a genuinely present value here (not the fake's NOT_SYNCHRONIZED_YET default),
        // since that readiness gate otherwise takes precedence over the legacy-unavailable check.
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        generic.setScalar(PlayerResourceIds.RAGE, 5, 10, ClientResourceTrust.FRESH);
        legacy.setRageUnavailable(zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE);

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);

        assertTrue(tracker.latest(PlayerResourceIds.RAGE).isPresent());
        assertEquals(ClientResourceParityClassification.MODEL_MISMATCH,
                tracker.latest(PlayerResourceIds.RAGE).orElseThrow().classification());
    }

    @Test
    void rageUnavailableLegacyPollStillPerformsTheGenericRageQuery() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        generic.setScalar(PlayerResourceIds.RAGE, 5, 10, ClientResourceTrust.FRESH);
        legacy.setRageUnavailable(zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE);

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);

        assertEquals(1, generic.scalarQueryCount(PlayerResourceIds.RAGE));
    }

    @Test
    void rageUnavailableReplacesAPreviousObservationRatherThanLeavingItStale() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        generic.setScalar(PlayerResourceIds.RAGE, 5, 10, ClientResourceTrust.FRESH);
        legacy.setRage(new ClientResourceParitySummary.Scalar(5, 10, 0, 1));
        legacy.setRageExpected(true);

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);
        assertEquals(ClientResourceParityClassification.EXACT_MATCH,
                tracker.latest(PlayerResourceIds.RAGE).orElseThrow().classification());

        legacy.setRageUnavailable(zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE);
        ClientResourceParityPoll.pollOnce(tracker, 1, generic, legacy);

        assertEquals(ClientResourceParityClassification.MODEL_MISMATCH,
                tracker.latest(PlayerResourceIds.RAGE).orElseThrow().classification());
    }

    @Test
    void genericNotSynchronizedYetWithLegacyRageUnavailableRemainsGenericNotReady() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        // Rage's generic entry is left unconfigured, so FakeClientResourceParityGenericAccess
        // defaults it to NOT_SYNCHRONIZED_YET (mirroring the real façade's pre-first-snapshot
        // behavior) — the readiness gate must still win over the legacy-unavailable check.
        legacy.setRageUnavailable(zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason.MALFORMED_SOURCE_STATE);

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);

        assertEquals(ClientResourceParityClassification.GENERIC_NOT_READY,
                tracker.latest(PlayerResourceIds.RAGE).orElseThrow().classification());
    }

    @Test
    void onePollPerformsExactlyFourGenericQueriesNeverMoreNeverFewer() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        generic.setScalar(PlayerResourceIds.MANA, 100, 100, ClientResourceTrust.FRESH);
        generic.setScalar(PlayerResourceIds.STAMINA, 100, 100, ClientResourceTrust.FRESH);
        generic.setPartitioned(PlayerResourceIds.SPELL_SLOTS, partitionedResult(tenLevels(0, 0)));
        generic.setScalar(PlayerResourceIds.RAGE, 0, 0, ClientResourceTrust.FRESH);

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);

        assertEquals(4, generic.invocations().size());
        assertEquals(1, generic.scalarQueryCount(PlayerResourceIds.MANA));
        assertEquals(1, generic.scalarQueryCount(PlayerResourceIds.STAMINA));
        assertEquals(1, generic.partitionedQueryCount(PlayerResourceIds.SPELL_SLOTS));
        assertEquals(1, generic.scalarQueryCount(PlayerResourceIds.RAGE));
    }

    @Test
    void onePollPerformsEachLegacyReadExactlyOnce() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);

        assertEquals(1, legacy.manaCallCount());
        assertEquals(1, legacy.staminaCallCount());
        assertEquals(1, legacy.spellSlotCallCount());
        assertEquals(1, legacy.rageCallCount());
        assertEquals(1, legacy.rageExpectedCallCount());
    }

    @Test
    void successivePollsPerformFreshReadsRatherThanReusingStoredSummaries() {
        var tracker = new ClientResourceParityTracker();
        var generic = new FakeClientResourceParityGenericAccess();
        var legacy = new FakeClientResourceParityLegacyAccess();
        generic.setScalar(PlayerResourceIds.MANA, 40, 100, ClientResourceTrust.FRESH);
        legacy.setMana(40, 100);

        ClientResourceParityPoll.pollOnce(tracker, 0, generic, legacy);
        assertEquals(ClientResourceParityClassification.EXACT_MATCH,
                tracker.latest(PlayerResourceIds.MANA).orElseThrow().classification());
        assertEquals(1, legacy.manaCallCount());
        assertEquals(1, generic.scalarQueryCount(PlayerResourceIds.MANA));

        // Change both sides between polls — a stale cached value would still report EXACT_MATCH.
        generic.setScalar(PlayerResourceIds.MANA, 90, 100, ClientResourceTrust.FRESH);
        legacy.setMana(40, 100);
        ClientResourceParityPoll.pollOnce(tracker, 1, generic, legacy);

        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH,
                tracker.latest(PlayerResourceIds.MANA).orElseThrow().classification());
        assertEquals(2, legacy.manaCallCount());
        assertEquals(2, generic.scalarQueryCount(PlayerResourceIds.MANA));
    }

    private static ClientResourceParityClassification pollAt(
            ClientResourceParityTracker tracker, FakeClientResourceParityGenericAccess generic,
            FakeClientResourceParityLegacyAccess legacy, long tick) {
        ClientResourceParityPoll.pollOnce(tracker, tick, generic, legacy);
        return tracker.latest(PlayerResourceIds.MANA).orElseThrow().classification();
    }

    private static zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult.Partitioned partitionedResult(
            ClientResourceParitySummary.Partitioned summary) {
        java.util.List<zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult.Partitioned.Partition> partitions =
                new java.util.ArrayList<>();
        for (var partition : summary.partitions().values()) {
            partitions.add(new zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult.Partitioned.Partition(
                    partition.partitionId(), partition.currentUnits(), partition.maximumUnits(), partition.overflowUnits()));
        }
        return zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult.Partitioned.of(
                PlayerResourceIds.SPELL_SLOTS, partitions, summary.unitScale(),
                zcylas.totality.api.rpg.resources.client.ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW,
                ClientResourceTrust.FRESH);
    }
}

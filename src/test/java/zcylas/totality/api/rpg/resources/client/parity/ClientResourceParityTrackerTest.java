package zcylas.totality.api.rpg.resources.client.parity;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers test-plan items 26-46 plus the external-review correction pass: the elapsed-client-tick
 * grace tracker's full state machine, including the PENDING_RESYNC freeze (with and without skipped
 * frozen ticks), the "no new source notification required" escalation path (see
 * {@link ClientResourceParityTracker#advanceDeadline}), frozen-mismatch recheck discoverability, and
 * saturating observation counts.
 */
class ClientResourceParityTrackerTest {

    private static final Identifier MANA = Identifier.fromNamespaceAndPath("totality", "mana");
    private static final Identifier STAMINA = Identifier.fromNamespaceAndPath("totality", "stamina");

    private static ClientResourceParitySummary.Scalar scalar(long current, long maximum) {
        return new ClientResourceParitySummary.Scalar(current, maximum, 0, 1);
    }

    // ── 26-28: exact T / T+1 / T+2 off-by-one semantics ────────────────────────────────────────

    @Test
    void firstMismatchAtTIsTransitional() {
        var tracker = new ClientResourceParityTracker();
        var observation = tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, observation.classification());
        assertEquals(10L, observation.firstMismatchTick());
    }

    @Test
    void continuedMismatchAtTPlusOneRemainsTransitional() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        var observation = tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, observation.classification());
        assertEquals(10L, observation.firstMismatchTick());
    }

    @Test
    void continuedMismatchAtTPlusTwoBecomesPersistent() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        var observation = tracker.observe(MANA, 12, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, observation.classification());
        assertEquals(10L, observation.firstMismatchTick());
    }

    @Test
    void configurableGraceDurationChangesTheDeadline() {
        var tracker = new ClientResourceParityTracker(3);
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        var stillTransitional = tracker.observe(MANA, 12, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, stillTransitional.classification());
        var nowPersistent = tracker.observe(MANA, 13, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, nowPersistent.classification());
    }

    @Test
    void graceDurationConstructorRejectsBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityTracker(0));
    }

    // ── 29-30: match clears / recovers ──────────────────────────────────────────────────────────

    @Test
    void matchDuringGraceClearsPendingMismatch() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        var observation = tracker.observe(MANA, 11, ClientResourceParityOutcome.MATCH, scalar(42, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.EXACT_MATCH, observation.classification());
        assertEquals(null, observation.firstMismatchTick());
    }

    @Test
    void matchAfterPersistenceRecoversToExactMatch() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        var persistent = tracker.observe(MANA, 12, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, persistent.classification());
        var recovered = tracker.observe(MANA, 13, ClientResourceParityOutcome.MATCH, scalar(42, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.EXACT_MATCH, recovered.classification());
    }

    @Test
    void newMismatchAfterMatchStartsANewGracePeriod() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        tracker.observe(MANA, 11, ClientResourceParityOutcome.MATCH, scalar(42, 100), scalar(42, 100), false);
        var freshMismatch = tracker.observe(MANA, 12, ClientResourceParityOutcome.MISMATCH, scalar(41, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, freshMismatch.classification());
        assertEquals(12L, freshMismatch.firstMismatchTick());
    }

    // ── 31-33: changing/repeated values never restart the deadline; escalation is inevitable ────

    @Test
    void changedMismatchingValuesDoNotRestartTheDeadline() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        // Value changed (39 instead of 40) but still mismatched — must not reset firstMismatchTick.
        var atTPlusOne = tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(39, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, atTPlusOne.classification());
        assertEquals(10L, atTPlusOne.firstMismatchTick());
        var atTPlusTwo = tracker.observe(MANA, 12, ClientResourceParityOutcome.MISMATCH, scalar(38, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, atTPlusTwo.classification());
        assertEquals(10L, atTPlusTwo.firstMismatchTick());
    }

    @Test
    void repeatedIdenticalMismatchDoesNotRestartTheDeadline() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        var atTPlusTwo = tracker.observe(MANA, 12, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, atTPlusTwo.classification());
        assertEquals(10L, atTPlusTwo.firstMismatchTick());
    }

    @Test
    void continuousMismatchCannotRemainTransitionalForever() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 0, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        ClientResourceParityObservation last = null;
        for (long tick = 1; tick <= 50; tick++) {
            last = tracker.observe(MANA, tick, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        }
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, last.classification());
    }

    // ── 34: escalation via advanceDeadline() alone, no fresh source notification ─────────────────

    @Test
    void advanceDeadlineEscalatesWithoutAnyNewSourceNotification() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        var atTPlusOne = tracker.advanceDeadline(MANA, 11, false).orElseThrow();
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, atTPlusOne.classification());
        var atTPlusTwo = tracker.advanceDeadline(MANA, 12, false).orElseThrow();
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, atTPlusTwo.classification());
    }

    @Test
    void advanceDeadlineOnUnobservedResourceReturnsEmpty() {
        var tracker = new ClientResourceParityTracker();
        assertTrue(tracker.advanceDeadline(MANA, 5, false).isEmpty());
    }

    @Test
    void advanceDeadlineOnNonMismatchStateIsANoOp() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MATCH, scalar(1, 1), scalar(1, 1), false);
        var advanced = tracker.advanceDeadline(MANA, 20, false).orElseThrow();
        assertEquals(ClientResourceParityClassification.EXACT_MATCH, advanced.classification());
        assertEquals(10L, advanced.lastObservedTick());
    }

    // ── 35-37: PENDING_RESYNC freeze ─────────────────────────────────────────────────────────────

    @Test
    void pendingResyncFreezesGraceProgression() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        var frozenOnce = tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), true);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, frozenOnce.classification());
        assertTrue(frozenOnce.graceFrozen());
        var frozenTwice = tracker.observe(MANA, 12, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), true);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, frozenTwice.classification());
    }

    @Test
    void matchWhilePendingResyncClearsTheMismatch() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), true);
        var matched = tracker.observe(MANA, 12, ClientResourceParityOutcome.MATCH, scalar(42, 100), scalar(42, 100), true);
        assertEquals(ClientResourceParityClassification.EXACT_MATCH, matched.classification());
    }

    @Test
    void graceResumesFromRemainingDurationAfterPendingResyncEnds() {
        // External-review correction 3: the specific frozen-to-unfrozen transition call itself must
        // consume zero newly-consumed grace (it only establishes a fresh timing baseline) — it is
        // NOT counted as "the first of the remaining grace ticks." Only strictly subsequent
        // still-unfrozen calls accumulate normally from that baseline.
        var tracker = new ClientResourceParityTracker();
        // T=10: mismatch begins, not frozen.
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        // T=11, T=12: frozen — two real ticks pass but consume no grace.
        tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), true);
        var stillFrozen = tracker.observe(MANA, 12, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), true);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, stillFrozen.classification());
        // T=13: the resumption call itself — consumes zero grace, only sets the new baseline.
        var resumptionCall = tracker.observe(MANA, 13, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, resumptionCall.classification());
        // T=14: the first grace tick actually consumed since the resumption baseline.
        var afterOneRealTick = tracker.observe(MANA, 14, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, afterOneRealTick.classification());
        // T=15: the second grace tick — now escalates.
        var afterTwoRealTicks = tracker.observe(MANA, 15, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, afterTwoRealTicks.classification());
    }

    // ── 38-39: lifecycle clear ───────────────────────────────────────────────────────────────────

    @Test
    void clearAllRemovesEveryObservationAndDeadline() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        tracker.observe(STAMINA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        tracker.clearAll();
        assertTrue(tracker.latest(MANA).isEmpty());
        assertTrue(tracker.latest(STAMINA).isEmpty());
        // A fresh mismatch after a full clear must start an entirely new grace period.
        var fresh = tracker.observe(MANA, 100, ClientResourceParityOutcome.MISMATCH, scalar(1, 2), scalar(2, 2), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, fresh.classification());
        assertEquals(100L, fresh.firstMismatchTick());
    }

    @Test
    void perResourceClearAffectsOnlyThatResource() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        tracker.observe(STAMINA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        tracker.clear(MANA);
        assertTrue(tracker.latest(MANA).isEmpty());
        assertTrue(tracker.latest(STAMINA).isPresent());
    }

    // ── 40: monotonicity ─────────────────────────────────────────────────────────────────────────

    @Test
    void decreasingTickIsSafelyIgnored() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        var ignored = tracker.observe(MANA, 5, ClientResourceParityOutcome.MATCH, scalar(42, 100), scalar(42, 100), false);
        // The earlier (still-mismatched) state must be preserved, not overwritten by the out-of-order call.
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, ignored.classification());
        assertEquals(10L, ignored.lastObservedTick());
    }

    @Test
    void negativeTickIsRejected() {
        var tracker = new ClientResourceParityTracker();
        assertThrows(IllegalArgumentException.class,
                () -> tracker.observe(MANA, -1, ClientResourceParityOutcome.MISMATCH, scalar(1, 2), scalar(2, 2), false));
    }

    // ── 41-42: immutability and boundedness ──────────────────────────────────────────────────────

    @Test
    void latestReturnsASingleImmutableObservationNeverAHistory() {
        var tracker = new ClientResourceParityTracker();
        for (long tick = 0; tick < 25; tick++) {
            tracker.observe(MANA, tick, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        }
        var observation = tracker.latest(MANA).orElseThrow();
        // Exactly one record, reflecting only the latest state — observationCount is a bounded int,
        // not an unbounded per-tick history list.
        assertEquals(25, observation.observationCount());
        assertEquals(24L, observation.lastObservedTick());
    }

    @Test
    void resourcesRequiringRecheckIncludesUnfrozenTransitionalMismatch() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        assertTrue(tracker.resourcesRequiringRecheck().contains(MANA));
    }

    @Test
    void resourcesRequiringRecheckIncludesFrozenTransitionalMismatch() {
        // External-review correction: a frozen transitional mismatch must remain discoverable —
        // excluding it would make the coordinator never notice when PENDING_RESYNC ends.
        var tracker = new ClientResourceParityTracker();
        tracker.observe(STAMINA, 10, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), true);
        assertTrue(tracker.resourcesRequiringRecheck().contains(STAMINA));
    }

    @Test
    void resourcesRequiringRecheckExcludesNonMismatchClassifications() {
        var tracker = new ClientResourceParityTracker();
        Identifier match = Identifier.fromNamespaceAndPath("totality", "spell_slots");
        Identifier notReady = Identifier.fromNamespaceAndPath("totality", "rage");
        tracker.observe(match, 10, ClientResourceParityOutcome.MATCH, scalar(1, 1), scalar(1, 1), false);
        tracker.observe(notReady, 10, ClientResourceParityOutcome.GENERIC_NOT_READY,
                new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET),
                scalar(0, 0), false);
        var required = tracker.resourcesRequiringRecheck();
        assertFalse(required.contains(match));
        assertFalse(required.contains(notReady));
    }

    @Test
    void resourcesRequiringRecheckExcludesModelMismatch() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MODEL_MISMATCH,
                new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.MODEL_MISMATCH),
                scalar(0, 0), false);
        assertFalse(tracker.resourcesRequiringRecheck().contains(MANA));
    }

    @Test
    void resourcesRequiringRecheckExcludesPersistentMismatch() {
        // A persistent mismatch no longer needs continued *deadline* advancement — Phase 3B-2B's
        // full per-tick poll still freshly compares it (through observe(...) directly), not through
        // this discovery set.
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        var persistent = tracker.observe(MANA, 12, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, persistent.classification());
        assertFalse(tracker.resourcesRequiringRecheck().contains(MANA));
    }

    @Test
    void resourcesRequiringRecheckReturnsImmutableSnapshot() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        var required = tracker.resourcesRequiringRecheck();
        assertThrows(UnsupportedOperationException.class, () -> required.add(STAMINA));
    }

    // ── 43-46: non-mismatch classifications never enter/leave grace incorrectly ────────────────

    @Test
    void genericNotReadyClearsObsoleteMismatchTracking() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        var notReady = tracker.observe(MANA, 11, ClientResourceParityOutcome.GENERIC_NOT_READY,
                new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET),
                scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.GENERIC_NOT_READY, notReady.classification());
        assertEquals(null, notReady.firstMismatchTick());

        // A fresh mismatch afterward must start an entirely new episode, proving the old one was
        // actually discarded rather than merely hidden.
        var freshMismatch = tracker.observe(MANA, 12, ClientResourceParityOutcome.MISMATCH, scalar(40, 100), scalar(42, 100), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, freshMismatch.classification());
        assertEquals(12L, freshMismatch.firstMismatchTick());
    }

    @Test
    void expectedSemanticDifferenceNeverEscalates() {
        var tracker = new ClientResourceParityTracker();
        ClientResourceParityObservation last = null;
        for (long tick = 0; tick < 10; tick++) {
            last = tracker.observe(MANA, tick, ClientResourceParityOutcome.EXPECTED_SEMANTIC_DIFFERENCE,
                    new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.NOT_AVAILABLE_TO_PLAYER),
                    scalar(0, 0), false);
        }
        assertEquals(ClientResourceParityClassification.EXPECTED_SEMANTIC_DIFFERENCE, last.classification());
    }

    @Test
    void modelMismatchNeverEntersOrdinaryMismatchGrace() {
        var tracker = new ClientResourceParityTracker();
        ClientResourceParityObservation last = null;
        for (long tick = 0; tick < 10; tick++) {
            last = tracker.observe(MANA, tick, ClientResourceParityOutcome.MODEL_MISMATCH,
                    new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.MODEL_MISMATCH),
                    scalar(0, 0), false);
        }
        assertEquals(ClientResourceParityClassification.MODEL_MISMATCH, last.classification());
        assertEquals(null, last.firstMismatchTick());
    }

    @Test
    void notApplicableNeverEntersGrace() {
        var tracker = new ClientResourceParityTracker();
        ClientResourceParityObservation last = null;
        for (long tick = 0; tick < 10; tick++) {
            last = tracker.observe(MANA, tick, ClientResourceParityOutcome.NOT_APPLICABLE,
                    new ClientResourceParitySummary.Unavailable(ClientResourceUnavailableReason.RESOURCE_UNREGISTERED),
                    scalar(0, 0), false);
        }
        assertEquals(ClientResourceParityClassification.NOT_APPLICABLE, last.classification());
        assertEquals(null, last.firstMismatchTick());
    }

    @Test
    void observationConstructorRejectsMismatchedFirstMismatchTickInvariant() {
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityObservation(
                MANA, ClientResourceParityClassification.EXACT_MATCH,
                scalar(1, 1), scalar(1, 1), 5L, 5, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityObservation(
                MANA, ClientResourceParityClassification.TRANSITIONAL_MISMATCH,
                scalar(1, 1), scalar(1, 1), null, 5, 1, false));
    }

    // ── External-review correction 3: resync freeze across skipped ticks ────────────────────────

    @Test
    void skippedFrozenSpanWithZeroGraceConsumedBeforeFreezingDoesNotCountTheGap() {
        var tracker = new ClientResourceParityTracker();
        // Mismatch begins at T=10, not frozen.
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        // Frozen observation at T=11.
        tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), true);
        // No calls at all from T=12 through T=99 — simulating a coordinator that does not poll a
        // frozen resource on every single tick.
        // Fresh (unfrozen) observation at T=100: the resumption call itself must consume zero grace.
        var resumed = tracker.observe(MANA, 100, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, resumed.classification());
        // T=101: exactly one grace tick consumed since the resumption baseline.
        var afterOneMore = tracker.observe(MANA, 101, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, afterOneMore.classification());
        // T=102: second grace tick consumed — now escalates.
        var afterTwoMore = tracker.observe(MANA, 102, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, afterTwoMore.classification());
    }

    @Test
    void skippedFrozenSpanWithOneGraceTickConsumedBeforeFreezingPreservesThatOneTick() {
        var tracker = new ClientResourceParityTracker();
        // Mismatch begins at T=10, not frozen.
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        // T=11: still unfrozen — one real grace tick elapses and is consumed.
        var atEleven = tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, atEleven.classification());
        // T=12: freezes.
        tracker.observe(MANA, 12, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), true);
        // No calls at all from T=13 through T=99.
        // T=100: resumption call — must remain at exactly one consumed grace tick, not add the
        // 88-tick frozen gap.
        var resumed = tracker.observe(MANA, 100, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, resumed.classification());
        // T=101: the second grace tick — now reaches the deadline.
        var afterOneMore = tracker.observe(MANA, 101, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, afterOneMore.classification());
    }

    @Test
    void frozenPersistentMismatchRemainsPersistentAcrossASkippedSpan() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        var persistent = tracker.observe(MANA, 12, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, persistent.classification());
        // Freeze begins, then a long skipped span, then resumes — must remain persistent throughout,
        // never regress to transitional merely because grace bookkeeping paused.
        tracker.observe(MANA, 13, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), true);
        var stillPersistentAfterResume = tracker.observe(MANA, 500, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, stillPersistentAfterResume.classification());
    }

    @Test
    void exactMatchClearsAMismatchEvenWhileFrozen() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), true);
        var matched = tracker.observe(MANA, 12, ClientResourceParityOutcome.MATCH, scalar(2, 100), scalar(2, 100), true);
        assertEquals(ClientResourceParityClassification.EXACT_MATCH, matched.classification());
        assertFalse(matched.graceFrozen());
    }

    @Test
    void decreasingTickStillSafelyIgnoredAfterAFreezeResumption() {
        var tracker = new ClientResourceParityTracker();
        tracker.observe(MANA, 10, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        tracker.observe(MANA, 11, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), true);
        var resumed = tracker.observe(MANA, 100, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        var ignored = tracker.observe(MANA, 50, ClientResourceParityOutcome.MATCH, scalar(2, 100), scalar(2, 100), false);
        assertEquals(resumed.classification(), ignored.classification());
        assertEquals(100L, ignored.lastObservedTick());
    }

    // ── External-review correction 5: strengthened observation invariants ──────────────────────

    @Test
    void exactMatchWithGraceFrozenTrueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityObservation(
                MANA, ClientResourceParityClassification.EXACT_MATCH,
                scalar(1, 1), scalar(1, 1), null, 5, 1, true));
    }

    @Test
    void negativeFirstMismatchTickIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityObservation(
                MANA, ClientResourceParityClassification.TRANSITIONAL_MISMATCH,
                scalar(1, 1), scalar(1, 1), -1L, 5, 1, false));
    }

    @Test
    void firstMismatchTickAfterLastObservedTickIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ClientResourceParityObservation(
                MANA, ClientResourceParityClassification.TRANSITIONAL_MISMATCH,
                scalar(1, 1), scalar(1, 1), 6L, 5, 1, false));
    }

    @Test
    void validFrozenTransitionalObservationIsAccepted() {
        var observation = new ClientResourceParityObservation(
                MANA, ClientResourceParityClassification.TRANSITIONAL_MISMATCH,
                scalar(1, 1), scalar(1, 1), 5L, 5, 1, true);
        assertTrue(observation.graceFrozen());
    }

    @Test
    void validFrozenPersistentObservationIsAccepted() {
        var observation = new ClientResourceParityObservation(
                MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH,
                scalar(1, 1), scalar(1, 1), 3L, 5, 1, true);
        assertTrue(observation.graceFrozen());
    }

    // ── External-review correction 6: saturating observationCount ───────────────────────────────
    //
    // The saturating-increment helper is exercised directly (package-private access, no reflection)
    // rather than by driving billions of real tracker observations, per the task's explicit
    // "do not attempt billions of loop iterations" instruction.

    @Test
    void saturatingIncrementIncrementsOrdinaryValuesNormally() {
        assertEquals(1, ClientResourceParityTracker.saturatingIncrement(0));
        assertEquals(43, ClientResourceParityTracker.saturatingIncrement(42));
    }

    @Test
    void saturatingIncrementStopsExactlyAtIntegerMaxValue() {
        assertEquals(Integer.MAX_VALUE, ClientResourceParityTracker.saturatingIncrement(Integer.MAX_VALUE - 1));
    }

    @Test
    void saturatingIncrementNeverWrapsNegativeOnceAtTheCeiling() {
        assertEquals(Integer.MAX_VALUE, ClientResourceParityTracker.saturatingIncrement(Integer.MAX_VALUE));
    }

    @Test
    void mismatchObservationCountAtIntegerMaxValueIsARepresentableObservation() {
        // A hand-constructed observation already at Integer.MAX_VALUE proves the ceiling is a valid,
        // representable state — the tracker's applyMismatch continuing-branch routes its increment
        // through the same saturatingIncrement helper verified above, so it can never exceed this.
        var atCeiling = new ClientResourceParityObservation(
                MANA, ClientResourceParityClassification.TRANSITIONAL_MISMATCH,
                scalar(1, 1), scalar(1, 1), 0L, 0, Integer.MAX_VALUE, false);
        assertEquals(Integer.MAX_VALUE, atCeiling.observationCount());
    }

    @Test
    void nonMismatchObservationCountAtIntegerMaxValueIsARepresentableObservation() {
        var atCeiling = new ClientResourceParityObservation(
                MANA, ClientResourceParityClassification.MODEL_MISMATCH,
                scalar(1, 1), scalar(1, 1), null, 0, Integer.MAX_VALUE, false);
        assertEquals(Integer.MAX_VALUE, atCeiling.observationCount());
    }

    @Test
    void observationCountIncrementsAcrossOrdinaryBoundedRunsWithoutBecomingNegative() {
        var tracker = new ClientResourceParityTracker();
        ClientResourceParityObservation last = null;
        for (long tick = 0; tick < 1000; tick++) {
            last = tracker.observe(MANA, tick, ClientResourceParityOutcome.MISMATCH, scalar(1, 100), scalar(2, 100), false);
        }
        assertTrue(last.observationCount() > 0);
    }
}

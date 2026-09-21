package zcylas.totality.api.rpg.resources.client.parity;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers Phase 3B-2B test-plan items 26-36 (plus the tick-counter overflow item 28): the pure
 *  tick-counter and local-player-identity lifecycle state machine, driven entirely with plain
 *  {@link Object} identity tokens — no Minecraft {@code LocalPlayer} or client is needed. */
class ClientResourceParityLifecycleTest {

    private static final Identifier MANA = Identifier.fromNamespaceAndPath("totality", "mana");

    @Test
    void firstPollUsesTickZero() {
        var lifecycle = new ClientResourceParityLifecycle<Object>(new ClientResourceParityTracker());
        Object player = new Object();
        assertEquals(0L, lifecycle.beginTick(player).orElseThrow());
    }

    @Test
    void tickIncrementsOncePerPoll() {
        var lifecycle = new ClientResourceParityLifecycle<Object>(new ClientResourceParityTracker());
        Object player = new Object();
        assertEquals(0L, lifecycle.beginTick(player).orElseThrow());
        assertEquals(1L, lifecycle.beginTick(player).orElseThrow());
        assertEquals(2L, lifecycle.beginTick(player).orElseThrow());
    }

    @Test
    void tickOverflowIsSafelyHandled() {
        assertEquals(Long.MAX_VALUE, ClientResourceParityLifecycle.safeIncrementTick(Long.MAX_VALUE - 1));
        assertEquals(Long.MAX_VALUE, ClientResourceParityLifecycle.safeIncrementTick(Long.MAX_VALUE));
    }

    @Test
    void preservesEstablishedTrackerOffByOneSemanticsAcrossRealPolls() {
        var tracker = new ClientResourceParityTracker();
        var lifecycle = new ClientResourceParityLifecycle<Object>(tracker);
        Object player = new Object();

        long t0 = lifecycle.beginTick(player).orElseThrow();
        var first = tracker.observe(MANA, t0, ClientResourceParityOutcome.MISMATCH,
                new ClientResourceParitySummary.Scalar(1, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(2, 100, 0, 1), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, first.classification());

        long t1 = lifecycle.beginTick(player).orElseThrow();
        var second = tracker.observe(MANA, t1, ClientResourceParityOutcome.MISMATCH,
                new ClientResourceParitySummary.Scalar(1, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(2, 100, 0, 1), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, second.classification());

        long t2 = lifecycle.beginTick(player).orElseThrow();
        var third = tracker.observe(MANA, t2, ClientResourceParityOutcome.MISMATCH,
                new ClientResourceParitySummary.Scalar(1, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(2, 100, 0, 1), false);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, third.classification());
    }

    @Test
    void joinResetEquivalentClearsObservations() {
        var tracker = new ClientResourceParityTracker();
        var lifecycle = new ClientResourceParityLifecycle<Object>(tracker);
        Object player = new Object();
        long t0 = lifecycle.beginTick(player).orElseThrow();
        tracker.observe(MANA, t0, ClientResourceParityOutcome.MISMATCH,
                new ClientResourceParitySummary.Scalar(1, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(2, 100, 0, 1), false);

        lifecycle.clear(); // stand-in for a JOIN/DISCONNECT/dimension-change reset

        assertTrue(tracker.latest(MANA).isEmpty());
    }

    @Test
    void disconnectResetEquivalentClearsObservationsAndTickCounter() {
        var tracker = new ClientResourceParityTracker();
        var lifecycle = new ClientResourceParityLifecycle<Object>(tracker);
        Object player = new Object();
        lifecycle.beginTick(player);
        lifecycle.beginTick(player);

        lifecycle.clear();

        assertEquals(0L, lifecycle.beginTick(player).orElseThrow());
    }

    @Test
    void dimensionChangeResetEquivalentClearsObservations() {
        var tracker = new ClientResourceParityTracker();
        var lifecycle = new ClientResourceParityLifecycle<Object>(tracker);
        Object player = new Object();
        long t0 = lifecycle.beginTick(player).orElseThrow();
        tracker.observe(MANA, t0, ClientResourceParityOutcome.MATCH,
                new ClientResourceParitySummary.Scalar(1, 1, 0, 1),
                new ClientResourceParitySummary.Scalar(1, 1, 0, 1), false);

        lifecycle.clear();

        assertTrue(tracker.latest(MANA).isEmpty());
    }

    @Test
    void localPlayerObjectReplacementClearsObservations() {
        var tracker = new ClientResourceParityTracker();
        var lifecycle = new ClientResourceParityLifecycle<Object>(tracker);
        Object firstPlayer = new Object();
        long t0 = lifecycle.beginTick(firstPlayer).orElseThrow();
        tracker.observe(MANA, t0, ClientResourceParityOutcome.MISMATCH,
                new ClientResourceParitySummary.Scalar(1, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(2, 100, 0, 1), false);

        Object secondPlayer = new Object(); // a different reference — e.g. respawn
        long resumedTick = lifecycle.beginTick(secondPlayer).orElseThrow();

        assertTrue(tracker.latest(MANA).isEmpty());
        assertEquals(0L, resumedTick); // fresh baseline, not a continuation of the old counter
    }

    @Test
    void sameLocalPlayerObjectDoesNotClearObservations() {
        var tracker = new ClientResourceParityTracker();
        var lifecycle = new ClientResourceParityLifecycle<Object>(tracker);
        Object player = new Object();
        long t0 = lifecycle.beginTick(player).orElseThrow();
        tracker.observe(MANA, t0, ClientResourceParityOutcome.MISMATCH,
                new ClientResourceParitySummary.Scalar(1, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(2, 100, 0, 1), false);

        long t1 = lifecycle.beginTick(player).orElseThrow(); // same reference

        assertEquals(1L, t1);
        assertTrue(tracker.latest(MANA).isPresent());
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH,
                tracker.latest(MANA).orElseThrow().classification());
    }

    @Test
    void nullPlayerEntersIdleStateWithoutPolling() {
        var lifecycle = new ClientResourceParityLifecycle<Object>(new ClientResourceParityTracker());
        assertTrue(lifecycle.beginTick(null).isEmpty());
    }

    @Test
    void repeatedNullPlayerTicksDoNotRepeatedlyResetUnnecessarily() {
        var tracker = new ClientResourceParityTracker();
        var lifecycle = new ClientResourceParityLifecycle<Object>(tracker);
        Object player = new Object();
        long t0 = lifecycle.beginTick(player).orElseThrow();
        tracker.observe(MANA, t0, ClientResourceParityOutcome.MISMATCH,
                new ClientResourceParitySummary.Scalar(1, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(2, 100, 0, 1), false);

        // Transition to null: one reset.
        assertTrue(lifecycle.beginTick(null).isEmpty());
        assertTrue(tracker.latest(MANA).isEmpty());

        // Observe something new while "idle" would not normally happen in production, but confirm
        // repeated null calls remain idle without incident (no exception, no further state change).
        assertTrue(lifecycle.beginTick(null).isEmpty());
        assertTrue(lifecycle.beginTick(null).isEmpty());
    }

    @Test
    void newPlayerAfterNullStateStartsFromACleanBaseline() {
        var tracker = new ClientResourceParityTracker();
        var lifecycle = new ClientResourceParityLifecycle<Object>(tracker);
        Object player = new Object();
        lifecycle.beginTick(player);
        lifecycle.beginTick(player);
        lifecycle.beginTick(null); // disconnect

        Object newPlayer = new Object();
        long tick = lifecycle.beginTick(newPlayer).orElseThrow();

        assertEquals(0L, tick);
        assertTrue(tracker.latest(MANA).isEmpty());
    }

    @Test
    void observationLookupIsReadOnly() {
        var tracker = new ClientResourceParityTracker();
        var lifecycle = new ClientResourceParityLifecycle<Object>(tracker);
        Object player = new Object();
        long t0 = lifecycle.beginTick(player).orElseThrow();
        var observation = tracker.observe(MANA, t0, ClientResourceParityOutcome.MATCH,
                new ClientResourceParitySummary.Scalar(1, 1, 0, 1),
                new ClientResourceParitySummary.Scalar(1, 1, 0, 1), false);
        var relookup = tracker.latest(MANA).orElseThrow();
        assertEquals(observation, relookup);
    }
}

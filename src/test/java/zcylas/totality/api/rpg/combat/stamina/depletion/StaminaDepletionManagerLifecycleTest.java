package zcylas.totality.api.rpg.combat.stamina.depletion;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real, executing lifecycle tests for {@link StaminaDepletionManager}'s renamed transition/
 * penalty-bookkeeping logic. These run against the UUID-keyed test hooks ({@code seedForTest}/
 * {@code advanceForTest}/etc.), not a real {@code ServerPlayer} — the manager's production API
 * only ever needs a player's UUID plus its current/max Stamina internally, and constructing a
 * real {@code ServerPlayer} requires a bootstrapped, unfrozen Minecraft registry — unreachable
 * under plain JUnit (see {@code HealingPotionItemContractTest}'s class Javadoc for the same
 * constraint documented elsewhere in this codebase). This is genuine behavioral proof of the
 * manager's own transition/regen/penalty logic; it is not a sentinel. What it cannot prove is
 * that {@code PlayerConnectionEvents}/{@code StaminaServerTick} correctly wire the
 * {@code ServerPlayer}-taking public API to the right lifecycle events — that wiring is covered
 * by {@link StaminaDepletionSourceMigrationRegressionTest} instead, and ultimately by manual
 * validation.
 */
class StaminaDepletionManagerLifecycleTest {

    @AfterEach
    void clear() {
        StaminaDepletionManager.clearForTest();
    }

    // ── Regeneration multipliers ────────────────────────────────────────────

    @Test
    void normalRegenMultiplierIsFull() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 100, 100);
        assertEquals(1.00f, StaminaDepletionManager.regenMultiplierForTest(player));
    }

    @Test
    void windedRegenMultiplierIsThreeQuarters() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 10, 100);
        assertEquals(0.75f, StaminaDepletionManager.regenMultiplierForTest(player));
    }

    @Test
    void depletedRegenMultiplierIsHalf() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 0, 100);
        assertEquals(0.50f, StaminaDepletionManager.regenMultiplierForTest(player));
    }

    // ── Transitions ──────────────────────────────────────────────────────────

    @Test
    void normalToWindedProducesOneBreathingTransition() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 100, 100);

        StaminaDepletionManager.TransitionEvent event =
                StaminaDepletionManager.advanceForTest(player, 10, 100);

        assertEquals(StaminaDepletionManager.TransitionEvent.ENTERED_WINDED, event);
    }

    @Test
    void remainingWindedProducesNoRepeatedBreathingTransition() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 100, 100);
        StaminaDepletionManager.advanceForTest(player, 10, 100);

        StaminaDepletionManager.TransitionEvent event =
                StaminaDepletionManager.advanceForTest(player, 9, 100);

        assertEquals(StaminaDepletionManager.TransitionEvent.NONE, event);
    }

    @Test
    void windedToDepletedProducesOneDepletedNotification() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 10, 100);

        StaminaDepletionManager.TransitionEvent event =
                StaminaDepletionManager.advanceForTest(player, 0, 100);

        assertEquals(StaminaDepletionManager.TransitionEvent.ENTERED_DEPLETED, event);
    }

    @Test
    void remainingDepletedProducesNoRepeatedNotification() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 10, 100);
        StaminaDepletionManager.advanceForTest(player, 0, 100);

        StaminaDepletionManager.TransitionEvent event =
                StaminaDepletionManager.advanceForTest(player, 0, 100);

        assertEquals(StaminaDepletionManager.TransitionEvent.NONE, event);
    }

    @Test
    void depletedToNormalRemovesPenalties() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 10, 100);
        StaminaDepletionManager.advanceForTest(player, 0, 100);
        assertTrue(StaminaDepletionManager.isPenalizedForTest(player));

        StaminaDepletionManager.advanceForTest(player, 100, 100);

        assertFalse(StaminaDepletionManager.isPenalizedForTest(player));
    }

    @Test
    void recoveryProducesOneRecoveryMessage() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 10, 100);
        StaminaDepletionManager.advanceForTest(player, 0, 100);

        StaminaDepletionManager.TransitionEvent event =
                StaminaDepletionManager.advanceForTest(player, 100, 100);

        assertEquals(StaminaDepletionManager.TransitionEvent.RECOVERED_TO_NORMAL, event);
    }

    @Test
    void repeatedNormalTicksProduceNoRecoverySpam() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 10, 100);
        StaminaDepletionManager.advanceForTest(player, 0, 100);
        StaminaDepletionManager.advanceForTest(player, 100, 100);

        StaminaDepletionManager.TransitionEvent event =
                StaminaDepletionManager.advanceForTest(player, 100, 100);

        assertEquals(StaminaDepletionManager.TransitionEvent.NONE, event);
    }

    @Test
    void penaltiesRemainActiveWhileRecoveringThroughWindedAfterDepleted() {
        // Preserves original behavior: penalties stay active until the player is fully back to
        // NORMAL, not merely above zero — recovering DEPLETED -> WINDED alone does not clear them.
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 10, 100);
        StaminaDepletionManager.advanceForTest(player, 0, 100);

        StaminaDepletionManager.advanceForTest(player, 5, 100);

        assertEquals(StaminaDepletionState.WINDED, StaminaDepletionManager.previousStateForTest(player));
        assertTrue(StaminaDepletionManager.isPenalizedForTest(player),
                "penalties must remain active until full recovery to NORMAL");
    }

    // ── Modifiers / bookkeeping ──────────────────────────────────────────────

    @Test
    void windedAloneHasNoPenalty() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 100, 100);
        StaminaDepletionManager.advanceForTest(player, 10, 100);

        assertFalse(StaminaDepletionManager.isPenalizedForTest(player));
    }

    @Test
    void depletedIsPenalized() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 100, 100);
        StaminaDepletionManager.advanceForTest(player, 0, 100);

        assertTrue(StaminaDepletionManager.isPenalizedForTest(player));
    }

    @Test
    void repeatedDepletedTicksDoNotStackPenaltyBookkeeping() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 100, 100);
        StaminaDepletionManager.advanceForTest(player, 0, 100);
        StaminaDepletionManager.advanceForTest(player, 0, 100);
        StaminaDepletionManager.advanceForTest(player, 0, 100);

        // A Set can never "stack" duplicate membership — this proves repeated ticks keep
        // exactly one (idempotent) penalized entry rather than growing unbounded state.
        assertTrue(StaminaDepletionManager.isPenalizedForTest(player));
        assertEquals(StaminaDepletionState.DEPLETED, StaminaDepletionManager.previousStateForTest(player));
    }

    // ── Lifecycle (join / disconnect / rejoin) ────────────────────────────────

    @Test
    void joiningAtZeroDoesNotSendAFalseTransition() {
        UUID player = UUID.randomUUID();

        StaminaDepletionManager.seedForTest(player, 0, 100);

        assertEquals(StaminaDepletionState.DEPLETED, StaminaDepletionManager.previousStateForTest(player));
        assertTrue(StaminaDepletionManager.isPenalizedForTest(player),
                "joining depleted must still seed the transient penalty state");
    }

    @Test
    void joiningWindedDoesNotSendAFalseTransition() {
        UUID player = UUID.randomUUID();

        StaminaDepletionManager.seedForTest(player, 10, 100);

        assertEquals(StaminaDepletionState.WINDED, StaminaDepletionManager.previousStateForTest(player));
        assertFalse(StaminaDepletionManager.isPenalizedForTest(player));
    }

    @Test
    void depletedPenaltiesDoNotStackOnFirstTickAfterJoining() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 0, 100);

        // First ordinary tick after join, stamina unchanged — must not re-fire ENTERED_DEPLETED.
        StaminaDepletionManager.TransitionEvent event =
                StaminaDepletionManager.advanceForTest(player, 0, 100);

        assertEquals(StaminaDepletionManager.TransitionEvent.NONE, event);
        assertTrue(StaminaDepletionManager.isPenalizedForTest(player));
    }

    @Test
    void recoveryAfterJoiningDepletedStillProducesOneValidTransition() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 0, 100);

        StaminaDepletionManager.TransitionEvent event =
                StaminaDepletionManager.advanceForTest(player, 100, 100);

        assertEquals(StaminaDepletionManager.TransitionEvent.RECOVERED_TO_NORMAL, event);
        assertFalse(StaminaDepletionManager.isPenalizedForTest(player));
    }

    @Test
    void disconnectCleanupRemovesAllBookkeeping() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 0, 100);
        assertTrue(StaminaDepletionManager.isPenalizedForTest(player));

        StaminaDepletionManager.clearPlayerForTest(player);

        assertFalse(StaminaDepletionManager.isPenalizedForTest(player));
        assertEquals(StaminaDepletionState.NORMAL, StaminaDepletionManager.previousStateForTest(player));
    }

    @Test
    void rejoinAfterDisconnectStartsFromAFreshBaseline() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 0, 100);
        StaminaDepletionManager.clearPlayerForTest(player);

        // Rejoin at full stamina must seed a plain NORMAL baseline, not carry over DEPLETED.
        StaminaDepletionManager.seedForTest(player, 100, 100);

        assertEquals(StaminaDepletionState.NORMAL, StaminaDepletionManager.previousStateForTest(player));
        assertFalse(StaminaDepletionManager.isPenalizedForTest(player));
    }

    @Test
    void reusingAUuidAfterCleanupDoesNotLeakPriorPenaltyState() {
        UUID player = UUID.randomUUID();
        StaminaDepletionManager.seedForTest(player, 0, 100);
        StaminaDepletionManager.clearPlayerForTest(player);

        StaminaDepletionManager.seedForTest(player, 0, 100);
        StaminaDepletionManager.TransitionEvent event =
                StaminaDepletionManager.advanceForTest(player, 100, 100);

        // Exactly one recovery transition after rejoin — no leftover state doubled it up.
        assertEquals(StaminaDepletionManager.TransitionEvent.RECOVERED_TO_NORMAL, event);
    }
}

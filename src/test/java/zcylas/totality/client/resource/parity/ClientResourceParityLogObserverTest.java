package zcylas.totality.client.resource.parity;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityClassification;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityLogTransition;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityLogTransitionTracker;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityObservation;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityOutcome;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParitySummary;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3B-2C: {@link ClientResourceParityLogObserver} is the thin client-only boundary that reads
 * {@link ClientResourceParityObservations}'s existing snapshot and delegates the actual entry/
 * recovery decision to {@link ClientResourceParityLogTransitionTracker} (exhaustively covered by its
 * own pure unit test). This test runs entirely without a Minecraft client — exactly like {@link
 * ClientResourceParityObservationsTest} — seeding the same-package package-private {@link
 * ClientResourceParityCoordinator#tracker()} accessor directly and calling {@link
 * ClientResourceParityLogObserver#tick()}/{@link ClientResourceParityLogObserver#clear()} rather
 * than {@code Minecraft.getInstance()} or {@code coordinator.tick()}.
 *
 * <p>Covers the original task's required-coverage items 21 (no throw into parity polling), 22
 * (logging integration never changes the stored observation), 23 (no repeated work for a
 * steady-state persistent observation), and 24 (reflection/static checks confirming no UI/command/
 * gameplay-mutation/legacy-manager dependency was added).
 *
 * <p><b>Second external-review correction:</b> the dimension-rebuild regression test now drives
 * {@link ClientResourceParityLogObserver#decide(Identifier, ClientResourceParityObservation)} — the
 * exact package-private decision method {@link ClientResourceParityLogObserver#tick()} itself calls
 * — once per intermediate observation, asserting the precise {@link ClientResourceParityLogTransition}
 * returned at each step. This is deliberate: inferring whether a log occurred only from {@link
 * ClientResourceParityLogObserver#transitions()}'s final active/inactive boolean cannot distinguish
 * "no event happened" from "a false recovery immediately followed by a fresh re-entry" — both leave
 * the same final state.
 */
class ClientResourceParityLogObserverTest {

    @AfterEach
    void clearState() {
        ClientResourceParityCoordinator.tracker().clearAll();
        ClientResourceParityLogObserver.clear();
    }

    private static ClientResourceParityObservation seedMismatchUntilPersistent() {
        var tracker = ClientResourceParityCoordinator.tracker();
        tracker.observe(PlayerResourceIds.MANA, 0, ClientResourceParityOutcome.MISMATCH,
                new ClientResourceParitySummary.Scalar(40, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1), false);
        tracker.observe(PlayerResourceIds.MANA, 1, ClientResourceParityOutcome.MISMATCH,
                new ClientResourceParitySummary.Scalar(40, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1), false);
        return tracker.observe(PlayerResourceIds.MANA, 2, ClientResourceParityOutcome.MISMATCH,
                new ClientResourceParitySummary.Scalar(40, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1), false);
    }

    // ── 21: tick() never throws, even repeatedly, across every classification ─────────────────

    @Test
    void tickNeverThrowsAcrossOrdinaryObservations() {
        seedMismatchUntilPersistent();
        assertDoesNotThrow(ClientResourceParityLogObserver::tick);
        assertDoesNotThrow(ClientResourceParityLogObserver::tick);
    }

    @Test
    void tickNeverThrowsWithNoObservationsAtAll() {
        assertDoesNotThrow(ClientResourceParityLogObserver::tick);
    }

    // ── 22: logging integration never changes the stored parity observation ──────────────────

    @Test
    void tickDoesNotChangeTheStoredObservation() {
        var expected = seedMismatchUntilPersistent();
        ClientResourceParityLogObserver.tick();
        Optional<ClientResourceParityObservation> after = ClientResourceParityCoordinator.tracker().latest(PlayerResourceIds.MANA);
        assertTrue(after.isPresent());
        assertEquals(expected, after.orElseThrow());
    }

    // ── 23: end-to-end entry/recovery/steady-state behavior via the real observation pipeline ─

    @Test
    void tickConsumesTheEntryTransitionSoASubsequentSteadyStateReclassifyIsNone() {
        seedMismatchUntilPersistent(); // becomes PERSISTENT_MISMATCH at tick 2
        ClientResourceParityLogObserver.tick(); // the observer's own tick() consumes the entry here

        ClientResourceParityLogTransitionTracker<Identifier> transitions = ClientResourceParityLogObserver.transitions();
        assertEquals(ClientResourceParityLogTransition.NONE,
                transitions.classify(PlayerResourceIds.MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH),
                "after tick() already processed the entry, re-classifying the same steady-state "
                        + "PERSISTENT_MISMATCH must produce no further transition");
    }

    @Test
    void tickConsumesARecoveryTransitionAfterAPersistentEpisode() {
        seedMismatchUntilPersistent();
        ClientResourceParityLogObserver.tick(); // consumes the entry transition

        ClientResourceParityCoordinator.tracker().observe(PlayerResourceIds.MANA, 3, ClientResourceParityOutcome.MATCH,
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1), false);
        ClientResourceParityLogObserver.tick(); // consumes the recovery transition

        ClientResourceParityLogTransitionTracker<Identifier> transitions = ClientResourceParityLogObserver.transitions();
        assertEquals(ClientResourceParityLogTransition.NONE,
                transitions.classify(PlayerResourceIds.MANA, ClientResourceParityClassification.EXACT_MATCH),
                "the recovery must already have been consumed by tick() — re-classifying the same "
                        + "steady-state EXACT_MATCH afterward must produce no further transition");
    }

    // ── clear() resets transition memory without fabricating a recovery, and represents a genuine ─
    // ── connection reset (external-review correction, regression items 4) ─────────────────────

    @Test
    void clearResetsTransitionMemoryWithoutFabricatingARecovery() {
        seedMismatchUntilPersistent();
        ClientResourceParityLogObserver.tick(); // consumes the entry transition
        ClientResourceParityLogObserver.clear();

        // After clear(), the transition tracker's memory is gone — the next classify() for the same
        // resource id, even with a non-persistent classification, must not read as "recovered".
        ClientResourceParityLogTransitionTracker<Identifier> transitions = ClientResourceParityLogObserver.transitions();
        assertEquals(ClientResourceParityLogTransition.NONE,
                transitions.classify(PlayerResourceIds.MANA, ClientResourceParityClassification.EXACT_MATCH));
    }

    @Test
    void explicitClearRepresentsAConnectionResetSoTheNextPersistentObservationIsTreatedAsANewEntry() {
        // Regression item 4: explicit clear() (JOIN/DISCONNECT) clears diagnostic episode memory,
        // fabricates no recovery, and a persistent observation in the "new connection" that follows
        // is treated as a brand-new entry — never suppressed as though it were a continuation.
        seedMismatchUntilPersistent();
        ClientResourceParityLogObserver.tick(); // consumes the entry transition for the old connection
        ClientResourceParityLogObserver.clear(); // simulates JOIN/DISCONNECT

        ClientResourceParityLogTransitionTracker<Identifier> transitions = ClientResourceParityLogObserver.transitions();
        assertEquals(ClientResourceParityLogTransition.NONE,
                transitions.classify(PlayerResourceIds.MANA, ClientResourceParityClassification.EXACT_MATCH),
                "clear() must never fabricate a recovery for a resource it forgot");
        assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH,
                transitions.classify(PlayerResourceIds.MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH),
                "a persistent observation after an explicit clear() must be treated as a new episode, "
                        + "never as a continuation of the old connection's episode");
    }

    // ── Second external-review correction: the observer-level regression must exercise the real ──
    // ── per-observation decision path — ClientResourceParityLogObserver.decide(...), the exact ────
    // ── method tick() itself calls — after EVERY intermediate observation of a dimension-change ──
    // ── rebuild, not just before/after a single batched tick(). The previous version of this test ──
    // ── batched three observe(...) calls and checked only the final state via transitions().classify(),
    // ── which cannot distinguish "no event occurred" from "a false recovery immediately followed by
    // ── a fresh re-entry" — both leave the same final active/inactive boolean. This replacement
    // ── captures the exact ClientResourceParityLogTransition returned at each step instead. ────────

    @Test
    void dimensionRebuildSequenceDecidedStepByStepProducesNoFalseRecoveryAndNoDuplicateEntryThenExactlyOneRealRecovery() {
        var tracker = ClientResourceParityCoordinator.tracker();

        // Step 1: consume the original persistent entry.
        ClientResourceParityObservation initialPersistent = seedMismatchUntilPersistent(); // tick 0,1,2 -> PERSISTENT_MISMATCH
        assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH,
                ClientResourceParityLogObserver.decide(PlayerResourceIds.MANA, initialPersistent),
                "the original persistent episode must produce exactly one entry");

        // Step 2: clear only the parity OBSERVATION tracker — never ClientResourceParityLogObserver
        // itself — exactly matching what ClientResourceParityCoordinator.clear() alone does on a
        // dimension change per the corrected TotalityClient.java wiring (AFTER_CLIENT_LEVEL_CHANGE
        // clears the coordinator's tracker only, never the log observer's episode memory).
        tracker.clearAll();

        // Steps 3-5: rebuild/seed GENERIC_NOT_READY, process one observer decision, assert no recovery.
        ClientResourceParityObservation notReady = tracker.observe(PlayerResourceIds.MANA, 0, ClientResourceParityOutcome.GENERIC_NOT_READY,
                new ClientResourceParitySummary.Unavailable(zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET),
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1), false);
        assertEquals(ClientResourceParityLogTransition.NONE,
                ClientResourceParityLogObserver.decide(PlayerResourceIds.MANA, notReady),
                "GENERIC_NOT_READY during a tracker rebuild must never be logged as a recovery");

        // Steps 6-8: rebuild/seed TRANSITIONAL_MISMATCH, process one observer decision, assert no recovery.
        ClientResourceParityObservation freshMismatch = tracker.observe(PlayerResourceIds.MANA, 1, ClientResourceParityOutcome.MISMATCH,
                new ClientResourceParitySummary.Scalar(40, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, freshMismatch.classification());
        assertEquals(ClientResourceParityLogTransition.NONE,
                ClientResourceParityLogObserver.decide(PlayerResourceIds.MANA, freshMismatch),
                "TRANSITIONAL_MISMATCH during a tracker rebuild must never be logged as a recovery");

        // One more tick still within grace — still TRANSITIONAL_MISMATCH — the real observer would
        // tick here too; decide() must still be NONE.
        ClientResourceParityObservation stillTransitional = tracker.observe(PlayerResourceIds.MANA, 2, ClientResourceParityOutcome.MISMATCH,
                new ClientResourceParitySummary.Scalar(40, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1), false);
        assertEquals(ClientResourceParityClassification.TRANSITIONAL_MISMATCH, stillTransitional.classification());
        assertEquals(ClientResourceParityLogTransition.NONE,
                ClientResourceParityLogObserver.decide(PlayerResourceIds.MANA, stillTransitional));

        // Steps 9-11: rebuild/seed PERSISTENT_MISMATCH (grace deadline reached), process one observer
        // decision, assert no duplicate entry.
        ClientResourceParityObservation rebuiltPersistent = tracker.observe(PlayerResourceIds.MANA, 3, ClientResourceParityOutcome.MISMATCH,
                new ClientResourceParitySummary.Scalar(40, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1), false);
        assertEquals(ClientResourceParityClassification.PERSISTENT_MISMATCH, rebuiltPersistent.classification());
        assertEquals(ClientResourceParityLogTransition.NONE,
                ClientResourceParityLogObserver.decide(PlayerResourceIds.MANA, rebuiltPersistent),
                "the rebuilt PERSISTENT_MISMATCH must never be logged as a second entry — it is the same continuing episode");

        // Steps 12-14: rebuild/seed EXACT_MATCH (e.g. respawn restoring the legacy mirror), process
        // one observer decision, assert exactly one recovery.
        ClientResourceParityObservation recovered = tracker.observe(PlayerResourceIds.MANA, 4, ClientResourceParityOutcome.MATCH,
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1),
                new ClientResourceParitySummary.Scalar(100, 100, 0, 1), false);
        assertEquals(ClientResourceParityLogTransition.RECOVERED_FROM_PERSISTENT_MISMATCH,
                ClientResourceParityLogObserver.decide(PlayerResourceIds.MANA, recovered),
                "the eventual real recovery must produce exactly one recovery event");

        // Steps 15-16: repeated EXACT_MATCH observations, assert no duplicate recovery.
        for (int i = 0; i < 10; i++) {
            ClientResourceParityObservation steadyExact = tracker.observe(PlayerResourceIds.MANA, 5 + i, ClientResourceParityOutcome.MATCH,
                    new ClientResourceParitySummary.Scalar(100, 100, 0, 1),
                    new ClientResourceParitySummary.Scalar(100, 100, 0, 1), false);
            assertEquals(ClientResourceParityLogTransition.NONE,
                    ClientResourceParityLogObserver.decide(PlayerResourceIds.MANA, steadyExact),
                    "steady-state EXACT_MATCH after the recovery must never log again");
        }
    }

    // ── Required-coverage item 7: all four production Identifier keys work through the typed ────
    // ── client observer/tracker integration ────────────────────────────────────────────────────

    @Test
    void allFourProductionResourceIdsAreTrackedThroughTheTypedObserver() {
        var tracker = ClientResourceParityCoordinator.tracker();
        for (Identifier id : new Identifier[] {
                PlayerResourceIds.MANA, PlayerResourceIds.STAMINA, PlayerResourceIds.SPELL_SLOTS, PlayerResourceIds.RAGE
        }) {
            tracker.observe(id, 0, ClientResourceParityOutcome.MISMATCH,
                    new ClientResourceParitySummary.Scalar(1, 10, 0, 1),
                    new ClientResourceParitySummary.Scalar(9, 10, 0, 1), false);
            tracker.observe(id, 1, ClientResourceParityOutcome.MISMATCH,
                    new ClientResourceParitySummary.Scalar(1, 10, 0, 1),
                    new ClientResourceParitySummary.Scalar(9, 10, 0, 1), false);
            tracker.observe(id, 2, ClientResourceParityOutcome.MISMATCH,
                    new ClientResourceParitySummary.Scalar(1, 10, 0, 1),
                    new ClientResourceParitySummary.Scalar(9, 10, 0, 1), false);
        }

        ClientResourceParityLogObserver.tick(); // processes all four resources in one pass

        ClientResourceParityLogTransitionTracker<Identifier> transitions = ClientResourceParityLogObserver.transitions();
        for (Identifier id : new Identifier[] {
                PlayerResourceIds.MANA, PlayerResourceIds.STAMINA, PlayerResourceIds.SPELL_SLOTS, PlayerResourceIds.RAGE
        }) {
            assertEquals(ClientResourceParityLogTransition.NONE,
                    transitions.classify(id, ClientResourceParityClassification.PERSISTENT_MISMATCH),
                    "expected " + id + "'s entry to have already been consumed by the one tick() pass");
        }
    }

    // ── Required-coverage item 5: TotalityClient registers observer clear() for JOIN/DISCONNECT ──
    // ── only — never for AFTER_CLIENT_LEVEL_CHANGE (external-review correction) ────────────────

    @Test
    void totalityClientRegistersObserverClearOnlyOnJoinAndDisconnectNeverOnDimensionChange() throws Exception {
        Path path = Path.of("src/main/java/zcylas/totality/TotalityClient.java");
        if (!Files.exists(path)) {
            return; // source not available in this environment; skip defensively
        }
        // Normalized to LF-only: the repository checks out this file with CRLF line endings
        // (core.autocrlf=true), so a raw embedded "\n" in an expected substring below would never
        // match the file's actual "\r\n" — normalize once rather than embedding "\r?\n" everywhere.
        String source = Files.readString(path).replace("\r\n", "\n");

        String callToken = "ClientResourceParityLogObserver.clear()";
        long occurrences = countOccurrences(source, callToken);
        assertEquals(2, occurrences,
                "expected ClientResourceParityLogObserver.clear() to be registered exactly twice (JOIN, DISCONNECT)");

        // Every AFTER_CLIENT_LEVEL_CHANGE.register(...) call's own statement (up to its closing
        // ");") must never mention the log observer's clear() — only the parity coordinator's own
        // clear() may appear there.
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("AFTER_CLIENT_LEVEL_CHANGE\\.register\\((?:.|\\R)*?\\)\\);")
                .matcher(source);
        boolean foundAtLeastOneRegistration = false;
        while (matcher.find()) {
            foundAtLeastOneRegistration = true;
            String registrationStatement = matcher.group();
            assertTrue(!registrationStatement.contains("ClientResourceParityLogObserver"),
                    "AFTER_CLIENT_LEVEL_CHANGE must never register ClientResourceParityLogObserver.clear(): "
                            + registrationStatement);
        }
        assertTrue(foundAtLeastOneRegistration,
                "expected at least one AFTER_CLIENT_LEVEL_CHANGE registration (the parity coordinator's own) to exist");

        // The JOIN and DISCONNECT registration blocks must each still contain the observer clear.
        assertTrue(source.contains("ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->\n"
                + "                zcylas.totality.client.resource.parity.ClientResourceParityLogObserver.clear());"));
        assertTrue(source.contains("ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->\n"
                + "                zcylas.totality.client.resource.parity.ClientResourceParityLogObserver.clear());"));
    }

    private static long countOccurrences(String source, String token) {
        long count = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) != -1) {
            count++;
            index += token.length();
        }
        return count;
    }

    // ── 24: reflection/static checks — no UI, command, gameplay mutation, legacy-manager write ─

    @Test
    void observerExposesOnlyTickClearAndPackagePrivateTransitionsAccessor() {
        List<Method> declared = Arrays.asList(ClientResourceParityLogObserver.class.getDeclaredMethods());
        List<Method> publicMethods = declared.stream().filter(m -> Modifier.isPublic(m.getModifiers())).toList();
        assertEquals(2, publicMethods.size(), "expected exactly tick() and clear() to be public");
        assertTrue(publicMethods.stream().anyMatch(m -> m.getName().equals("tick")));
        assertTrue(publicMethods.stream().anyMatch(m -> m.getName().equals("clear")));

        Method transitionsAccessor = declared.stream()
                .filter(m -> m.getName().equals("transitions"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a package-private transitions() accessor"));
        assertFalse(Modifier.isPublic(transitionsAccessor.getModifiers()),
                "transitions() must never be public — only this package's own tests may call it directly");

        // Second external-review correction: decide(...) is the new package-private deterministic
        // seam tick() itself uses — it must never become a public API either, and there must be
        // exactly one such method (no second, competing decision entry point).
        List<Method> decideMethods = declared.stream().filter(m -> m.getName().equals("decide")).toList();
        assertEquals(1, decideMethods.size(), "expected exactly one decide(...) method");
        assertFalse(Modifier.isPublic(decideMethods.get(0).getModifiers()),
                "decide(...) must never be public — only this package's own tests may call it directly");
    }

    @Test
    void observerImportsNoHudCommandOrLegacyManagerType() {
        String source = readSourceIfAvailable();
        if (source == null) {
            return; // source not available in this environment (e.g. jar-only classpath); skip defensively
        }
        List<String> forbidden = List.of(
                "Hud", "Screen", "Command", "ChatComponent",
                "ClientManaManager", "ClientStaminaManager", "ClientSpellSlotManager", "PlayerChargesComponent");
        for (String token : forbidden) {
            assertFalse(source.contains(token), "ClientResourceParityLogObserver must never reference " + token);
        }
    }

    private static String readSourceIfAvailable() {
        try {
            Path path = Path.of("src/main/java/zcylas/totality/client/resource/parity/ClientResourceParityLogObserver.java");
            if (!Files.exists(path)) {
                return null;
            }
            return Files.readString(path);
        } catch (Exception e) {
            return null;
        }
    }
}

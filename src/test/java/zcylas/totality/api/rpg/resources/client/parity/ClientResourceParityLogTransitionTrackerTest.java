package zcylas.totality.api.rpg.resources.client.parity;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3B-2C: {@link ClientResourceParityLogTransitionTracker} is the single owner of the
 * "should this observation log an entry/recovery diagnostic" decision — this test drives that pure
 * decision directly (classification in, {@link ClientResourceParityLogTransition} out), with no
 * dependency on {@link ClientResourceParityTracker}, {@link ClientResourceParityObservation}, or any
 * client/logging boundary. Covers the original task's required-coverage items 1-16, the first
 * external-review correction's generic-key/no-Minecraft-import items, and the second
 * external-review correction's episode-continuation items (GENERIC_NOT_READY/TRANSITIONAL_MISMATCH
 * must not close an active persistent episode — see {@code fullDimensionRebuildSequence*} below).
 */
class ClientResourceParityLogTransitionTrackerTest {

    private static final Identifier MANA = PlayerResourceIds.MANA;
    private static final Identifier STAMINA = PlayerResourceIds.STAMINA;
    private static final Identifier SPELL_SLOTS = PlayerResourceIds.SPELL_SLOTS;
    private static final Identifier RAGE = PlayerResourceIds.RAGE;

    // ── 1-3: ordinary non-persistent classifications alone never log ──────────────────────────

    @Test
    void exactMatchAloneProducesNoTransition() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.EXACT_MATCH));
    }

    @Test
    void transitionalMismatchAloneProducesNoTransition() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.TRANSITIONAL_MISMATCH));
    }

    @Test
    void genericNotReadyAloneProducesNoTransition() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.GENERIC_NOT_READY));
    }

    @Test
    void modelMismatchAloneProducesNoTransition() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.MODEL_MISMATCH));
    }

    @Test
    void expectedSemanticDifferenceAloneProducesNoTransition() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(RAGE, ClientResourceParityClassification.EXPECTED_SEMANTIC_DIFFERENCE));
    }

    @Test
    void notApplicableAloneProducesNoTransition() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.NOT_APPLICABLE));
    }

    // ── 4-5: first entry logs once, repeats do not ─────────────────────────────────────────────

    @Test
    void firstTransitionIntoPersistentMismatchProducesOneEntryEvent() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH,
                tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH));
    }

    @Test
    void repeatedPersistentObservationsProduceNoDuplicateEntryEvent() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH);
        for (int i = 0; i < 20; i++) {
            assertEquals(ClientResourceParityLogTransition.NONE,
                    tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH));
        }
    }

    // ── 6-7: recovery to EXACT_MATCH logs once, repeats do not ─────────────────────────────────

    @Test
    void persistentToExactMatchProducesOneRecoveryEvent() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH);
        assertEquals(ClientResourceParityLogTransition.RECOVERED_FROM_PERSISTENT_MISMATCH,
                tracker.classify(MANA, ClientResourceParityClassification.EXACT_MATCH));
    }

    @Test
    void repeatedExactObservationsAfterRecoveryProduceNoDuplicate() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH);
        tracker.classify(MANA, ClientResourceParityClassification.EXACT_MATCH);
        for (int i = 0; i < 20; i++) {
            assertEquals(ClientResourceParityLogTransition.NONE,
                    tracker.classify(MANA, ClientResourceParityClassification.EXACT_MATCH));
        }
    }

    // ── 8: recovery to a "stable exit" classification also logs once (EXACT_MATCH, MODEL_MISMATCH,
    // ── EXPECTED_SEMANTIC_DIFFERENCE, NOT_APPLICABLE — the four classifications that genuinely close
    // ── an active episode; see the second external-review correction below for GENERIC_NOT_READY/
    // ── TRANSITIONAL_MISMATCH, which do NOT close one) ─────────────────────────────────────────

    @Test
    void persistentToModelMismatchProducesOneRecoveryEvent() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        tracker.classify(RAGE, ClientResourceParityClassification.PERSISTENT_MISMATCH);
        assertEquals(ClientResourceParityLogTransition.RECOVERED_FROM_PERSISTENT_MISMATCH,
                tracker.classify(RAGE, ClientResourceParityClassification.MODEL_MISMATCH));
    }

    @Test
    void persistentToExpectedSemanticDifferenceProducesOneRecoveryEvent() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        tracker.classify(RAGE, ClientResourceParityClassification.PERSISTENT_MISMATCH);
        assertEquals(ClientResourceParityLogTransition.RECOVERED_FROM_PERSISTENT_MISMATCH,
                tracker.classify(RAGE, ClientResourceParityClassification.EXPECTED_SEMANTIC_DIFFERENCE));
    }

    @Test
    void persistentToNotApplicableProducesOneRecoveryEvent() {
        // NOT_APPLICABLE is a stable exit classification for the same reason MODEL_MISMATCH and
        // EXPECTED_SEMANTIC_DIFFERENCE are (§ class Javadoc): the previous persistent numeric-mismatch
        // comparison no longer applies at all — this was not previously covered by a
        // persistent-episode-closing test, only by the "alone" (no active episode) case above.
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH);
        assertEquals(ClientResourceParityLogTransition.RECOVERED_FROM_PERSISTENT_MISMATCH,
                tracker.classify(MANA, ClientResourceParityClassification.NOT_APPLICABLE));
    }

    // ── Second external-review correction: GENERIC_NOT_READY and TRANSITIONAL_MISMATCH do NOT close
    // ── an active persistent episode — they represent observation uncertainty or an ordinary
    // ── within-grace mismatch, not a real recovery. Required-coverage items 1-5. ───────────────

    @Test
    void persistentToGenericNotReadyReturnsNoneAndKeepsTheEpisodeActive() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH);

        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.GENERIC_NOT_READY),
                "GENERIC_NOT_READY must never be treated as a recovery from an active persistent episode");

        // Prove the episode is still active, not merely that this one call returned NONE: an
        // immediate PERSISTENT_MISMATCH re-observation must also return NONE (continuation, not a
        // fresh entry) — if the episode had actually closed, this would incorrectly be ENTERED.
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH),
                "the episode must still be active after a GENERIC_NOT_READY reading");
    }

    @Test
    void persistentToTransitionalMismatchReturnsNoneAndKeepsTheEpisodeActive() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH);

        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.TRANSITIONAL_MISMATCH),
                "TRANSITIONAL_MISMATCH must never be treated as a recovery from an active persistent episode — it is still a mismatch");

        // Same "still active" proof as above, via an EXACT_MATCH this time: if the episode had
        // already closed on the TRANSITIONAL_MISMATCH call, this EXACT_MATCH would incorrectly
        // return NONE (nothing to recover from) instead of RECOVERED_FROM_PERSISTENT_MISMATCH.
        assertEquals(ClientResourceParityLogTransition.RECOVERED_FROM_PERSISTENT_MISMATCH,
                tracker.classify(MANA, ClientResourceParityClassification.EXACT_MATCH),
                "the episode must still be active after a TRANSITIONAL_MISMATCH reading, so this EXACT_MATCH must be a real recovery");
    }

    @Test
    void fullDimensionRebuildSequenceProducesNoRecoveryAndNoDuplicateEntry() {
        // The confirmed live dimension-transfer/return sequence: an active persistent episode
        // survives the parity tracker's rebuild through GENERIC_NOT_READY -> TRANSITIONAL_MISMATCH
        // -> PERSISTENT_MISMATCH for the same continuing mismatch, with no recovery and no second
        // entry logged anywhere in that rebuild.
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH,
                tracker.classify(RAGE, ClientResourceParityClassification.PERSISTENT_MISMATCH));

        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(RAGE, ClientResourceParityClassification.GENERIC_NOT_READY));
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(RAGE, ClientResourceParityClassification.TRANSITIONAL_MISMATCH));
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(RAGE, ClientResourceParityClassification.PERSISTENT_MISMATCH),
                "the rebuilt PERSISTENT_MISMATCH must never be a second entry — it is the same continuing episode");
    }

    @Test
    void fullDimensionRebuildSequenceFollowedByExactMatchProducesExactlyOneRecovery() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        tracker.classify(RAGE, ClientResourceParityClassification.PERSISTENT_MISMATCH);
        tracker.classify(RAGE, ClientResourceParityClassification.GENERIC_NOT_READY);
        tracker.classify(RAGE, ClientResourceParityClassification.TRANSITIONAL_MISMATCH);
        tracker.classify(RAGE, ClientResourceParityClassification.PERSISTENT_MISMATCH);

        assertEquals(ClientResourceParityLogTransition.RECOVERED_FROM_PERSISTENT_MISMATCH,
                tracker.classify(RAGE, ClientResourceParityClassification.EXACT_MATCH),
                "the eventual real recovery (e.g. respawn restoring the legacy mirror) must still produce exactly one recovery event");

        // Repeated EXACT_MATCH afterward must never duplicate the recovery.
        for (int i = 0; i < 10; i++) {
            assertEquals(ClientResourceParityLogTransition.NONE,
                    tracker.classify(RAGE, ClientResourceParityClassification.EXACT_MATCH),
                    "steady-state EXACT_MATCH after the recovery must never log again");
        }
    }

    // ── 9 (original numbering): persistent -> transitional -> persistent stays inside one episode ─

    @Test
    void persistentStaysActiveThroughTransitionalWithoutClosingTheEpisode() {
        // Corrected (second external-review pass): TRANSITIONAL_MISMATCH while a persistent episode
        // is active no longer closes it — this replaces the previous (incorrect)
        // persistentThenTransitionalThenPersistentLogsRecoveryThenEntryAgain test, which asserted
        // TRANSITIONAL_MISMATCH produced a recovery. That assertion encoded the defect this pass
        // fixes: it would have caused a false recovery log every time a dimension-change rebuild
        // passed through TRANSITIONAL_MISMATCH on its way back to PERSISTENT_MISMATCH.
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH,
                tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH));
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.TRANSITIONAL_MISMATCH));
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.TRANSITIONAL_MISMATCH));
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH),
                "returning to PERSISTENT_MISMATCH without ever having recovered must not be a fresh entry");
    }

    // ── 10: recovery followed by a new persistent episode logs again ──────────────────────────

    @Test
    void recoveryFollowedByNewPersistentEpisodeLogsAgain() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH);
        tracker.classify(MANA, ClientResourceParityClassification.EXACT_MATCH);
        tracker.classify(MANA, ClientResourceParityClassification.TRANSITIONAL_MISMATCH);
        assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH,
                tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH));
    }

    // ── 11: independent resources never interfere with each other ─────────────────────────────

    @Test
    void manaAndRageTransitionsAreIndependent() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH,
                tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH));
        // Rage entering persistent mismatch must not be suppressed or duplicated by Mana's own state.
        assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH,
                tracker.classify(RAGE, ClientResourceParityClassification.PERSISTENT_MISMATCH));
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH));
        assertEquals(ClientResourceParityLogTransition.RECOVERED_FROM_PERSISTENT_MISMATCH,
                tracker.classify(RAGE, ClientResourceParityClassification.EXACT_MATCH));
        // Rage recovering must not affect Mana's still-active persistent episode.
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH));
    }

    // ── 12: all four production resource ids can be tracked independently ────────────────────

    @Test
    void allFourProductionResourceIdsCanBeTrackedIndependently() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        for (Identifier id : new Identifier[] {MANA, STAMINA, SPELL_SLOTS, RAGE}) {
            assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH,
                    tracker.classify(id, ClientResourceParityClassification.PERSISTENT_MISMATCH),
                    "expected a fresh entry event for " + id);
        }
        for (Identifier id : new Identifier[] {MANA, STAMINA, SPELL_SLOTS, RAGE}) {
            assertEquals(ClientResourceParityLogTransition.NONE,
                    tracker.classify(id, ClientResourceParityClassification.PERSISTENT_MISMATCH),
                    "expected no duplicate entry event for " + id);
        }
    }

    // ── 13-14: lifecycle clear erases memory without fabricating a recovery, next entry is fresh ─

    @Test
    void clearingASingleResourceErasesMemoryWithoutFabricatingRecovery() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH);
        tracker.clear(MANA);
        // The clear() call itself returns void — nothing was logged by it. The next observation must
        // behave as though this resource had never been observed before (a fresh entry, not a
        // recovery), proving no recovery was silently fabricated by the clear.
        assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH,
                tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH));
    }

    @Test
    void clearAllErasesEveryResourcesMemoryWithoutFabricatingRecovery() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH);
        tracker.classify(RAGE, ClientResourceParityClassification.PERSISTENT_MISMATCH);
        tracker.clearAll();
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.EXACT_MATCH),
                "a non-persistent observation right after clearAll() must never be treated as a recovery");
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(RAGE, ClientResourceParityClassification.GENERIC_NOT_READY),
                "a non-persistent observation right after clearAll() must never be treated as a recovery");
    }

    @Test
    void afterClearALaterPersistentEpisodeIsTreatedAsANewEntry() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH);
        tracker.clear(MANA);
        assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH,
                tracker.classify(MANA, ClientResourceParityClassification.PERSISTENT_MISMATCH));
    }

    // ── 15-16: readiness-period sequences ──────────────────────────────────────────────────────

    @Test
    void genericNotReadyThenExactMatchProducesNoLog() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.GENERIC_NOT_READY));
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(MANA, ClientResourceParityClassification.EXACT_MATCH));
    }

    @Test
    void genericNotReadyThenTransitionalThenPersistentProducesExactlyOnePersistentEntryEvent() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(STAMINA, ClientResourceParityClassification.GENERIC_NOT_READY));
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify(STAMINA, ClientResourceParityClassification.TRANSITIONAL_MISMATCH));
        assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH,
                tracker.classify(STAMINA, ClientResourceParityClassification.PERSISTENT_MISMATCH));
    }

    // ── null-safety (defensive, not part of the numbered list but consistent with sibling tests) ─

    @Test
    void classifyRejectsNullResourceId() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertThrows(NullPointerException.class,
                () -> tracker.classify(null, ClientResourceParityClassification.EXACT_MATCH));
    }

    @Test
    void classifyRejectsNullClassification() {
        var tracker = new ClientResourceParityLogTransitionTracker<Identifier>();
        assertThrows(NullPointerException.class, () -> tracker.classify(MANA, null));
    }

    // ── External-review correction: the tracker is genuinely generic, not merely Identifier-typed ─
    // ── Required-coverage item 6: no net.minecraft/Fabric/logger/client/networking/legacy import ──

    @Test
    void trackerWorksWithAnArbitraryNonIdentifierKeyType() {
        // Proves the class is actually generic over its key type, not Identifier-specific with a
        // type parameter bolted on for appearances — a plain String key exercises the exact same
        // entry/recovery/steady-state behavior the Identifier-keyed production tests above cover.
        var tracker = new ClientResourceParityLogTransitionTracker<String>();
        assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH,
                tracker.classify("some-arbitrary-key", ClientResourceParityClassification.PERSISTENT_MISMATCH));
        assertEquals(ClientResourceParityLogTransition.NONE,
                tracker.classify("some-arbitrary-key", ClientResourceParityClassification.PERSISTENT_MISMATCH));
        assertEquals(ClientResourceParityLogTransition.RECOVERED_FROM_PERSISTENT_MISMATCH,
                tracker.classify("some-arbitrary-key", ClientResourceParityClassification.EXACT_MATCH));
    }

    @Test
    void trackerSourceImportsNoMinecraftFabricLoggerClientNetworkingOrLegacyManagerType() throws Exception {
        java.nio.file.Path path = java.nio.file.Path.of(
                "src/main/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityLogTransitionTracker.java");
        assertTrue(java.nio.file.Files.exists(path), "expected to find the pure tracker's source file");
        String source = java.nio.file.Files.readString(path);

        List<String> importLines = source.lines().filter(line -> line.trim().startsWith("import ")).toList();
        for (String importLine : importLines) {
            assertTrue(
                    !importLine.contains("net.minecraft") && !importLine.contains("fabricmc")
                            && !importLine.contains("Logger") && !importLine.contains("LocalPlayer")
                            && !importLine.contains("networking") && !importLine.contains("ClientManaManager")
                            && !importLine.contains("ClientStaminaManager") && !importLine.contains("ClientSpellSlotManager")
                            && !importLine.contains("PlayerChargesComponent"),
                    "unexpected non-pure import in ClientResourceParityLogTransitionTracker: " + importLine);
        }
        // The class body itself (beyond imports) must never reference these tokens either — e.g. a
        // fully-qualified in-line reference that bypassed an import statement.
        List<String> forbiddenTokens = List.of("net.minecraft", "Totality.LOGGER", "LocalPlayer", "@Environment");
        for (String token : forbiddenTokens) {
            assertTrue(!source.contains(token), "unexpected reference to " + token + " in the pure transition tracker");
        }
    }
}

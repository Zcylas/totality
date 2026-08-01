package zcylas.totality.api.rpg.combat.stamina.depletion;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinels for the Exhaustion → Stamina Depletion rename and join-lifecycle
 * wiring correction. Every test here is a sentinel, not runtime proof: confirming
 * {@code ServerPlayer}-facing event wiring requires a live server, unreachable under plain JUnit
 * (see {@code HealingPotionItemContractTest}'s class Javadoc for the same constraint documented
 * elsewhere in this codebase). The actual transition/penalty/regen *behavior* these sentinels'
 * wiring depends on is covered by real, executing tests in
 * {@link StaminaDepletionManagerLifecycleTest} instead. Runtime confirmation of the join/
 * disconnect/rejoin flow is the manual validation recorded in the implementation report.
 *
 * <p>This system is Totality's immediate, Stamina-derived exertion/depletion state — WINDED and
 * DEPLETED are NOT D&D Exhaustion levels. The future persistent, multi-source Exhaustion
 * condition (influenced by Fatigue, Rest, starvation, dehydration, curses, etc.) remains
 * unimplemented and separately owned; this rename does not implement it.
 */
class StaminaDepletionSourceMigrationRegressionTest {

    private static final Path PLAYER_CONNECTION_EVENTS =
            Path.of("src/main/java/zcylas/totality/init/events/PlayerConnectionEvents.java");
    private static final Path STAMINA_SERVER_TICK =
            Path.of("src/main/java/zcylas/totality/networking/stamina/StaminaServerTick.java");
    private static final Path DEPLETION_MANAGER = Path.of(
            "src/main/java/zcylas/totality/api/rpg/combat/stamina/depletion/StaminaDepletionManager.java");
    private static final Path DEPLETION_STATE = Path.of(
            "src/main/java/zcylas/totality/api/rpg/combat/stamina/depletion/StaminaDepletionState.java");
    private static final Path OLD_EXHAUSTION_MANAGER = Path.of(
            "src/main/java/zcylas/totality/api/rpg/combat/exhaustion/ExhaustionManager.java");
    private static final Path OLD_EXHAUSTION_STATE = Path.of(
            "src/main/java/zcylas/totality/api/rpg/combat/exhaustion/ExhaustionState.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    // ── Old paths/types removed ──────────────────────────────────────────────

    @Test
    void oldExhaustionPackageFilesAreAbsent() {
        assertFalse(Files.exists(OLD_EXHAUSTION_MANAGER), "old ExhaustionManager.java must be removed, not duplicated");
        assertFalse(Files.exists(OLD_EXHAUSTION_STATE), "old ExhaustionState.java must be removed, not duplicated");
        assertFalse(Files.exists(Path.of("src/main/java/zcylas/totality/api/rpg/combat/exhaustion")),
                "old combat.exhaustion package directory must be removed");
    }

    @Test
    void newTypesExistAtThePreferredPackage() {
        assertTrue(Files.exists(DEPLETION_MANAGER));
        assertTrue(Files.exists(DEPLETION_STATE));
    }

    // ── No production reference to the old names ─────────────────────────────

    @Test
    void noProductionFileImportsTheOldExhaustionPackage() throws Exception {
        forEachProductionJavaFile(source ->
                assertFalse(source.contains("zcylas.totality.api.rpg.combat.exhaustion"),
                        "found a reference to the old combat.exhaustion package"));
    }

    @Test
    void noProductionFileReferencesExhaustionManagerOrExhaustionState() throws Exception {
        forEachProductionJavaFile(source -> {
            assertFalse(source.contains("ExhaustionManager"), "found a reference to old type ExhaustionManager");
            assertFalse(source.contains("ExhaustionState"), "found a reference to old type ExhaustionState");
        });
    }

    @Test
    void noProductionStateSwitchUsesWarningOrExhaustedConstants() throws Exception {
        String managerSource = read(DEPLETION_MANAGER);
        String stateSource = read(DEPLETION_STATE);
        for (String bannedToken : List.of("WARNING", "EXHAUSTED")) {
            assertFalse(managerSource.contains(bannedToken), "StaminaDepletionManager must not reference " + bannedToken);
            assertFalse(stateSource.contains(bannedToken), "StaminaDepletionState must not reference " + bannedToken);
        }
    }

    @Test
    void noPlayerFacingStaminaNotificationClaimsExhaustion() throws Exception {
        String managerSource = read(DEPLETION_MANAGER);
        assertFalse(managerSource.toLowerCase(Locale.ROOT).contains("you are exhausted"),
                "the Stamina-derived notification must not claim the future persistent Exhaustion condition");
        assertTrue(managerSource.contains("You are out of stamina!"));
        assertTrue(managerSource.contains("Your stamina has recovered."));
    }

    @Test
    void vanillaHungerExhaustionApiIsUntouchedByName() throws Exception {
        // causeFoodExhaustion is a legitimate, unrelated vanilla API — confirming it still exists
        // unmodified elsewhere in the codebase (HealEffect) is out of scope here; this test only
        // guards that this rename did not attempt to touch/rename that vanilla-owned concept.
        String managerSource = read(DEPLETION_MANAGER);
        assertFalse(managerSource.contains("causeFoodExhaustion"));
    }

    @Test
    void noRealPersistentExhaustionConditionWasIntroduced() throws Exception {
        forEachProductionJavaFile(source -> {
            assertFalse(source.contains("ExhaustionCondition"), "found a reference to a not-yet-approved ExhaustionCondition type");
        });
    }

    // ── Approved terminology present ──────────────────────────────────────────

    @Test
    void approvedStateNamesArePresent() throws Exception {
        String stateSource = read(DEPLETION_STATE);
        assertTrue(stateSource.contains("NORMAL"));
        assertTrue(stateSource.contains("WINDED"));
        assertTrue(stateSource.contains("DEPLETED"));
    }

    @Test
    void renamedMethodsArePresent() throws Exception {
        String managerSource = read(DEPLETION_MANAGER);
        assertTrue(managerSource.contains("isDepleted("), "expected the renamed isDepleted(...) method");
        assertTrue(managerSource.contains("isWinded("), "expected the renamed isWinded(...) method");
        assertTrue(managerSource.contains("depletedPlayers"), "expected the renamed depletedPlayers field");
    }

    // ── Modifier identifiers ───────────────────────────────────────────────────

    @Test
    void attributeModifierIdsUseStaminaDepletionTerminology() throws Exception {
        String tickSource = read(STAMINA_SERVER_TICK);
        assertTrue(tickSource.contains("\"stamina_depleted_movement_penalty\""));
        assertTrue(tickSource.contains("\"stamina_depleted_attack_penalty\""));
        assertFalse(tickSource.contains("\"exhaustion_speed\""));
        assertFalse(tickSource.contains("\"exhaustion_attack\""));
    }

    @Test
    void modifierPenaltyAmountsAreUnchanged() throws Exception {
        String tickSource = read(STAMINA_SERVER_TICK);
        assertTrue(tickSource.contains("-0.20"), "movement penalty amount must remain unchanged");
        assertTrue(tickSource.contains("-0.25"), "attack penalty amount must remain unchanged");
        assertTrue(tickSource.contains("ADD_MULTIPLIED_TOTAL"), "modifier operation must remain unchanged");
    }

    // ── Join lifecycle wiring ───────────────────────────────────────────────────

    @Test
    void joinEventCallsTheRenamedInitializationMethod() throws Exception {
        String source = read(PLAYER_CONNECTION_EVENTS);
        assertTrue(source.contains("StaminaDepletionManager.onPlayerJoin(player)"),
                "expected the JOIN handler to call StaminaDepletionManager.onPlayerJoin(player)");
    }

    @Test
    void joinInitializationHappensAfterTheStaminaSyncReadProvingComponentAvailability() throws Exception {
        String source = read(PLAYER_CONNECTION_EVENTS);
        int syncIndex = source.indexOf("StaminaServerTick.syncStamina(player);");
        int joinInitIndex = source.indexOf("StaminaDepletionManager.onPlayerJoin(player);");
        assertTrue(syncIndex >= 0, "expected the existing stamina sync call to still be present");
        assertTrue(joinInitIndex >= 0, "expected the join initialization call to be present");
        assertTrue(joinInitIndex > syncIndex,
                "join initialization must happen after the stamina sync proves the component is loaded");
    }

    @Test
    void disconnectHandlerInvokesCleanup() throws Exception {
        String source = read(PLAYER_CONNECTION_EVENTS);
        assertTrue(source.contains("StaminaDepletionManager.onPlayerLeave(handler.player)"),
                "expected the DISCONNECT handler to call StaminaDepletionManager.onPlayerLeave(...)");
    }

    @Test
    void serverTickStillDelegatesTickAndPenaltyChecksToTheRenamedManager() throws Exception {
        String source = read(STAMINA_SERVER_TICK);
        assertTrue(source.contains("StaminaDepletionManager.tick(player)"));
        assertTrue(source.contains("StaminaDepletionManager.isPenalized(player)"));
        assertTrue(source.contains("StaminaDepletionManager.getRegenMultiplier(player)"));
    }

    // ── Scope guard: no unrelated systems touched ─────────────────────────────

    @Test
    void noRestOrFatigueOrDormantResourceReferencesWereIntroduced() throws Exception {
        // "Fatigue"/"Rest" may legitimately appear only inside explanatory comments describing the
        // future, still-unimplemented Exhaustion condition this rename deliberately does not
        // build — so this test scans code lines only (imports/fields/calls/types), not comments.
        for (String banned : List.of("RestNeed", "PlayerResourceDefinition", "GENERIC_COMPONENT")) {
            assertFalse(codeLinesOnly(read(DEPLETION_MANAGER)).contains(banned),
                    "StaminaDepletionManager must not reference " + banned + " in executable code");
            assertFalse(codeLinesOnly(read(DEPLETION_STATE)).contains(banned),
                    "StaminaDepletionState must not reference " + banned + " in executable code");
        }
    }

    private static String codeLinesOnly(String source) {
        StringBuilder sb = new StringBuilder();
        boolean inBlockComment = false;
        for (String line : source.lines().toList()) {
            String trimmed = line.trim();
            if (inBlockComment) {
                if (trimmed.contains("*/")) inBlockComment = false;
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) inBlockComment = true;
                continue;
            }
            if (trimmed.startsWith("*") || trimmed.startsWith("//")) continue;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private interface SourceAssertion {
        void check(String source);
    }

    private static void forEachProductionJavaFile(SourceAssertion assertion) throws Exception {
        try (var stream = Files.walk(Path.of("src/main/java/zcylas/totality"))) {
            List<Path> javaFiles = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
            assertFalse(javaFiles.isEmpty(), "expected to find production Java sources to scan");
            for (Path path : javaFiles) {
                assertion.check(Files.readString(path));
            }
        }
    }
}

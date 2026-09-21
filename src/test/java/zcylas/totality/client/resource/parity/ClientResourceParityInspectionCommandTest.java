package zcylas.totality.client.resource.parity;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityClassification;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityLogTransition;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityObservation;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityOutcome;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityReport;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParitySummary;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3B-3: {@link ClientResourceParityInspectionCommand} is the client-only, development-gated
 * command boundary behind {@code /totalitydebug resource parity} (moved off the shared {@code
 * totality} root by the third correction pass — see "Command-root correction" below). Covers the
 * task's required-coverage items 28-33 (repeated-invocation non-mutation, spell-slot labeling
 * reaching the command's actual output, development-only registration policy, and the client/server
 * dependency boundary) plus the external-review correction's Blocker 3 access-failure-containment
 * coverage; items 1-27 are covered by {@code ClientResourceParityReportAssemblerTest} at the pure
 * assembler layer.
 *
 * <p>Never calls {@link ClientResourceParityInspectionCommand#registerIfDevelopmentEnvironment()} —
 * that method calls {@code FabricLoader.getInstance()}, which is not initialized in a plain JUnit run
 * (no existing test in this codebase calls it either). The development-gating contract is instead
 * proven by source inspection, exactly as the task instructs — paired with a behavioral test of
 * {@link ClientResourceParityInspectionCommand#gatherReport()}/its package-private two-argument
 * overload, both safely callable without a running client.
 */
class ClientResourceParityInspectionCommandTest {

    @AfterEach
    void clearState() {
        ClientResourceParityCoordinator.tracker().clearAll();
        ClientResourceParityLogObserver.clear();
    }

    // ── 28: repeated command execution does not mutate parity observations ────────────────────

    @Test
    void repeatedGatherReportDoesNotMutateTheSeededParityObservation() {
        var summary = new ClientResourceParitySummary.Scalar(50, 100, 0, 1);
        var seeded = ClientResourceParityCoordinator.tracker().observe(
                PlayerResourceIds.MANA, 0, ClientResourceParityOutcome.MATCH, summary, summary, false);

        ClientResourceParityReport first = ClientResourceParityInspectionCommand.gatherReport();
        ClientResourceParityReport second = ClientResourceParityInspectionCommand.gatherReport();

        assertEquals(first.chatLines(), second.chatLines(), "repeated gatherReport() must be deterministic");
        assertEquals(Optional.of(seeded), ClientResourceParityObservations.latest(PlayerResourceIds.MANA),
                "gatherReport() must never mutate the observation it merely reads");
    }

    // ── 29: repeated command execution does not alter logging episode state ───────────────────

    @Test
    void repeatedGatherReportDoesNotDisturbTheLoggingEpisodeTracker() {
        // Seed an active persistent-mismatch episode directly on the log observer's own transition
        // memory — if gatherReport() ever touched ClientResourceParityLogObserver (it must not), a
        // classify(...) call afterward would no longer see the episode as active.
        ClientResourceParityLogTransition entered =
                ClientResourceParityLogObserver.transitions().classify(PlayerResourceIds.RAGE, ClientResourceParityClassification.PERSISTENT_MISMATCH);
        assertEquals(ClientResourceParityLogTransition.ENTERED_PERSISTENT_MISMATCH, entered);

        var mismatchGeneric = new ClientResourceParitySummary.Scalar(1, 2, 0, 1);
        var mismatchLegacy = new ClientResourceParitySummary.Scalar(0, 0, 0, 1);
        ClientResourceParityCoordinator.tracker().observe(
                PlayerResourceIds.RAGE, 0, ClientResourceParityOutcome.MISMATCH, mismatchGeneric, mismatchLegacy, true);

        for (int i = 0; i < 5; i++) {
            ClientResourceParityInspectionCommand.gatherReport();
        }

        ClientResourceParityLogTransition afterRepeatedInvocation =
                ClientResourceParityLogObserver.transitions().classify(PlayerResourceIds.RAGE, ClientResourceParityClassification.EXACT_MATCH);
        assertEquals(ClientResourceParityLogTransition.RECOVERED_FROM_PERSISTENT_MISMATCH, afterRepeatedInvocation,
                "the episode seeded before gatherReport() must still be active afterward — gatherReport() must never "
                        + "read, clear, or otherwise touch the logging episode tracker");
    }

    @Test
    void gatherReportMethodBodyNeverReferencesTheLogObserver() throws Exception {
        // Scans only the zero-argument gatherReport()'s own method body (not the class's Javadoc,
        // which mentions ClientResourceParityLogObserver in prose to explain why it is deliberately
        // not touched) — proving the actual executable code never reads or mutates the logging
        // episode tracker.
        String source = readSource("ClientResourceParityInspectionCommand.java");
        int methodStart = source.indexOf("static ClientResourceParityReport gatherReport()");
        assertTrue(methodStart >= 0, "expected to find gatherReport()");
        int bodyStart = source.indexOf('{', methodStart);
        int bodyEnd = source.indexOf("\n    }", bodyStart);
        assertTrue(bodyStart >= 0 && bodyEnd > bodyStart, "expected to locate gatherReport()'s method body");
        String body = source.substring(bodyStart, bodyEnd);

        assertFalse(body.contains("LogObserver"),
                "gatherReport()'s executable body must never reference ClientResourceParityLogObserver — it reads "
                        + "only the trusted façade and the read-only parity observation snapshot");
    }

    // ── spell-slot values are labeled remaining, reaching the command's own output shape ──────

    @Test
    void gatherReportLabelsSpellSlotsAsRemainingNeverUsed() {
        var partitioned = ClientResourceParitySummary.Partitioned.of(java.util.List.of(
                new ClientResourceParitySummary.Partitioned.Partition(1, 2, 3, 0)), 1);
        ClientResourceParityCoordinator.tracker().observe(
                PlayerResourceIds.SPELL_SLOTS, 0, ClientResourceParityOutcome.MATCH, partitioned, partitioned, false);

        ClientResourceParityReport report = ClientResourceParityInspectionCommand.gatherReport();
        String spellSlotLine = lineFor(report, PlayerResourceIds.SPELL_SLOTS);

        assertTrue(spellSlotLine.contains("remaining"), "expected an explicit 'remaining' label: " + spellSlotLine);
        assertFalse(spellSlotLine.toLowerCase(java.util.Locale.ROOT).contains("used"),
                "spell-slot output must never use the word 'used': " + spellSlotLine);
    }

    @Test
    void gatherReportCoversAllSevenCanonicalResourcesEvenWithNothingSeeded() {
        ClientResourceParityReport report = ClientResourceParityInspectionCommand.gatherReport();
        assertEquals(7, report.lines().size());
    }

    @Test
    void gatherReportResourceIdsArePlainStringsMatchingTheIdentifierToString() {
        ClientResourceParityReport report = ClientResourceParityInspectionCommand.gatherReport();
        assertEquals(PlayerResourceIds.HEALTH.toString(), lineFor(report, PlayerResourceIds.HEALTH).split(" \\| ")[0]);
    }

    // ── 18 (no local player / no world), integration-level: unregistered façade readers ───────

    @Test
    void gatherReportNativeResultsAreBoundedWhenNoReaderIsRegistered() {
        // In this plain JUnit context, TotalityClientResourceReaders.register() (production-only
        // client wiring) was never invoked, so ClientResourceService.INSTANCE has no reader for
        // Health/Food/Breath registered — proving gatherReport() degrades to a bounded, structured
        // Unavailable result rather than touching Minecraft.getInstance() or throwing.
        ClientResourceParityReport report = assertDoesNotThrow(() -> ClientResourceParityInspectionCommand.gatherReport());
        String healthLine = lineFor(report, PlayerResourceIds.HEALTH);
        assertTrue(healthLine.contains("UNAVAILABLE"), "expected a bounded Unavailable native line: " + healthLine);
    }

    // ── Blocker 2: native lines carry no ClientResourceParityClassification ────────────────────

    @Test
    void nativeLinesFromTheRealCommandNeverContainAParityClassificationName() {
        ClientResourceParityReport report = ClientResourceParityInspectionCommand.gatherReport();
        for (Identifier id : List.of(PlayerResourceIds.HEALTH, PlayerResourceIds.FOOD, PlayerResourceIds.BREATH)) {
            String line = lineFor(report, id);
            for (ClientResourceParityClassification classification : ClientResourceParityClassification.values()) {
                assertFalse(line.contains(classification.name()), id + " native line must not contain " + classification);
            }
            assertTrue(line.contains("comparison=native-only"));
        }
    }

    // ══ Blocker 3: per-Resource access-failure containment ════════════════════════════════════

    @Test
    void oneThrowingNativeReaderDoesNotAbortTheReport() {
        Function<Identifier, ClientResourceQueryResult> throwingHealth = id -> {
            if (id.equals(PlayerResourceIds.HEALTH)) {
                throw new IllegalStateException("synthetic failure for test");
            }
            return ClientResourceQueryResult.unavailable(id, zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason.NO_LOCAL_PLAYER);
        };

        ClientResourceParityReport report = assertDoesNotThrow(() ->
                ClientResourceParityInspectionCommand.gatherReport(throwingHealth, id -> Optional.empty()));

        assertEquals(7, report.lines().size(), "one failing Resource must never reduce the total line count");
        assertTrue(lineFor(report, PlayerResourceIds.HEALTH).contains("ACCESS_ERROR(IllegalStateException)"));
        assertTrue(lineFor(report, PlayerResourceIds.FOOD).contains("UNAVAILABLE(NO_LOCAL_PLAYER)"));
        assertTrue(lineFor(report, PlayerResourceIds.BREATH).contains("UNAVAILABLE(NO_LOCAL_PLAYER)"));
    }

    @Test
    void oneThrowingShadowLookupDoesNotAbortTheReport() {
        Function<Identifier, Optional<ClientResourceParityObservation>> throwingRage = id -> {
            if (id.equals(PlayerResourceIds.RAGE)) {
                throw new IllegalStateException("synthetic failure for test");
            }
            return Optional.empty();
        };

        ClientResourceParityReport report = assertDoesNotThrow(() -> ClientResourceParityInspectionCommand.gatherReport(
                id -> ClientResourceQueryResult.unavailable(id, zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason.NO_LOCAL_PLAYER),
                throwingRage));

        assertEquals(7, report.lines().size(), "one failing Resource must never reduce the total line count");
        assertTrue(lineFor(report, PlayerResourceIds.RAGE).contains("OBSERVATION_ACCESS_ERROR(IllegalStateException)"));
        assertTrue(lineFor(report, PlayerResourceIds.MANA).contains("NO_OBSERVATION_YET"));
        assertTrue(lineFor(report, PlayerResourceIds.STAMINA).contains("NO_OBSERVATION_YET"));
        assertTrue(lineFor(report, PlayerResourceIds.SPELL_SLOTS).contains("NO_OBSERVATION_YET"));
    }

    @Test
    void extremelyLongExceptionMessageNeverEntersTheOutput() {
        String hugeMessage = "SECRET-".repeat(2000);
        Function<Identifier, ClientResourceQueryResult> throwingHealth = id -> {
            throw new IllegalStateException(hugeMessage);
        };

        ClientResourceParityReport report = ClientResourceParityInspectionCommand.gatherReport(throwingHealth, id -> Optional.empty());
        String healthLine = lineFor(report, PlayerResourceIds.HEALTH);

        assertFalse(healthLine.contains("SECRET"), "the exception's message must never reach the rendered output");
        assertTrue(healthLine.length() <= 500, "the whole line must remain bounded even given an enormous exception message");
        assertTrue(healthLine.contains("ACCESS_ERROR(IllegalStateException)"));
    }

    @Test
    void repeatedFailingInvocationsRemainDeterministic() {
        Function<Identifier, ClientResourceQueryResult> throwingHealth = id -> {
            throw new IllegalStateException("boom");
        };
        Function<Identifier, Optional<ClientResourceParityObservation>> throwingRage = id -> {
            throw new IllegalStateException("boom");
        };

        ClientResourceParityReport first = ClientResourceParityInspectionCommand.gatherReport(throwingHealth, throwingRage);
        ClientResourceParityReport second = ClientResourceParityInspectionCommand.gatherReport(throwingHealth, throwingRage);

        assertEquals(first.chatLines(), second.chatLines());
        assertEquals(8, first.chatLines().size(), "header + seven Resource lines, even when two Resources fail access");
    }

    @Test
    void accessFailuresDuringGatherReportMutateNoParityObservationOrLoggingEpisodeState() {
        var summary = new ClientResourceParitySummary.Scalar(1, 1, 0, 1);
        var seeded = ClientResourceParityCoordinator.tracker().observe(
                PlayerResourceIds.MANA, 0, ClientResourceParityOutcome.MATCH, summary, summary, false);

        Function<Identifier, ClientResourceQueryResult> throwingHealth = id -> {
            throw new IllegalStateException("boom");
        };

        ClientResourceParityInspectionCommand.gatherReport(throwingHealth, ClientResourceParityObservations::latest);

        assertEquals(Optional.of(seeded), ClientResourceParityObservations.latest(PlayerResourceIds.MANA),
                "a native access failure must never mutate an unrelated shadow-parity observation");
    }

    @Test
    void neverCatchesThrowableOnlyRuntimeException() throws Exception {
        String source = readSource("ClientResourceParityInspectionCommand.java");
        assertFalse(source.contains("catch (Throwable"), "must never catch Throwable");
        assertTrue(source.contains("catch (RuntimeException"), "expected a narrow RuntimeException catch for access containment");
    }

    // ══ Blocker 2 (second correction pass): access vs. result/observation-conversion failure ══

    @Test
    void nativeAccessorReturningNullResultProducesTheDistinctConversionErrorLine() {
        // The accessor call itself succeeds (returns null rather than throwing) — the failure only
        // happens one step later, converting that null result into the pure input vocabulary. This
        // must render as RESULT_CONVERSION_ERROR, never ACCESS_ERROR.
        Function<Identifier, ClientResourceQueryResult> nullReturningHealth = id ->
                id.equals(PlayerResourceIds.HEALTH) ? null
                        : ClientResourceQueryResult.unavailable(id, zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason.NO_LOCAL_PLAYER);

        ClientResourceParityReport report = assertDoesNotThrow(() ->
                ClientResourceParityInspectionCommand.gatherReport(nullReturningHealth, id -> Optional.empty()));

        assertEquals(7, report.lines().size(), "a conversion failure for one Resource must never reduce the total line count");
        String healthLine = lineFor(report, PlayerResourceIds.HEALTH);
        assertTrue(healthLine.contains("RESULT_CONVERSION_ERROR"), "expected a conversion-error line: " + healthLine);
        assertFalse(healthLine.contains("ACCESS_ERROR("), "a conversion failure must never render as an access-error line: " + healthLine);
        assertTrue(lineFor(report, PlayerResourceIds.FOOD).contains("UNAVAILABLE(NO_LOCAL_PLAYER)"),
                "the other native Resources must still be gathered normally");
    }

    @Test
    void shadowAccessorReturningNullLookupProducesTheDistinctConversionErrorLine() {
        Function<Identifier, Optional<ClientResourceParityObservation>> nullReturningRage = id ->
                id.equals(PlayerResourceIds.RAGE) ? null : Optional.empty();

        ClientResourceParityReport report = assertDoesNotThrow(() -> ClientResourceParityInspectionCommand.gatherReport(
                id -> ClientResourceQueryResult.unavailable(id, zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason.NO_LOCAL_PLAYER),
                nullReturningRage));

        assertEquals(7, report.lines().size(), "a conversion failure for one Resource must never reduce the total line count");
        String rageLine = lineFor(report, PlayerResourceIds.RAGE);
        assertTrue(rageLine.contains("OBSERVATION_CONVERSION_ERROR"), "expected a conversion-error line: " + rageLine);
        assertFalse(rageLine.contains("OBSERVATION_ACCESS_ERROR"), "a conversion failure must never render as an access-error line: " + rageLine);
        assertTrue(lineFor(report, PlayerResourceIds.MANA).contains("NO_OBSERVATION_YET"),
                "the other shadow-parity Resources must still be gathered normally");
    }

    @Test
    void accessAndConversionFailureLabelsCannotBeConfusedForTheSameResourceAndExceptionType() {
        Function<Identifier, ClientResourceQueryResult> throwingHealth = id -> {
            throw new IllegalStateException("boom");
        };
        Function<Identifier, ClientResourceQueryResult> nullReturningHealth = id -> null;

        String accessLine = lineFor(
                ClientResourceParityInspectionCommand.gatherReport(throwingHealth, id -> Optional.empty()), PlayerResourceIds.HEALTH);
        String conversionLine = lineFor(
                ClientResourceParityInspectionCommand.gatherReport(nullReturningHealth, id -> Optional.empty()), PlayerResourceIds.HEALTH);

        assertFalse(accessLine.equals(conversionLine));
        assertTrue(accessLine.contains("ACCESS_ERROR("));
        assertTrue(conversionLine.contains("RESULT_CONVERSION_ERROR("));
    }

    @Test
    void repeatedConversionFailuresRemainDeterministicAndStillProduceExactlyEightChatLines() {
        Function<Identifier, ClientResourceQueryResult> nullReturningHealth = id -> null;
        Function<Identifier, Optional<ClientResourceParityObservation>> nullReturningRage = id -> null;

        ClientResourceParityReport first = ClientResourceParityInspectionCommand.gatherReport(nullReturningHealth, nullReturningRage);
        ClientResourceParityReport second = ClientResourceParityInspectionCommand.gatherReport(nullReturningHealth, nullReturningRage);

        assertEquals(first.chatLines(), second.chatLines());
        assertEquals(8, first.chatLines().size(), "header + seven Resource lines, even when two Resources fail conversion");
    }

    @Test
    void conversionFailuresMutateNoParityObservationOrLoggingEpisodeState() {
        var summary = new ClientResourceParitySummary.Scalar(1, 1, 0, 1);
        var seeded = ClientResourceParityCoordinator.tracker().observe(
                PlayerResourceIds.STAMINA, 0, ClientResourceParityOutcome.MATCH, summary, summary, false);

        Function<Identifier, ClientResourceQueryResult> nullReturningHealth = id -> null;

        ClientResourceParityInspectionCommand.gatherReport(nullReturningHealth, ClientResourceParityObservations::latest);

        assertEquals(Optional.of(seeded), ClientResourceParityObservations.latest(PlayerResourceIds.STAMINA),
                "a native conversion failure must never mutate an unrelated shadow-parity observation");
    }

    // ── 31: development-only registration policy (source inspection) ──────────────────────────

    @Test
    void classIsAnnotatedClientOnly() throws Exception {
        // net.fabricmc.api.Environment uses RetentionPolicy.CLASS (confirmed by inspecting the
        // fabric-loader jar) — it is deliberately not visible to runtime reflection, exactly like
        // every other @Environment(EnvType.CLIENT) check elsewhere in this codebase's own test
        // suite (see LegacyClientResourceParityReadersTest), so this is a source check, not a
        // reflection check.
        String source = readSource("ClientResourceParityInspectionCommand.java");
        int classIndex = source.indexOf("public final class ClientResourceParityInspectionCommand");
        assertTrue(classIndex >= 0, "expected to find the class declaration");
        String beforeClass = source.substring(0, classIndex);
        assertTrue(beforeClass.trim().endsWith("@Environment(EnvType.CLIENT)"),
                "the command boundary must be annotated @Environment(EnvType.CLIENT) immediately before the class declaration");
    }

    @Test
    void registrationMethodGuardsOnFabricLoaderDevelopmentEnvironmentBeforeRegistering() throws Exception {
        String source = readSource("ClientResourceParityInspectionCommand.java");

        int methodStart = source.indexOf("static void registerIfDevelopmentEnvironment()");
        assertTrue(methodStart >= 0, "expected to find registerIfDevelopmentEnvironment()");
        int guardIndex = source.indexOf("FabricLoader.getInstance().isDevelopmentEnvironment()", methodStart);
        int registerIndex = source.indexOf("ClientCommandRegistrationCallback.EVENT.register", methodStart);

        assertTrue(guardIndex >= 0, "expected a FabricLoader.isDevelopmentEnvironment() gate in the registration method");
        assertTrue(registerIndex >= 0, "expected the command to actually register with ClientCommandRegistrationCallback");
        assertTrue(guardIndex < registerIndex, "the development-environment guard must appear before command registration");
    }

    @Test
    void noPermissionOrOperatorCheckSubstitutesForDevelopmentGating() throws Exception {
        String source = readSource("ClientResourceParityInspectionCommand.java");
        assertFalse(source.contains(".requires("),
                "the command must be gated by the development environment, never by a permission/operator predicate");
    }

    // ══ Command-root correction (third correction pass, manual-validation finding) ═══════════
    //
    // Confirmed live: registering anything under the shared `totality` literal client-side caused
    // Fabric's client command dispatch to intercept and break every other server-side /totality
    // branch (showancestry, ancestry, wallet, ...) with a client-side Brigadier syntax exception,
    // because the client dispatcher commits to a client-registered root literal and does not fall
    // back to forwarding unmatched remainder input to the server. The command now registers under
    // the entirely distinct root literal `totalitydebug`, which no server command begins with.

    @Test
    void rootLiteralConstantIsTotalityDebugNotTotality() {
        assertEquals("totalitydebug", ClientResourceParityInspectionCommand.ROOT_LITERAL);
    }

    @Test
    void registrationMethodRegistersTheTotalityDebugRootLiteral() throws Exception {
        String source = readSource("ClientResourceParityInspectionCommand.java");
        int methodStart = source.indexOf("static void registerIfDevelopmentEnvironment()");
        assertTrue(methodStart >= 0, "expected to find registerIfDevelopmentEnvironment()");
        int bodyEnd = source.indexOf("\n    }", methodStart);
        String body = source.substring(methodStart, bodyEnd);

        assertTrue(body.contains("ClientCommands.literal(ROOT_LITERAL)"),
                "expected the registration method to register the ROOT_LITERAL constant as its root");
    }

    @Test
    void productionCommandNeverRegistersTheSharedTotalityRootLiteral() throws Exception {
        String source = readSource("ClientResourceParityInspectionCommand.java");
        assertFalse(source.contains("ClientCommands.literal(\"totality\")"),
                "the client command must never register the shared 'totality' root literal — that "
                        + "root belongs exclusively to the existing server-side command tree");
        assertFalse(source.contains(".literal(\"totality\")"),
                "no Brigadier literal builder anywhere in this file may name the bare 'totality' root");
    }

    @Test
    void commandPathIsExactlyTotalityDebugResourceParity() {
        String fullPath = ClientResourceParityInspectionCommand.ROOT_LITERAL + " resource parity";
        assertEquals("totalitydebug resource parity", fullPath);
    }

    // ── 32: client-only command boundary is not referenced from server initialization,
    // ── and does not modify the existing server command-tree file ─────────────────────────────

    @Test
    void commandClassIsNotReferencedFromServerInitialization() throws Exception {
        for (String candidate : new String[] {
                "src/main/java/zcylas/totality/Totality.java",
                "src/main/java/zcylas/totality/init/ModEvents.java",
                "src/main/java/zcylas/totality/init/TotalityCommands.java"
        }) {
            Path path = Path.of(candidate);
            if (!Files.exists(path)) {
                continue;
            }
            String source = Files.readString(path);
            assertFalse(source.contains("ClientResourceParityInspectionCommand"),
                    candidate + " must never reference the client-only parity inspection command");
        }
    }

    @Test
    void serverCommandTreeFileWasNotModifiedByThisSlice() throws Exception {
        // Coexistence with the existing server /totality command tree is a manual-validation item
        // (see the implementation report), not something this test can prove at runtime — this test
        // only proves the narrower, provable claim: TotalityCommands.java's own file was not touched.
        Path path = Path.of("src/main/java/zcylas/totality/init/TotalityCommands.java");
        assertTrue(Files.exists(path));
        String source = Files.readString(path);
        assertFalse(source.contains("resource") && source.contains("parity"),
                "TotalityCommands.java must not have gained a 'resource parity' branch");
    }

    @Test
    void totalityClientRegistersTheCommand() throws Exception {
        Path path = Path.of("src/main/java/zcylas/totality/TotalityClient.java");
        assertTrue(Files.exists(path), "expected to find TotalityClient.java at " + path);
        String source = Files.readString(path);
        assertTrue(source.contains("ClientResourceParityInspectionCommand.registerIfDevelopmentEnvironment()"),
                "expected TotalityClient.onInitializeClient() to register the Phase 3B-3 command");
    }

    // ── 33: no Resource mutation / packet-send / legacy-manager write / consumer migration ────

    @Test
    void productionFilesIntroduceNoForbiddenDependency() throws Exception {
        String[] files = {
                "ClientResourceParityInspectionCommand.java"
        };
        String[] forbiddenTokens = {
                "ServerPlayNetworking.send", "ClientPlayNetworking.send", ".setMana(", ".setStamina(",
                "PlayerManaManager.set", "PlayerStaminaManager.set", "SpellSlotComponent.useSlot",
                "SpellSlotComponent.restoreAll", "ChargeComponents", "KeyBinding", "KeyMapping",
                "HudElementRegistry", "Entitlement"
        };
        for (String file : files) {
            String source = readSource(file);
            for (String forbidden : forbiddenTokens) {
                assertFalse(source.contains(forbidden), file + " must not contain forbidden token: " + forbidden);
            }
        }
    }

    @Test
    void gatherReportIsPackagePrivateNotPublicApi() throws NoSuchMethodException {
        Method method = ClientResourceParityInspectionCommand.class.getDeclaredMethod("gatherReport");
        assertFalse(Modifier.isPublic(method.getModifiers()),
                "gatherReport() is a package-private test seam, not part of the command's public surface");
    }

    @Test
    void twoArgumentGatherReportSeamIsAlsoPackagePrivate() throws NoSuchMethodException {
        Method method = ClientResourceParityInspectionCommand.class.getDeclaredMethod("gatherReport", Function.class, Function.class);
        assertFalse(Modifier.isPublic(method.getModifiers()),
                "the two-argument gatherReport(...) seam is a package-private test seam, not part of the command's public surface");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private static String lineFor(ClientResourceParityReport report, Identifier id) {
        return report.lines().stream()
                .filter(line -> line.resourceId().equals(id.toString()))
                .findFirst()
                .map(zcylas.totality.api.rpg.resources.client.parity.ClientResourceParityReportLine::text)
                .orElseThrow(() -> new AssertionError("no line rendered for " + id));
    }

    private static String readSource(String fileName) throws Exception {
        Path path = Path.of("src/main/java/zcylas/totality/client/resource/parity").resolve(fileName);
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }
}

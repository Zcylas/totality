package zcylas.totality.api.rpg.resources;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinels proving the dormant Resource Registration pass stayed exactly as
 * narrow as scoped: no Temperature id/definition, no new gameplay/HUD/client-reader/packet/command
 * code for Thirst/Sanity/Ki/Fatigue, and no reference to the three new ids anywhere outside the two
 * files that legitimately declare/register them. These are sentinels, not runtime proof — see
 * {@link DormantResourceRegistrationTest} for the real, executing dormancy/query behavior these
 * assertions assume stays true.
 */
class DormantResourceScopeRegressionTest {

    private static final Path RESOURCE_IDS = Path.of(
            "src/main/java/zcylas/totality/api/rpg/resources/PlayerResourceIds.java");
    private static final Path PRODUCTION_DEFINITIONS = Path.of(
            "src/main/java/zcylas/totality/api/rpg/resources/ProductionResourceDefinitions.java");
    private static final Path SRC_MAIN_JAVA = Path.of("src/main/java/zcylas/totality");
    private static final Path SRC_MAIN = Path.of("src/main");
    private static final Path SRC_TEST = Path.of("src/test");

    private static String read(Path path) throws IOException {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    // ── Temperature absence ───────────────────────────────────────────────────────────────────

    @Test
    void noTemperatureIdConstantExists() throws IOException {
        String source = read(RESOURCE_IDS);
        assertFalse(source.contains("TEMPERATURE"), "PlayerResourceIds must declare no TEMPERATURE constant");
        assertFalse(source.contains("\"temperature\""), "PlayerResourceIds must declare no \"temperature\" path literal");
    }

    @Test
    void noTemperatureDefinitionIsRegistered() throws IOException {
        // Code lines only: this file's own explanatory comments legitimately mention "temperature"
        // when documenting why it is deliberately deferred (see the class Javadoc) — that is not a
        // registration.
        String codeOnly = codeLinesOnly(read(PRODUCTION_DEFINITIONS));
        assertFalse(codeOnly.toLowerCase(java.util.Locale.ROOT).contains("temperature"),
                "ProductionResourceDefinitions must register no Temperature definition");
    }

    @Test
    void noTemperatureGameplayOrStateClassExists() throws IOException {
        forEachProductionJavaFile(source -> {
            assertFalse(source.contains("class TemperatureManager"));
            assertFalse(source.contains("class BodyTemperature"));
            assertFalse(source.contains("class ThermalExposure"));
            assertFalse(source.contains("class HeatState"));
            assertFalse(source.contains("class ColdState"));
        });
    }

    // ── Fatigue absence (this pass; still deferred) ───────────────────────────────────────────

    @Test
    void noFatigueIdConstantExists() throws IOException {
        String source = read(RESOURCE_IDS);
        assertFalse(source.contains("FATIGUE"), "PlayerResourceIds must declare no FATIGUE constant this pass");
        assertFalse(source.contains("\"fatigue\""), "PlayerResourceIds must declare no \"fatigue\" path literal this pass");
    }

    @Test
    void noFatigueDefinitionIsRegistered() throws IOException {
        String source = read(PRODUCTION_DEFINITIONS);
        String codeOnly = codeLinesOnly(source);
        assertFalse(codeOnly.toLowerCase(java.util.Locale.ROOT).contains("fatigue"),
                "ProductionResourceDefinitions must register no Fatigue definition this pass "
                        + "(comments describing the deferral are permitted and excluded from this scan)");
    }

    // ── No new gameplay classes for any of the four candidates ────────────────────────────────

    @Test
    void noThirstSanityKiOrFatigueGameplayManagerClassWasAdded() throws IOException {
        forEachProductionJavaFile(source -> {
            for (String banned : List.of(
                    "class ThirstManager", "class SanityManager", "class KiManager", "class FatigueManager",
                    "class ThirstApi", "class SanityApi", "class KiApi", "class FatigueApi")) {
                assertFalse(source.contains(banned), "found unexpected gameplay class: " + banned);
            }
        });
    }

    @Test
    void noPersistentExhaustionConditionClassWasAdded() throws IOException {
        forEachProductionJavaFile(source -> assertFalse(source.contains("class ExhaustionCondition")));
    }

    // ── No presentation/client/command/packet surface for the dormant ids ────────────────────

    @Test
    void noClientReaderReferencesTheDormantIds() throws IOException {
        forEachFileUnder(Path.of("src/main/java/zcylas/totality/api/rpg/resources/client"), source -> {
            assertFalse(source.contains("PlayerResourceIds.THIRST"));
            assertFalse(source.contains("PlayerResourceIds.SANITY"));
            assertFalse(source.contains("PlayerResourceIds.KI"));
        });
    }

    @Test
    void noHudRendererReferencesTheDormantIds() throws IOException {
        // Checks the actual identifier reference, not the bare English word — TotalityHudRenderer.java
        // already contains a pre-existing "TODO: Thirst aligned with Stamina" planning comment from
        // earlier design work, unrelated to this pass and explicitly out of scope to touch ("Do not
        // modify the recently completed HUD"). What matters is that no code path reads
        // PlayerResourceIds.THIRST/SANITY/KI, which this checks precisely.
        forEachFileUnder(Path.of("src/main/java/zcylas/totality/client/renderer/hud"), source -> {
            assertFalse(source.contains("PlayerResourceIds.THIRST"));
            assertFalse(source.contains("PlayerResourceIds.SANITY"));
            assertFalse(source.contains("PlayerResourceIds.KI"));
        });
    }

    @Test
    void noPacketReferencesTheDormantIds() throws IOException {
        forEachFileUnder(Path.of("src/main/java/zcylas/totality/networking"), source -> {
            assertFalse(source.contains("PlayerResourceIds.THIRST"));
            assertFalse(source.contains("PlayerResourceIds.SANITY"));
            assertFalse(source.contains("PlayerResourceIds.KI"));
        });
    }

    @Test
    void noCommandGrantsOrMutatesTheDormantIds() throws IOException {
        // Totality has no dedicated `command` package — command classes are named *Command*/
        // *Commands* and live alongside their subsystem (e.g. init/TotalityCommands.java,
        // client/.../ClientResourceParityInspectionCommand.java). Scanning by filename rather than a
        // fixed directory catches all of them regardless of package.
        try (Stream<Path> stream = Files.walk(SRC_MAIN_JAVA)) {
            List<Path> commandFiles = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> p.getFileName().toString().contains("Command"))
                    .toList();
            assertFalse(commandFiles.isEmpty(), "expected to find at least one *Command* source file to scan");
            for (Path path : commandFiles) {
                String source = Files.readString(path);
                assertFalse(source.contains("PlayerResourceIds.THIRST"), () -> path + " must not reference THIRST");
                assertFalse(source.contains("PlayerResourceIds.SANITY"), () -> path + " must not reference SANITY");
                assertFalse(source.contains("PlayerResourceIds.KI"), () -> path + " must not reference KI");
            }
        }
    }

    @Test
    void noPlayerLifecycleEventInstantiatesDormantState() throws IOException {
        Path connectionEvents = Path.of("src/main/java/zcylas/totality/init/events/PlayerConnectionEvents.java");
        String source = read(connectionEvents);
        assertFalse(source.contains("PlayerResourceIds.THIRST"));
        assertFalse(source.contains("PlayerResourceIds.SANITY"));
        assertFalse(source.contains("PlayerResourceIds.KI"));
        assertFalse(source.contains("instantiateScalar"));
    }

    // ── The only two files allowed to reference the new ids by name ──────────────────────────

    @Test
    void onlyTheIdsFileAndProductionDefinitionsReferenceTheNewIdentifiersByConstantName() throws IOException {
        List<String> bannedTokens = List.of(
                "PlayerResourceIds.THIRST", "PlayerResourceIds.SANITY", "PlayerResourceIds.KI");
        try (Stream<Path> stream = Files.walk(SRC_MAIN_JAVA)) {
            List<Path> javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path path : javaFiles) {
                if (path.equals(RESOURCE_IDS) || path.equals(PRODUCTION_DEFINITIONS)) continue;
                String source = Files.readString(path);
                for (String token : bannedTokens) {
                    assertFalse(source.contains(token),
                            () -> path + " must not reference " + token + " outside declaration/registration");
                }
            }
        }
    }

    // ── Repository hygiene ─────────────────────────────────────────────────────────────────────

    @Test
    void noReviewBundleZipExistsUnderSourceDirectories() throws IOException {
        // Only checks for .zip (a review bundle's actual extension) under src/ — src/main/resources
        // legitimately contains ordinary PNG game assets (item/block textures, the mod icon), which
        // are not "screenshots" in the audit-artifact sense this check guards against, so PNGs are
        // deliberately not banned here to avoid a permanent false positive against normal mod assets.
        for (Path root : List.of(SRC_MAIN, SRC_TEST)) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".zip"))
                        .forEach(path -> fail("found a zip archive under source: " + path));
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────

    private interface SourceAssertion {
        void check(String source);
    }

    private static void forEachProductionJavaFile(SourceAssertion assertion) throws IOException {
        forEachFileUnder(SRC_MAIN_JAVA, assertion);
    }

    private static void forEachFileUnder(Path root, SourceAssertion assertion) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path path : javaFiles) {
                assertion.check(Files.readString(path));
            }
        }
    }

    /** Strips {@code //}/{@code *}-prefixed comment lines so a scan only sees executable code. */
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
}

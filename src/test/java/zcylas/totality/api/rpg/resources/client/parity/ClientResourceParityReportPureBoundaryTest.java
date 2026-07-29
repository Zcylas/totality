package zcylas.totality.api.rpg.resources.client.parity;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 3B-3 external-review correction (Blocker 1): every file in the pure report layer —
 * {@link ClientResourceParityReportLine}, {@link ClientResourceParityReport}, {@link
 * ClientResourceParityReportAssembler}, and {@link ClientResourceParityReportInput} — must carry no
 * Minecraft/Fabric/client-runtime/networking/legacy-manager/logging <b>import</b> whatsoever. This is
 * a source/import regression test, mirroring the exact precedent already established by {@code
 * ClientResourceParityLogTransitionTrackerTest#trackerSourceImportsNoMinecraftFabricLoggerClientNetworkingOrLegacyManagerType}
 * for the Phase 3B-2C pure tracker: it scans only lines starting with {@code import }, never whole-
 * file text, since these classes' own Javadoc legitimately mentions {@code net.minecraft.resources
 * .Identifier} in prose to explain why it is deliberately absent from their actual imports.
 *
 * <p>Reusing another already-pure Totality-authored type from this same package or from {@code
 * zcylas.totality.api.rpg.resources.client} (e.g. {@link ClientResourceParityClassification},
 * {@link ClientResourceParitySummary}, {@code ClientResourceTrust}, {@code
 * ClientResourceUnavailableReason} — all confirmed zero/near-zero-import pure types) is expected and
 * allowed; only a forbidden external dependency import fails this test.
 */
class ClientResourceParityReportPureBoundaryTest {

    private static final List<String> PURE_FILES = List.of(
            "ClientResourceParityReportLine.java",
            "ClientResourceParityReport.java",
            "ClientResourceParityReportAssembler.java",
            "ClientResourceParityReportInput.java"
    );

    @Test
    void pureFilesImportOnlyJavaUtilOrThisOwnPurePackageFamily() throws Exception {
        for (String fileName : PURE_FILES) {
            Path path = Path.of("src/main/java/zcylas/totality/api/rpg/resources/client/parity").resolve(fileName);
            assertTrue(Files.exists(path), "expected to find pure report source file at " + path);
            String source = Files.readString(path);
            List<String> importLines = source.lines().map(String::trim).filter(line -> line.startsWith("import ")).toList();

            for (String importLine : importLines) {
                boolean allowed = importLine.startsWith("import java.")
                        || importLine.startsWith("import zcylas.totality.api.rpg.resources.client.parity.")
                        || importLine.equals("import zcylas.totality.api.rpg.resources.client.ClientResourceTrust;")
                        || importLine.equals("import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;");
                assertTrue(allowed, fileName + " has a disallowed import: " + importLine);

                assertFalse(importLine.contains("net.minecraft"), fileName + " must not import net.minecraft.*: " + importLine);
                assertFalse(importLine.contains("net.fabricmc"), fileName + " must not import net.fabricmc.*: " + importLine);
                assertFalse(importLine.toLowerCase(java.util.Locale.ROOT).contains("logg"),
                        fileName + " must not import any logging type: " + importLine);
                assertFalse(importLine.contains("networking"), fileName + " must not import any networking type: " + importLine);
                assertFalse(importLine.contains("ChargeComponents") || importLine.contains("PlayerManaManager")
                                || importLine.contains("PlayerStaminaManager") || importLine.contains("SpellSlotComponents"),
                        fileName + " must not import a legacy manager: " + importLine);
            }
        }
    }

    @Test
    void noPureFileHasAnEnvironmentAnnotation() throws Exception {
        for (String fileName : PURE_FILES) {
            Path path = Path.of("src/main/java/zcylas/totality/api/rpg/resources/client/parity").resolve(fileName);
            String source = Files.readString(path);
            assertFalse(source.contains("@Environment"), fileName + " must not be annotated @Environment — it must load on any side");
        }
    }
}

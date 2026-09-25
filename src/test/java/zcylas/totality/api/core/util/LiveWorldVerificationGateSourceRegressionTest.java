package zcylas.totality.api.core.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the "a normal dev launch never touches the development save" rule: every in-mod
 * {@code *Verification} suite that works against the live server/world (fake players, spawned
 * entities, block edits, block-damage saved data) must gate its {@code register()} entry point on
 * {@link VerificationReporter#liveWorldVerificationEnabled()} — the explicit, default-off opt-in —
 * not merely on {@link VerificationReporter#isDevEnvironment()}.
 */
class LiveWorldVerificationGateSourceRegressionTest {

    private static final Path SRC_MAIN_JAVA = Path.of("src/main/java");
    private static final Pattern LIVE_WORLD_USE = Pattern.compile(
            "TotalityFakePlayer|addFreshEntity|setBlock|BlockDamageStorage");
    private static final String GATE = "if (!VerificationReporter.liveWorldVerificationEnabled()) return;";
    /** Read-only suites allowed to run automatically: ItemValueVerification only passes a fake player
     *  into PricingContext as a read-only argument and never mutates it or the world. */
    private static final List<String> READ_ONLY_EXEMPT = List.of("ItemValueVerification.java");

    @Test
    void everyLiveWorldVerificationSuiteGatesRegistrationOnTheOptInFlag() throws Exception {
        // Fail loudly rather than pass vacuously when run from an unexpected working directory.
        assertTrue(Files.isDirectory(SRC_MAIN_JAVA), "expected production sources at " + SRC_MAIN_JAVA.toAbsolutePath());

        List<Path> suites;
        try (Stream<Path> stream = Files.walk(SRC_MAIN_JAVA)) {
            suites = stream.filter(p -> p.getFileName().toString().endsWith("Verification.java")).toList();
        }
        assertFalse(suites.isEmpty(), "expected to find *Verification.java suites");

        int liveSuites = 0;
        for (Path suite : suites) {
            String source = Files.readString(suite).replace("\r\n", "\n");
            if (!LIVE_WORLD_USE.matcher(source).find()) continue;
            if (READ_ONLY_EXEMPT.contains(suite.getFileName().toString())) continue;
            liveSuites++;
            int register = source.indexOf("public static void register() {");
            assertTrue(register >= 0, suite + " works against the live world but has no register() entry point");
            String firstStatement = source.substring(register).lines().skip(1).findFirst().orElse("").trim();
            assertTrue(firstStatement.startsWith(GATE),
                    suite + " works against the live world, so register() must start with the opt-in gate:\n  "
                            + GATE + "\nbut starts with:\n  " + firstStatement);
        }
        assertTrue(liveSuites >= 14, "expected at least the 14 known live-world suites, found " + liveSuites);
    }
}

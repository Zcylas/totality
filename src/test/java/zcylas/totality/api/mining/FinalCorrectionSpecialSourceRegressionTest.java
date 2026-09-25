package zcylas.totality.api.mining;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Block Breaking V2 final correction pass: Repeater, Comparator, End Rod and Scaffolding are explicitly SPECIAL and carry no
 * authored (dormant) HP. Behaviour — vanilla ownership, no damage accumulation, tooltip rows, unchanged coverage — is verified
 * live (MiningVerification) and in a real client (BlockBreakingFinalCorrectionClientGameTest).
 */
class FinalCorrectionSpecialSourceRegressionTest {

    private static final String[] IDS = {"repeater", "comparator", "end_rod", "scaffolding"};

    @Test
    void theFourAreExplicitlySpecialAndNoLongerAuthoredWithHp() throws Exception {
        String src = Files.readString(Path.of("src/main/java/zcylas/totality/api/mining/VanillaBlockProfiles.java"));
        assertTrue(src.contains("special(\"repeater\", \"comparator\", \"end_rod\", \"scaffolding\");"));
        for (String id : IDS) {
            assertEquals(1, Pattern.compile("\"" + id + "\"").matcher(src).results().count(), id + " is named exactly once (SPECIAL)");
            assertFalse(Pattern.compile("ordinary\\([^;]*\"" + id + "\"").matcher(src).find(), id + " has no ordinary(...) HP");
        }
    }
}

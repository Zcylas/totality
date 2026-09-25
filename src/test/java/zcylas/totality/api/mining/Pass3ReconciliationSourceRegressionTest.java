package zcylas.totality.api.mining;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pass 3 reconciliation: the canonical exact-id CSV is well formed (176 unique rows, 147 ORDINARY + 29 SPECIAL) and the
 * reconciliation registration cannot spill over (no tag membership) or author a Required Mining Tier. Each row is verified
 * against the live engine by {@code MiningVerification.reconciliationCsv}.
 */
class Pass3ReconciliationSourceRegressionTest {

    private static final Path CSV = Path.of("Context/References/TOTALITY_BLOCK_BREAKING_V2_PASS3_RECONCILIATION_DELTA_176.csv");
    private static final Path DATASET = Path.of("src/main/java/zcylas/totality/api/mining/VanillaBlockProfiles.java");

    @Test
    void canonicalCsvHas176UniqueDecisions() throws Exception {
        List<String> lines = Files.readAllLines(CSV);
        Set<String> ids = new HashSet<>();
        int ordinary = 0, special = 0;
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] f = line.split(",", -1);
            assertTrue(ids.add(f[0]), "duplicate id " + f[0]);
            assertTrue(f[0].startsWith("minecraft:"), f[0]);
            if (f[2].equals("ORDINARY")) ordinary++;
            else if (f[2].equals("SPECIAL")) special++;
            else fail("unexpected classification " + f[2]);
        }
        assertEquals(176, ids.size());
        assertEquals(147, ordinary);
        assertEquals(29, special);
    }

    @Test
    void reconciliationUsesExactIdsOnlyAndAuthorsNoTier() throws Exception {
        String src = Files.readString(DATASET);
        int start = src.indexOf("private static void reconciliation() {");
        assertTrue(start >= 0);
        String body = src.substring(start, src.indexOf("\n    }\n", start));
        assertFalse(body.contains("tag(") || body.contains("specialTag(") || body.contains("BlockTags."),
                "reconciliation members are the CSV's exact ids; no tag membership that could spill over");
        assertFalse(body.contains("withRequiredTier"), "Required Mining Tier stays vanilla-derived");
        assertFalse(body.contains("transformation("), "no transformation pairs: cross-id preservation is a separate pass");
        assertFalse(src.contains("private static void review()"), "the ledger's review parkings are resolved by the delta");
    }
}

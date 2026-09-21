package zcylas.totality.api.soulgem;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.mob.stats.MobRank;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression guards for the Soul Gem foundation's architectural boundaries: no ordinal
 * reliance, no per-tier subclasses, no gem ladder beyond Petty/Common, no numeric soul-capacity
 * system, no Soul Trap. See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §18, §20-21.
 */
class SoulGemArchitectureSourceRegressionTest {

    private static final Path SOUL_GEM_ITEM =
            Path.of("src/main/java/zcylas/totality/item/soulgem/SoulGemItem.java");
    private static final Path SOUL_CAPTURE_SERVICE =
            Path.of("src/main/java/zcylas/totality/api/soulgem/SoulCaptureService.java");
    private static final Path SOUL_GEM_ACCEPTANCE_RULE =
            Path.of("src/main/java/zcylas/totality/api/soulgem/SoulGemAcceptanceRule.java");
    private static final Path MAGIC_ITEMS =
            Path.of("src/main/java/zcylas/totality/init/items/MagicItems.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    @Test
    void noSoulGemArchitectureFileUsesEnumOrdinal() throws Exception {
        for (Path p : new Path[]{SOUL_GEM_ITEM, SOUL_CAPTURE_SERVICE, SOUL_GEM_ACCEPTANCE_RULE}) {
            assertFalse(read(p).contains(".ordinal()"), p + " must not rely on enum ordinal");
        }
    }

    @Test
    void soulGemItemIsASingleReusableClassWithNoPerTierSubclass() throws Exception {
        String source = read(SOUL_GEM_ITEM);
        assertFalse(source.contains("extends SoulGemItem"), "no per-tier SoulGemItem subclass may exist");
        assertEquals(1, countOccurrences(source, "class SoulGemItem"),
                "SoulGemItem.java must declare exactly one SoulGemItem class");
    }

    @Test
    void noPettyOrCommonSpecificItemClassFilesExist() {
        assertFalse(Files.exists(Path.of("src/main/java/zcylas/totality/item/soulgem/PettySoulGemItem.java")));
        assertFalse(Files.exists(Path.of("src/main/java/zcylas/totality/item/soulgem/CommonSoulGemItem.java")));
    }

    @Test
    void magicItemsRegistersBothPettyAndCommonAsSoulGemItem() throws Exception {
        String source = read(MAGIC_ITEMS);
        assertTrue(source.contains("SoulGemItem PETTY_SOUL_GEM"));
        assertTrue(source.contains("SoulGemItem COMMON_SOUL_GEM"));
        assertTrue(source.contains("new SoulGemItem(properties,"), "both registrations must construct a SoulGemItem");
        assertTrue(source.contains("SoulGemAcceptanceRule.categoryUpToRank(SoulCategory.ORDINARY, MobRank.F)"),
                "Petty's authored acceptance rule (ORDINARY, F ceiling) must be present");
        assertTrue(source.contains("SoulGemAcceptanceRule.categoryUpToRank(SoulCategory.ORDINARY, MobRank.D)"),
                "Common's authored acceptance rule (ORDINARY, D ceiling) must be present");
    }

    @Test
    void noFutureGemLadderTierWasIntroduced() throws Exception {
        String source = read(MAGIC_ITEMS);
        for (String forbidden : new String[]{"LESSER_SOUL_GEM", "GREATER_SOUL_GEM", "GRAND_SOUL_GEM", "BLACK_SOUL_GEM"}) {
            assertFalse(source.contains(forbidden), "must not introduce " + forbidden + " yet");
        }
    }

    @Test
    void noNumericSoulStrengthOrCapacitySystemWasAdded() throws Exception {
        for (Path p : new Path[]{SOUL_GEM_ITEM, SOUL_CAPTURE_SERVICE, SOUL_GEM_ACCEPTANCE_RULE}) {
            String source = read(p);
            assertFalse(source.contains("soulStrength"), p + " must not contain soulStrength");
            assertFalse(source.contains("soulCapacity"), p + " must not contain soulCapacity");
            assertFalse(source.contains("gemCapacity"), p + " must not contain gemCapacity");
        }
    }

    @Test
    void noBlackSoulGemOrSoulTrapImplementationExists() {
        assertFalse(Files.exists(Path.of("src/main/java/zcylas/totality/item/soulgem/BlackSoulGemItem.java")));
        assertFalse(Files.exists(Path.of("src/main/java/zcylas/totality/api/soulgem/SoulTrap.java")));
        assertFalse(Files.exists(Path.of("src/main/java/zcylas/totality/api/soulgem/BlackSoulCategory.java")));
    }

    @Test
    void rankZeroIsNotAcceptedByAnyRegisteredGemsAuthoredCeiling() {
        // Neither Petty's nor Common's authored ceiling is ZERO or above it — this is a second,
        // independent proof alongside SoulCaptureEligibilityTest that no registered gem's own rule
        // would accidentally admit Rank 0 even if the global eligibility gate were bypassed.
        assertFalse(SoulGemAcceptanceRule.categoryUpToRank(SoulCategory.ORDINARY, MobRank.F)
                .acceptsRank(MobRank.ZERO));
        assertFalse(SoulGemAcceptanceRule.categoryUpToRank(SoulCategory.ORDINARY, MobRank.D)
                .acceptsRank(MobRank.ZERO));
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0, idx = 0;
        while ((idx = source.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}

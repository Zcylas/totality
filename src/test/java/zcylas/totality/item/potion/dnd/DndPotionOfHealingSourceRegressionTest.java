package zcylas.totality.item.potion.dnd;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source/import regression tests for the standalone D&D Potion of Healing, mirroring the
 * established convention in {@code ClientResourceParityReportPureBoundaryTest}: these check
 * source text directly because the classes under test (an {@code Item} subclass and its
 * registration site) cannot be instantiated under plain JUnit (see
 * {@link HealingPotionItemContractTest}'s class Javadoc for why).
 *
 * <p><b>Evidentiary limits (review-correction pass):</b> every test in this file is a source-text
 * regression <i>sentinel</i>, not a proof of runtime behavior. A sentinel can only assert that
 * particular tokens exist in a particular textual arrangement in the current source; it cannot
 * execute the method, cannot observe actual control flow, and can be defeated by a refactor that
 * preserves behavior but changes wording (e.g. renaming a local variable, or restructuring the
 * guard into a helper method). Where this file used to compare raw line indices to "prove" a call
 * is nested inside a guard, that was never runtime proof either — it is retained here (see
 * {@link #healingPotionItemGatesItsSingleHealCallBehindExactlyOneServerSideCheck()}) explicitly
 * relabeled as a sentinel, because the repository has no JavaParser/ArchUnit-style structural
 * analysis dependency and this task does not introduce one solely for this item. The genuine
 * runtime proof for server-authoritative, exactly-once healing application remains the manual
 * smoke test recorded in the implementation report.
 *
 * <p>Per review correction: comments and Javadoc in this package are expected to mention
 * "Alchemy" — e.g. to document why a class is independent of it, or to document the registry-id
 * collision with the Alchemy potion ladder. The actual required boundary is <i>no production
 * import</i> of an Alchemy class, so only import lines are checked, never whole-file text.
 */
class DndPotionOfHealingSourceRegressionTest {

    private static final Path HEALING_AMOUNT =
            Path.of("src/main/java/zcylas/totality/item/potion/dnd/HealingAmount.java");
    private static final Path HEALING_POTION_ITEM =
            Path.of("src/main/java/zcylas/totality/item/potion/dnd/HealingPotionItem.java");
    private static final Path DND_POTION_ITEMS =
            Path.of("src/main/java/zcylas/totality/init/items/DndPotionItems.java");
    private static final Path GENERATED_ITEM_MODEL =
            Path.of("src/main/generated/assets/totality/items/dnd_potion_of_healing.json");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    // ── Import boundary: no production dependency on any Alchemy class ─────────

    @Test
    void noneOfTheThreeNewFilesImportAnyAlchemyClass() throws Exception {
        for (Path path : List.of(HEALING_AMOUNT, HEALING_POTION_ITEM, DND_POTION_ITEMS)) {
            String source = read(path);
            List<String> importLines = source.lines().map(String::trim).filter(l -> l.startsWith("import ")).toList();
            for (String importLine : importLines) {
                assertFalse(importLine.toLowerCase(Locale.ROOT).contains("alchemy"),
                        path + " must not import any Alchemy class: " + importLine);
            }
        }
    }

    // ── Server-gate sentinel (source-text only — see class Javadoc) ─────────────

    @Test
    void healingPotionItemCallsHealExactlyOnce() throws Exception {
        String source = read(HEALING_POTION_ITEM);
        long healCallSites = source.lines().filter(l -> l.contains(".heal(")).count();
        assertEquals(1, healCallSites, "expected exactly one .heal( call site to avoid double-application");
    }

    @Test
    void healingPotionItemGatesItsSingleHealCallBehindExactlyOneServerSideCheck() throws Exception {
        // Sentinel only: this proves the source contains one guard token and one heal-call token,
        // with the heal-call token appearing on a later line than the guard token. It does not
        // execute the method and cannot prove the heal call is lexically nested inside the guard's
        // braces (e.g. it would not catch a pathological reformatting that de-nests them while
        // keeping line order). Runtime confirmation is the manual smoke test in the report.
        String source = read(HEALING_POTION_ITEM);
        long serverGateCount = source.lines().filter(l -> l.contains("isClientSide()")).count();
        assertEquals(1, serverGateCount, "expected exactly one isClientSide() gate");

        List<String> lines = source.lines().toList();
        int gateLine = -1;
        int healLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("isClientSide()")) gateLine = i;
            if (lines.get(i).contains(".heal(")) healLine = i;
        }
        assertTrue(gateLine >= 0 && healLine >= 0, "expected to find both the gate and the heal call");
        assertTrue(healLine > gateLine, "the .heal( call must appear textually after the isClientSide() guard");
    }

    @Test
    void healingPotionItemGuardIsANegationOfIsClientSide() throws Exception {
        // Sentinel only — confirms the literal token "!level.isClientSide()" is present, i.e. the
        // guard reads as server-only rather than its inverse. Does not execute the method.
        String source = read(HEALING_POTION_ITEM);
        assertTrue(source.contains("!level.isClientSide()"),
                "expected the heal application to be guarded by !level.isClientSide() (server-authoritative)");
    }

    // ── Constructor-invariant sentinel (source-text only) ────────────────────────

    @Test
    void healingPotionItemConstructorSourceContainsBothInvariantChecks() throws Exception {
        // Sentinel only: HealingPotionItem extends Item, whose constructor requires a bootstrapped,
        // non-frozen Minecraft item registry (confirmed by direct probing — see
        // HealingPotionItemContractTest's class Javadoc) — so the constructor cannot be invoked,
        // valid or invalid, under plain JUnit. This checks the validation source text exists;
        // it cannot prove the exceptions actually throw at runtime.
        String source = read(HEALING_POTION_ITEM);
        assertTrue(source.contains("Objects.requireNonNull(healingAmount"),
                "expected the constructor to null-check healingAmount");
        assertTrue(source.contains("useDurationTicks <= 0"),
                "expected the constructor to reject a non-positive useDurationTicks");
    }

    // ── Registration formula sentinel (whitespace-normalized) ────────────────────

    @Test
    void normalPotionOfHealingIsRegisteredWithExactlyTwoD4PlusTwoAndThirtyTwoTicks() throws Exception {
        String source = read(DND_POTION_ITEMS);
        String normalized = source.replaceAll("\\s+", " ");
        assertTrue(normalized.contains("HealingAmount.dice(2, Dice.D4, 2), 32"),
                "expected the normal Potion of Healing to be registered with HealingAmount.dice(2, Dice.D4, 2) and a 32-tick duration (whitespace-normalized)");
    }

    @Test
    void normalPotionOfHealingUsesTheDistinctDndRegistryIdNotTheAlchemyOccupiedId() throws Exception {
        String source = read(DND_POTION_ITEMS);
        assertTrue(source.contains("\"dnd_potion_of_healing\""),
                "expected the distinct registry id \"dnd_potion_of_healing\" (potion_of_healing is already used by Alchemy)");

        // The bare id "potion_of_healing" is expected to appear in an explanatory comment
        // documenting the collision — that is required, not forbidden (see implementation report).
        // What must never happen is the *registration call* itself using that bare id as its
        // string literal argument.
        List<String> registerItemArgLines = source.lines()
                .map(String::trim)
                .filter(l -> l.equals("\"potion_of_healing\","))
                .toList();
        assertTrue(registerItemArgLines.isEmpty(),
                "must not silently reuse Alchemy's existing \"potion_of_healing\" id as the registerItem(...) name argument");
    }

    // ── Rendering-independence sentinel ───────────────────────────────────────────

    @Test
    void generatedClientItemJsonDoesNotUseTheAlchemyOwnedTintType() throws Exception {
        String json = read(GENERATED_ITEM_MODEL);
        assertFalse(json.contains("totality:potion_color"),
                "the D&D potion's generated client-item JSON must not reference Alchemy's \"totality:potion_color\" tint type");
    }

    @Test
    void generatedClientItemJsonUsesTheVanillaConstantTintType() throws Exception {
        // This task uses Minecraft's own built-in constant tint source rather than introducing a
        // new Totality tint class, so there is no new tint-source file to check for Alchemy
        // imports — the generated JSON referencing "minecraft:constant" is itself the evidence.
        String json = read(GENERATED_ITEM_MODEL);
        assertTrue(json.contains("minecraft:constant"),
                "expected the D&D potion's generated client-item JSON to use vanilla's built-in \"minecraft:constant\" tint type");
    }

    @Test
    void modelProviderSourceDoesNotUsePotionTintSourceForTheDndItem() throws Exception {
        Path modelProvider = Path.of("src/main/java/zcylas/totality/datagen/ModModelProvider.java");
        String source = read(modelProvider);
        List<String> lines = source.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("DndPotionItems.POTION_OF_HEALING")) {
                // The tinted-model call for the D&D item follows on a nearby line; scan a small
                // window after the reference for the forbidden Alchemy-owned tint source.
                int end = Math.min(lines.size(), i + 4);
                for (int j = i; j < end; j++) {
                    assertFalse(lines.get(j).contains("PotionTintSource"),
                            "the D&D potion's model registration must not use PotionTintSource (Alchemy-owned): line " + j);
                }
            }
        }
    }
}

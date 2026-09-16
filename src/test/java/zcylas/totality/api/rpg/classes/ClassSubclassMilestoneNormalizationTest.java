package zcylas.totality.api.rpg.classes;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.classes.barbarian.BarbarianClass;
import zcylas.totality.api.rpg.classes.monk.MonkClass;
import zcylas.totality.api.rpg.classes.wizard.WizardClass;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the D&D 2024-style subclass milestone normalization (2026-09-16, see the
 * Class Tab Quick Level-Up implementation report's amended sections): Wizard's subclass unlock
 * moves from class level 2 (stale) to class level 3, matching Barbarian and Monk, which were
 * already correct.
 *
 * <p>The {@code ClassData.subclassUnlockClassLevel()} assertions below are real, executing checks
 * against the production data records (no registration/bootstrap needed — {@code DATA} is a plain
 * public static final field). Whether each class's {@code ClassLevelUpRegistry} handler actually
 * *fires* {@code OpenSubclassSelectionPayload} at the right moment cannot be executed under plain
 * JUnit (the handler calls {@code ServerPlayNetworking.send}, which needs a live connection — the
 * same networking boundary documented elsewhere in this suite), so the milestone-comparison logic
 * itself is pinned as a source-text sentinel instead: each handler must compare against {@code
 * DATA.subclassUnlockClassLevel()} (the single source of truth this test also asserts against),
 * never a re-typed literal that could drift out of sync with it again the way Wizard's did.
 */
class ClassSubclassMilestoneNormalizationTest {

    @Test
    void wizardSubclassMilestoneIsNowClassLevelThree() {
        assertEquals(3, WizardClass.DATA.subclassUnlockClassLevel(),
                "Wizard's Arcane Tradition must now unlock at class level 3, matching Barbarian/Monk");
    }

    @Test
    void barbarianSubclassMilestoneRemainsClassLevelThree() {
        assertEquals(3, BarbarianClass.DATA.subclassUnlockClassLevel());
    }

    @Test
    void monkSubclassMilestoneRemainsClassLevelThree() {
        assertEquals(3, MonkClass.DATA.subclassUnlockClassLevel());
    }

    @Test
    void eachHandlerComparesAgainstItsOwnClassDataMilestoneNotAReTypedLiteral() throws Exception {
        assertHandlerUsesDataMilestone(
                "src/main/java/zcylas/totality/api/rpg/classes/wizard/WizardClass.java");
        assertHandlerUsesDataMilestone(
                "src/main/java/zcylas/totality/api/rpg/classes/barbarian/BarbarianClass.java");
        assertHandlerUsesDataMilestone(
                "src/main/java/zcylas/totality/api/rpg/classes/monk/MonkClass.java");
    }

    private void assertHandlerUsesDataMilestone(String path) throws Exception {
        Path source = Path.of(path);
        assertTrue(Files.exists(source), "expected to find source file at " + source);
        String text = Files.readString(source);
        assertTrue(text.contains("classLevel == DATA.subclassUnlockClassLevel()"),
                path + " must compare classLevel against DATA.subclassUnlockClassLevel(), not a "
                        + "re-typed literal that could silently drift out of sync (exactly the bug "
                        + "this normalization fixed for Wizard)");
    }

    @Test
    void eachHandlerChecksItsOwnClassesSubclassNotAnotherClasss() throws Exception {
        assertHandlerChecksOwnClass(
                "src/main/java/zcylas/totality/api/rpg/classes/wizard/WizardClass.java", "WIZARD_ID");
        assertHandlerChecksOwnClass(
                "src/main/java/zcylas/totality/api/rpg/classes/barbarian/BarbarianClass.java", "BARBARIAN_ID");
        assertHandlerChecksOwnClass(
                "src/main/java/zcylas/totality/api/rpg/classes/monk/MonkClass.java", "MONK_ID");
    }

    private void assertHandlerChecksOwnClass(String path, String ownClassIdConstant) throws Exception {
        Path source = Path.of(path);
        String text = Files.readString(source);
        assertTrue(text.contains("hasSubclass(TotalityClasses." + ownClassIdConstant + ")"),
                path + " must check hasSubclass(TotalityClasses." + ownClassIdConstant + ") — its own "
                        + "class's subclass status, never a global/parameterless check that a "
                        + "different owned class's subclass could satisfy instead");
    }
}

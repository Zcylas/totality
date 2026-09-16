package zcylas.totality.screen.character.tabs;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinel for the Class Tab Quick Level-Up "+" button (see {@code
 * TOTALITY_CLASS_TAB_QUICK_LEVEL_UP_IMPLEMENTATION_2026-09-16.md}). A source-text sentinel, not a
 * runtime proof — {@code ClassTab} is a client GUI tab that needs a bootstrapped, GL-initialized
 * {@code Minecraft}/{@code Font} to render/click-test, unavailable under plain JUnit (the same
 * constraint {@code Phase3CConsumerMigrationSourceRegressionTest} documents elsewhere in this
 * suite). What the button's server-authoritative target actually does is covered by executing
 * tests in {@code SelectSubclassHandlerTest}; this test pins the structural guarantees the task
 * requires of the client-side button itself.
 */
class ClassTabQuickLevelUpSourceRegressionTest {

    private static final Path SOURCE =
            Path.of("src/main/java/zcylas/totality/screen/character/tabs/ClassTab.java");

    private static String read() throws Exception {
        assertTrue(Files.exists(SOURCE), "expected to find source file at " + SOURCE);
        return Files.readString(SOURCE);
    }

    @Test
    void theQuickLevelUpButtonSendsAddClassLevelPayload() throws Exception {
        String source = read();
        int start = source.indexOf("for (QuickLevelButton btn : quickLevelButtons)");
        assertTrue(start >= 0, "expected to find the quick level-up button click loop");
        int end = source.indexOf("@Override", start);
        assertTrue(end > start, "expected to find the end of mouseClicked");
        String body = source.substring(start, end);

        assertTrue(body.contains("new zcylas.totality.networking.classes.AddClassLevelPayload("),
                "the quick level-up button must send the same server-authoritative "
                        + "AddClassLevelPayload the full Class Screen flow sends");
        assertTrue(body.contains("btn.enabled()"),
                "a disabled button must never be actionable, even if its bounds are still clicked");
    }

    @Test
    void theQuickLevelUpButtonNeverMutatesClassStateDirectly() throws Exception {
        String source = read();

        // PlayerClassComponent.toClassLevel(...) is a pre-existing, pure, read-only static formula
        // call (already used by the SPEND CLASS POINT indicator) — allowed. Every actual mutation
        // method on the component must never be called from this client GUI class.
        assertFalse(source.contains("ClassComponents.get("),
                "ClassTab must never reach into the server-side class component directly");
        for (String mutation : new String[] {
                ".addClassLevel(", ".selectSubclass(", ".selectClass(", ".setClassLevel(",
                ".selectCovenant(", ".resetClass("
        }) {
            assertFalse(source.contains(mutation),
                    "ClassTab must never call " + mutation + " directly — only a server-authoritative "
                            + "handler may mutate class-progression state");
        }
        assertFalse(source.contains("SelectSubclassPayload"),
                "ClassTab's shortcut must never send a subclass-selection payload itself — reaching "
                        + "a subclass milestone must always go through the normal forced "
                        + "OpenSubclassSelectionPayload flow, never be special-cased here");
    }

    @Test
    void theQuickLevelUpButtonHasNoPerClassBranching() throws Exception {
        String source = read();

        // The generic requirement: no "if this class == Wizard/Barbarian/..." shortcut anywhere.
        assertFalse(source.contains("BARBARIAN_ID") && source.contains("WIZARD_ID"),
                "the quick level-up mechanism must not special-case specific classes by id");
        assertFalse(source.toLowerCase().contains("if (classdata.id()"),
                "no per-class identity branch is expected in the quick level-up path");
    }

    @Test
    void theQuickLevelUpButtonEnablementIsTiedToUnspentClassPoints() throws Exception {
        String source = read();

        assertTrue(source.contains("boolean canSpendAPoint = unspentPoints > 0;"),
                "button enablement must be gated on the same shared unspent-class-points pool the "
                        + "existing SPEND CLASS POINT button already uses — there is no separate "
                        + "per-class maximum in the current architecture");
    }

    @Test
    void quickLevelButtonsAreOnlyEverCreatedForEntriesInTheOwnedClassLevelsMap() throws Exception {
        // Structural proof that an unowned class can never receive a button: both render paths
        // (single-class and multiclass) only ever call drawQuickLevelButton for a class id that is
        // already a key in ClientClassManager.getClassLevels() (the primary class, or a multiclass
        // entry) — there is no third call site.
        String source = read();
        int count = 0;
        int idx = 0;
        while ((idx = source.indexOf("drawQuickLevelButton(", idx)) >= 0) {
            count++;
            idx += 1;
        }
        assertEquals(3, count,
                "expected exactly one method definition plus two call sites (single-class and "
                        + "multiclass) — a new call site would need auditing for unowned-class safety");
    }
}

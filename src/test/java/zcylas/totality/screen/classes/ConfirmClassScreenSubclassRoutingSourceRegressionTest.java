package zcylas.totality.screen.classes;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinel for the pre-existing production bug fixed alongside the Class Tab
 * Quick Level-Up feature (see {@code TOTALITY_CLASS_TAB_QUICK_LEVEL_UP_IMPLEMENTATION_2026-09-16.md}):
 * a source-text sentinel, not a runtime proof — {@code ConfirmClassScreen} is a client GUI screen
 * that needs a bootstrapped, GL-initialized {@code Minecraft}/{@code Font} to execute, unavailable
 * under plain JUnit (the same constraint documented by {@code
 * Phase3CConsumerMigrationSourceRegressionTest} elsewhere in this suite). The actual subclass
 * application logic this routing feeds into is covered by executing tests in {@code
 * SelectSubclassHandlerTest}; this test only pins that the client screen actually routes to it
 * under the right condition, and preserves the two pre-existing branches otherwise.
 */
class ConfirmClassScreenSubclassRoutingSourceRegressionTest {

    private static final Path SOURCE =
            Path.of("src/main/java/zcylas/totality/screen/classes/ConfirmClassScreen.java");

    private static String read() throws Exception {
        assertTrue(Files.exists(SOURCE), "expected to find source file at " + SOURCE);
        return Files.readString(SOURCE);
    }

    private static String confirmBranchBody(String source) {
        int start = source.indexOf("if (isNext(mx, my)) {");
        assertTrue(start >= 0, "expected to find the CONFIRM (isNext) branch");
        int end = source.indexOf("return super.mouseClicked(mouse, dc);", start);
        assertTrue(end > start, "expected to find the end of the CONFIRM branch");
        return source.substring(start, end);
    }

    @Test
    void confirmingASubclassChoiceForAnAlreadyOwnedClassSendsSelectSubclassPayload() throws Exception {
        String body = confirmBranchBody(read());

        assertTrue(body.contains("ClientClassManager.getClassLevels().containsKey(cls.id())"),
                "must determine already-owned-class status from the synced client class mirror, "
                        + "not from the one-shot IS_MULTICLASSING flag, since that flag is already "
                        + "consumed by the time this screen is reached via ordinary leveling");
        assertTrue(body.contains("sub != null && alreadyOwnsClass"),
                "must route to the new subclass-application path only when a subclass was chosen "
                        + "AND the class is already owned");
        assertTrue(body.contains("new zcylas.totality.networking.classes.SelectSubclassPayload("),
                "must send SelectSubclassPayload for an already-owned class's subclass confirmation");
    }

    @Test
    void theMulticlassNewClassBranchIsPreservedAndSendsAddClassLevelPayload() throws Exception {
        String body = confirmBranchBody(read());

        assertTrue(body.contains("ClassScreenMode.IS_MULTICLASSING"),
                "the pre-existing multiclass-new-class branch must still be present");
        assertTrue(body.contains("new AddClassLevelPayload(cls.id().toString())"),
                "leveling a brand-new class via multiclassing must still send AddClassLevelPayload");
    }

    @Test
    void theFirstTimeClassSelectionBranchIsPreservedAndSendsSelectClassPayload() throws Exception {
        String body = confirmBranchBody(read());

        assertTrue(body.contains("new SelectClassPayload("),
                "first-time class selection must still send SelectClassPayload — unchanged by this fix");
    }

    @Test
    void theNewBranchIsCheckedBeforeTheMulticlassAndFirstTimeBranches() throws Exception {
        // Ordering matters: IS_MULTICLASSING could still (incidentally) be true in some edge case
        // while sub != null and the class is already owned — the subclass-application branch must
        // win, since a subclass choice for an owned class can never legitimately mean "start a new
        // multiclass" or "first-time select".
        String body = confirmBranchBody(read());

        int subclassBranch = body.indexOf("sub != null && alreadyOwnsClass");
        int multiclassBranch = body.indexOf("ClassScreenMode.IS_MULTICLASSING) {");
        assertTrue(subclassBranch >= 0 && multiclassBranch >= 0);
        assertTrue(subclassBranch < multiclassBranch,
                "the already-owned-class subclass check must be evaluated before the multiclass branch");
    }
}

package zcylas.totality.api.dialogue;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinel for the dialogue Ability Check bonus routing fix (2026-09-16) — a
 * source-text sentinel, not a runtime proof, matching {@code
 * BlessLifecycleSourceRegressionTest}'s own established precedent for the same constraint:
 * exercising {@code DialogueSessionManager.handleChoice} end-to-end requires a live {@code
 * ServerPlayer} and an active dialogue session, unreachable under plain JUnit.
 *
 * <p>Before this fix, every dialogue roll (Persuasion, Intimidation, Investigation — all Ability
 * Checks, never Saving Throws) built its bonus list via {@code RollModifierRegistry
 * .resolveSaveBonusList}, so a save-only effect like Bless (+1d4 to attacks and saves, never
 * checks, per D&amp;D 5e) incorrectly applied to e.g. a Banker Persuasion check. The generic
 * bonus-channel separation itself ({@code getCheckBonusList}/{@code resolveCheckBonusList} vs.
 * {@code getSaveBonusList}/{@code resolveSaveBonusList}) is covered by real, executing tests in
 * {@code RollModifierRegistryCheckBonusChannelTest} instead — this sentinel only confirms
 * dialogue's own call site was actually updated to use the correct channel.
 */
class DialogueSessionManagerCheckBonusRoutingSourceRegressionTest {

    private static final Path SOURCE = Path.of(
            "src/main/java/zcylas/totality/api/dialogue/DialogueSessionManager.java");

    private static String read() throws Exception {
        assertTrue(Files.exists(SOURCE), "expected to find source file at " + SOURCE);
        return Files.readString(SOURCE);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "expected to locate method: " + signature);
        int end = source.indexOf("\n    }", start);
        assertTrue(end > start, "expected to locate the end of method: " + signature);
        return source.substring(start, end);
    }

    @Test
    void handleChoiceBuildsDialogueRollBonusesFromTheAbilityCheckChannelNotTheSavingThrowChannel() throws Exception {
        String body = methodBody(read(), "public static void handleChoice(ServerPlayer player, int choiceIndex)");
        assertTrue(body.contains("RollModifierRegistry.resolveCheckBonusList(player, scoreForModifiers)"),
                "handleChoice must build a dialogue roll's bonus list from resolveCheckBonusList — every "
                        + "dialogue roll (Persuasion, Intimidation, Investigation, ...) is an Ability Check, "
                        + "never a Saving Throw, so it must not receive Saving-Throw-only effects like Bless.");
        assertFalse(body.contains("RollModifierRegistry.resolveSaveBonusList"),
                "handleChoice must no longer call resolveSaveBonusList at all — that channel is reserved "
                        + "for real Saving Throw resolution (SavingThrow.java), not dialogue Ability Checks.");
    }
}

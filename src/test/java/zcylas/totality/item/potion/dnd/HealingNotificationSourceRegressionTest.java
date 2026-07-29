package zcylas.totality.item.potion.dnd;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinels for the Potion of Healing notification wiring. Every test in this
 * file is a source-text sentinel, not runtime proof — same evidentiary limits documented in
 * {@link DndPotionOfHealingSourceRegressionTest}'s class Javadoc, for the same reason: the actual
 * healing/notification call path runs inside {@code HealingPotionItem.finishUsingItem}, which
 * cannot be invoked under plain JUnit (the class extends {@code Item}; see
 * {@link HealingPotionItemContractTest}'s class Javadoc). The healing formula/roll logic itself
 * (the part that *can* be constructed and executed for real) is covered behaviorally by
 * {@code HealingAmountTest}, {@code HealingRollResultTest}, and
 * {@code HealingRollNotificationFormatMessageTest} instead.
 */
class HealingNotificationSourceRegressionTest {

    private static final Path HEALING_POTION_ITEM =
            Path.of("src/main/java/zcylas/totality/item/potion/dnd/HealingPotionItem.java");
    private static final Path HEALING_ROLL_NOTIFICATION =
            Path.of("src/main/java/zcylas/totality/networking/potion/HealingRollNotification.java");
    private static final Path HEALING_AMOUNT =
            Path.of("src/main/java/zcylas/totality/item/potion/dnd/HealingAmount.java");
    private static final Path HEALING_ROLL_RESULT =
            Path.of("src/main/java/zcylas/totality/item/potion/dnd/HealingRollResult.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    // ── Notification color ────────────────────────────────────────────────────

    @Test
    void healingRollNotificationSendsWithGreenColor() throws Exception {
        String source = read(HEALING_ROLL_NOTIFICATION);
        assertTrue(source.contains("SendNotificationPayload.GREEN"),
                "expected the healing notification to use SendNotificationPayload.GREEN");
    }

    // ── Roll-once discipline ──────────────────────────────────────────────────

    @Test
    void healingPotionItemCallsRollDetailedExactlyOnce() throws Exception {
        String source = read(HEALING_POTION_ITEM);
        long callSites = source.lines().filter(l -> l.contains(".rollDetailed(")).count();
        assertEquals(1, callSites, "expected exactly one rollDetailed(...) call site — the roll must not be performed again for formatting");
    }

    @Test
    void healingPotionItemDoesNotCallTheSimpleRollMethodAlongsideRollDetailed() throws Exception {
        String source = read(HEALING_POTION_ITEM);
        assertFalse(source.contains("healingAmount.roll("),
                "expected only rollDetailed(...) to be used in the item — a separate .roll(...) call would risk a second, different roll");
    }

    // ── LivingEntity-generic healing, ServerPlayer confined to presentation ───

    @Test
    void healSiteIsNotNestedInsideTheServerPlayerCheck() throws Exception {
        // Sentinel only (see class Javadoc): confirms user.heal( appears on an earlier line than
        // the "instanceof ServerPlayer" check, i.e. healing is not conditioned on the actor being
        // a player. Cannot prove actual nesting/braces structurally.
        String source = read(HEALING_POTION_ITEM);
        List<String> lines = source.lines().toList();
        int healLine = -1;
        int serverPlayerCheckLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains("user.heal(")) healLine = i;
            if (lines.get(i).contains("instanceof ServerPlayer")) serverPlayerCheckLine = i;
        }
        assertTrue(healLine >= 0, "expected to find a user.heal( call");
        assertTrue(serverPlayerCheckLine >= 0, "expected to find an instanceof ServerPlayer check");
        assertTrue(healLine < serverPlayerCheckLine,
                "user.heal( must occur before (outside of) the instanceof ServerPlayer check — healing must not require a player");
    }

    @Test
    void notificationCallSiteIsGuardedByBothActualHealingAndServerPlayerChecks() throws Exception {
        String source = read(HEALING_POTION_ITEM);
        String normalized = source.replaceAll("\\s+", " ");
        assertTrue(normalized.contains("if (actualHealing > 0 && user instanceof ServerPlayer serverPlayer)"),
                "expected the notification call to require both actualHealing > 0 and a ServerPlayer actor (whitespace-normalized)");
    }

    // ── No forbidden dependencies ──────────────────────────────────────────────

    @Test
    void noneOfTheFourFilesImportAlchemyDamageRollNotificationOrCombatText() throws Exception {
        List<String> forbiddenSubstrings = List.of("alchemy", "damagerollnotification", "combattext");
        for (Path path : List.of(HEALING_POTION_ITEM, HEALING_ROLL_NOTIFICATION, HEALING_AMOUNT, HEALING_ROLL_RESULT)) {
            String source = read(path);
            List<String> importLines = source.lines().map(String::trim).filter(l -> l.startsWith("import ")).toList();
            for (String importLine : importLines) {
                String lower = importLine.toLowerCase(Locale.ROOT);
                for (String forbidden : forbiddenSubstrings) {
                    assertFalse(lower.contains(forbidden),
                            path + " must not import anything matching \"" + forbidden + "\": " + importLine);
                }
            }
        }
    }

    @Test
    void healingAmountAndHealingRollResultDoNotImportServerPlayerOrNotificationClasses() throws Exception {
        // HealingAmount/HealingRollResult must stay actor-generic and presentation-free — only
        // HealingPotionItem (ServerPlayer only, for the optional presentation branch) and
        // HealingRollNotification (ServerPlayer + notification payload, by design) may reference
        // these.
        List<String> forbiddenSubstrings = List.of("serverplayer", "notificationmanager", "sendnotificationpayload");
        for (Path path : List.of(HEALING_AMOUNT, HEALING_ROLL_RESULT)) {
            String source = read(path);
            List<String> importLines = source.lines().map(String::trim).filter(l -> l.startsWith("import ")).toList();
            for (String importLine : importLines) {
                String lower = importLine.toLowerCase(Locale.ROOT);
                for (String forbidden : forbiddenSubstrings) {
                    assertFalse(lower.contains(forbidden),
                            path + " must stay LivingEntity-generic and presentation-free, must not import \"" + forbidden + "\": " + importLine);
                }
            }
        }
    }

    @Test
    void healingRollNotificationUsesTheSharedHealthDisplayConversionNotAnIndependentLiteral() throws Exception {
        String source = read(HEALING_ROLL_NOTIFICATION);
        assertTrue(source.contains("RpgDisplayUtils.toDisplayHp("),
                "expected the shared Health display conversion (RpgDisplayUtils.toDisplayHp) to be used");
        assertFalse(source.contains("* 5") || source.contains("*5"),
                "must not introduce an independent literal 5 display multiplier");
    }
}

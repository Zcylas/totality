package zcylas.totality.networking.combat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinels for the Part D combined combat-roll notification wiring in
 * {@code CombatResolver.handleHit}. Every test here is a source-text sentinel, not runtime proof:
 * {@code handleHit} operates on real {@code LivingEntity}/{@code ServerPlayer} attackers, which —
 * like every other {@code Entity} subtype touched by this project's test suite — require a
 * bootstrapped, unfrozen Minecraft registry to construct, unreachable under plain JUnit (see
 * {@code HealingPotionItemContractTest}'s class Javadoc for the same constraint documented
 * elsewhere in this codebase). The actual message content these call sites produce is covered by
 * real, executing tests in {@code CombatRollNotificationFormatMessageTest} instead.
 */
class CombatRollNotificationSourceRegressionTest {

    private static final Path COMBAT_RESOLVER =
            Path.of("src/main/java/zcylas/totality/api/rpg/combat/CombatResolver.java");
    private static final Path COMBAT_ROLL_NOTIFICATION =
            Path.of("src/main/java/zcylas/totality/networking/combat/CombatRollNotification.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    @Test
    void damageRollNotificationClassWasRemovedNotLeftAsDeadDuplicate() {
        assertFalse(Files.exists(Path.of("src/main/java/zcylas/totality/networking/combat/DamageRollNotification.java")),
                "expected the old damage-only notification class to be removed once nothing used it anymore");
    }

    @Test
    void combatResolverNoLongerImportsTheOldDamageOnlyNotification() throws Exception {
        String source = read(COMBAT_RESOLVER);
        List<String> importLines = source.lines().map(String::trim).filter(l -> l.startsWith("import ")).toList();
        for (String importLine : importLines) {
            assertFalse(importLine.contains("DamageRollNotification"), "must not import the removed class: " + importLine);
        }
        assertTrue(source.contains("import zcylas.totality.networking.combat.CombatRollNotification;"),
                "expected CombatResolver to import the new combined notification");
    }

    @Test
    void handleHitSendsExactlyOneCombatRollNotificationCallOnAMissAndOneOnAHit() throws Exception {
        // Exactly two call sites total: the miss branch (early return) and the hit/crit branch
        // (after damage is applied) — never both for the same resolved attack, since the miss
        // branch returns before reaching the hit branch.
        String source = read(COMBAT_RESOLVER);
        long callSites = source.lines().filter(l -> l.contains("CombatRollNotification.send(")).count();
        assertEquals(2, callSites,
                "expected exactly two CombatRollNotification.send(...) call sites in CombatResolver: one for miss, one for hit/crit");
    }

    @Test
    void missCallSitePassesNullDamageResult() throws Exception {
        String source = read(COMBAT_RESOLVER);
        String normalized = source.replaceAll("\\s+", " ");
        assertTrue(normalized.contains("CombatRollNotification.send(p, label, attackResult, null, null, List.of());"),
                "expected the miss branch to pass a null damage result, producing no DMG line");
    }

    @Test
    void hitCallSitePassesARealDamageResult() throws Exception {
        String source = read(COMBAT_RESOLVER);
        String normalized = source.replaceAll("\\s+", " ");
        assertTrue(normalized.contains("CombatRollNotification.send(p, label, attackResult, dmg, abilityScore, extraBonuses);"),
                "expected the hit/crit branch to pass the retained damage roll result");
    }

    @Test
    void attackRollResultIsNeverRerolledForPresentation() throws Exception {
        // handleHit must receive the AttackRoll.Result produced by AttackRoll.roll(...) and pass it
        // straight through — never construct a second one.
        String source = read(COMBAT_RESOLVER);
        long attackRollCallSites = source.lines().filter(l -> l.contains("AttackRoll.roll(")).count();
        assertEquals(3, attackRollCallSites,
                "expected exactly three AttackRoll.roll(...) call sites (the two weapon overloads plus resolveSpellAttack), none inside handleHit itself");

        String handleHitBody = extractMethodBody(source, "private static void handleHit");
        assertFalse(handleHitBody.isEmpty(), "expected to find handleHit's method body");
        assertFalse(handleHitBody.contains("AttackRoll.roll("),
                "handleHit must not itself call AttackRoll.roll — it only formats an already-produced result");
    }

    @Test
    void combatRollNotificationDoesNotGuessPixelWidthsOrInsertManualWrapping() throws Exception {
        String source = read(COMBAT_ROLL_NOTIFICATION);
        assertFalse(source.contains("Font") || source.contains("font.split") || source.contains("guiWidth"),
                "CombatRollNotification must not perform its own width measurement/wrapping — that is NotificationManager's job (Part C)");
    }

    @Test
    void combatRollNotificationUsesAtMostThreeExplicitlyAuthoredSemanticLines() throws Exception {
        String source = read(COMBAT_ROLL_NOTIFICATION);
        // The formatter appends at most two '\n' characters total (label+outcome / ATK / optional DMG).
        long newlineAppends = source.lines().filter(l -> l.contains("append('\\n')")).count();
        assertEquals(2, newlineAppends,
                "expected at most 3 semantic lines: exactly 2 authored newline joins (label\\nATK, and ATK\\nDMG when present)");
    }

    /** Extracts a method's brace-balanced body text by name, for a narrow structural check. */
    private static String extractMethodBody(String source, String methodName) {
        int idx = source.indexOf(methodName + "(");
        if (idx < 0) return "";
        int braceStart = source.indexOf('{', idx);
        if (braceStart < 0) return "";
        int depth = 0;
        for (int i = braceStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(braceStart, i + 1);
            }
        }
        return source.substring(braceStart);
    }
}

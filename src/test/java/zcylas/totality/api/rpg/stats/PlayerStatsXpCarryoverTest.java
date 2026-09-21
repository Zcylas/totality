package zcylas.totality.api.rpg.stats;

import net.minecraft.core.HolderLookup;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.classes.PlayerClassComponent;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the Player XP carryover bug: {@link PlayerStats#addCharacterXp(int)}
 * previously called {@link PlayerStats#tryLevelUp()} at most once per grant, which unconditionally
 * reset {@code characterXp} to 0 — discarding any XP beyond the threshold it just consumed, and
 * only ever advancing one Player Level per grant even when the awarded amount covered several
 * thresholds at once (e.g. Level 1 at 0/100 XP gaining 120 XP produced Level 2 at 0 XP instead of
 * the correct 20 XP remainder). The fix loops, consuming exactly
 * {@link PlayerStats#getXpRequiredForNextLevel()} (recalculated each iteration, since it changes
 * with level) and carrying the remainder forward, until the remaining XP is below the next
 * requirement or the level cap is reached.
 *
 * <p>All XP requirements below are computed from the real formula, {@code (level + 3) * 25} — not
 * hardcoded assumptions — so these tests break if that formula ever changes without the test
 * being updated to match, rather than silently testing stale numbers.
 */
class PlayerStatsXpCarryoverTest {

    private static int requiredAt(int level) {
        return (level + 3) * 25;
    }

    @Test
    void xpBelowThresholdDoesNotLevelUpAndIsUnchanged() {
        PlayerStats stats = new PlayerStats();
        int req = requiredAt(1);
        boolean leveledUp = stats.addCharacterXp(req - 1);

        assertFalse(leveledUp);
        assertEquals(1, stats.getLevel());
        assertEquals(req - 1, stats.getCharacterXp());
    }

    @Test
    void xpExactlyAtThresholdLevelsUpWithZeroRemainder() {
        PlayerStats stats = new PlayerStats();
        boolean leveledUp = stats.addCharacterXp(requiredAt(1));

        assertTrue(leveledUp);
        assertEquals(2, stats.getLevel());
        assertEquals(0, stats.getCharacterXp());
    }

    @Test
    void xpSlightlyAboveThresholdLevelsUpAndPreservesTheRemainder() {
        // The exact bug scenario from the report: Level 1 at 0/100 XP, +120 XP.
        PlayerStats stats = new PlayerStats();
        int req = requiredAt(1);
        assertEquals(100, req, "sanity check: Level 1 currently requires 100 XP");

        boolean leveledUp = stats.addCharacterXp(req + 20);

        assertTrue(leveledUp);
        assertEquals(2, stats.getLevel());
        assertEquals(20, stats.getCharacterXp(), "the 20 overflow XP must carry over, not be lost");
    }

    @Test
    void exactlyEnoughXpForTwoLevelsLandsOnZeroRemainder() {
        // Demonstrates the changing per-level requirement is honored across both steps:
        // Level 1 needs 100, then Level 2 needs 125 — not 100 again.
        PlayerStats stats = new PlayerStats();
        int total = requiredAt(1) + requiredAt(2);

        boolean leveledUp = stats.addCharacterXp(total);

        assertTrue(leveledUp);
        assertEquals(3, stats.getLevel());
        assertEquals(0, stats.getCharacterXp());
    }

    @Test
    void oneGrantCanCrossTwoLevelsAndPreserveTheFinalRemainder() {
        PlayerStats stats = new PlayerStats();
        int total = requiredAt(1) + requiredAt(2) + 25; // one full level 1->2->3, plus leftover

        boolean leveledUp = stats.addCharacterXp(total);

        assertTrue(leveledUp);
        assertEquals(3, stats.getLevel());
        assertEquals(25, stats.getCharacterXp());
    }

    @Test
    void oneLargeGrantCanCrossSeveralLevels() {
        // Level 1 -> 4, computed independently of the loop under test.
        PlayerStats stats = new PlayerStats();
        int total = requiredAt(1) + requiredAt(2) + requiredAt(3) + 125;

        boolean leveledUp = stats.addCharacterXp(total);

        assertTrue(leveledUp);
        assertEquals(4, stats.getLevel());
        assertEquals(125, stats.getCharacterXp());
    }

    @Test
    void multiLevelGrantCorrectlyCrossesTheLevelFiveClassPointMilestone() {
        // Player Level 3 -> 6 in one grant, crossing the Level 5 Class Level milestone
        // mid-jump. Class Level entitlement is a pure function of the FINAL Player Level
        // (PlayerClassComponent.toClassLevel), not an incrementally-tracked counter, so it
        // is correct here with no special per-level "replay" needed — see that class.
        PlayerStats stats = new PlayerStats();
        stats.setLevelDirectly(3);
        int total = requiredAt(3) + requiredAt(4) + requiredAt(5) + 30;

        stats.addCharacterXp(total);

        assertEquals(6, stats.getLevel());
        assertEquals(30, stats.getCharacterXp());
        assertEquals(2, PlayerClassComponent.toClassLevel(stats.getLevel()),
                "Player Level 6 (past the Level 5 milestone) must already reflect 2 total Class Level points");
    }

    @Test
    void cannotExceedTheLevelCapAndOverflowXpIsNotRetainedAtCap() {
        PlayerStats stats = new PlayerStats();
        stats.setLevelDirectly(PlayerStats.MAX_LEVEL - 1);
        int massiveAmount = requiredAt(PlayerStats.MAX_LEVEL - 1) * 1000;

        boolean leveledUp = stats.addCharacterXp(massiveAmount);

        assertTrue(leveledUp);
        assertEquals(PlayerStats.MAX_LEVEL, stats.getLevel(), "must stop exactly at the cap, never exceed it");
        assertEquals(0, stats.getCharacterXp(),
                "existing max-level policy preserved: no XP is retained once capped, even from overflow mid-loop");
    }

    @Test
    void grantingXpAlreadyAtMaxLevelDoesNothingAndNeverLoops() {
        PlayerStats stats = new PlayerStats();
        stats.setLevelDirectly(PlayerStats.MAX_LEVEL);

        boolean leveledUp = stats.addCharacterXp(1_000_000);

        assertFalse(leveledUp);
        assertEquals(PlayerStats.MAX_LEVEL, stats.getLevel());
        assertEquals(0, stats.getCharacterXp());
    }

    @Test
    void splittingOneGrantAcrossTwoCallsMatchesOneCombinedCallWithNoDuplicateLevelGain() {
        PlayerStats split = new PlayerStats();
        split.addCharacterXp(60);
        split.addCharacterXp(60); // 60 + 60 = 120, same as the single-call bug scenario

        PlayerStats combined = new PlayerStats();
        combined.addCharacterXp(120);

        assertEquals(combined.getLevel(), split.getLevel());
        assertEquals(combined.getCharacterXp(), split.getCharacterXp());
        assertEquals(2, split.getLevel());
        assertEquals(20, split.getCharacterXp());
    }

    @Test
    void attributePointsAreAwardedOncePerLevelGainedNotOncePerGrant() {
        PlayerStats stats = new PlayerStats();
        int before = stats.getUnspentAttributePoints();
        int total = requiredAt(1) + requiredAt(2) + requiredAt(3) + 10; // 3 level-ups in one grant

        stats.addCharacterXp(total);

        assertEquals(before + 3 * PlayerStats.ATTRIBUTE_POINTS_PER_LEVEL, stats.getUnspentAttributePoints());
    }

    @Test
    void xpRemainderSurvivesTheRealSaveLoadRoundTrip() {
        // new PlayerStatsComponent(null) is safe: writeData/readData never dereference the
        // player field, matching the same nullable-player pattern already established in
        // PlayerChargesRageCharacterizationTest for this exact kind of persistence test.
        PlayerStatsComponent saved = new PlayerStatsComponent(null);
        saved.getStats().addCharacterXp(requiredAt(1) + 20); // -> Level 2, 20 XP remainder

        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        saved.writeData(out);

        PlayerStatsComponent restored = new PlayerStatsComponent(null);
        restored.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), out.buildResult()));

        assertEquals(2, restored.getStats().getLevel());
        assertEquals(20, restored.getStats().getCharacterXp());
    }

    private static HolderLookup.Provider emptyRegistries() {
        return HolderLookup.Provider.create(Stream.of());
    }
}

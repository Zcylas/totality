package zcylas.totality.api.rpg.skills.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks which mastery ranks the player has unlocked.
 * Stored in PlayerMasteriesComponent, persists on death, synced to client.
 *
 * Key: masteryId, Value: current unlocked rank (0 = locked, 1 = rank 1, 2 = rank 2 etc.)
 * Mastery points are tracked here as a separate pool from attribute points.
 */
public class PlayerMasteries {

    private final Map<String, Integer> unlockedRanks = new HashMap<>();
    private int masteryPoints = 0;

    public PlayerMasteries() {}

    // ── Mastery Points ────────────────────────────────────────────────────────

    public int getMasteryPoints() { return masteryPoints; }

    public void addMasteryPoints(int amount) {
        masteryPoints = Math.max(0, masteryPoints + amount);
    }

    public void setMasteryPointsDirectly(int points) {
        masteryPoints = Math.max(0, points);
    }

    // ── Unlock ────────────────────────────────────────────────────────────────

    /**
     * Returns the current unlocked rank for a mastery (0 = locked).
     */
    public int getUnlockedRank(String masteryId) {
        return unlockedRanks.getOrDefault(masteryId, 0);
    }

    public boolean isUnlocked(String masteryId) {
        return getUnlockedRank(masteryId) > 0;
    }

    public boolean isFullyUnlocked(Mastery mastery) {
        return getUnlockedRank(mastery.getId()) >= mastery.getRankCount();
    }

    /**
     * Attempts to unlock the next rank of a mastery.
     * Returns true if successful.
     * Validates: has mastery points, meets skill level requirement, has previous rank.
     */
    public boolean unlockNextRank(Mastery mastery, int currentSkillLevel) {
        int currentRank = getUnlockedRank(mastery.getId());
        int nextRank = currentRank + 1;

        // Already fully unlocked
        if (nextRank > mastery.getRankCount()) return false;

        // Not enough mastery points
        if (masteryPoints <= 0) return false;

        // Doesn't meet skill level requirement
        int required = mastery.getRequiredLevelForRank(nextRank);
        if (currentSkillLevel < required) return false;

        // Unlock it
        unlockedRanks.put(mastery.getId(), nextRank);
        masteryPoints--;
        return true;
    }

    /**
     * Directly set a mastery rank — used for serialization only.
     */
    public void setRankDirectly(String masteryId, int rank) {
        if (rank <= 0) unlockedRanks.remove(masteryId);
        else unlockedRanks.put(masteryId, rank);
    }

    public Map<String, Integer> getAllUnlockedRanks() {
        return Map.copyOf(unlockedRanks);
    }

    public void clearRanks() {
        unlockedRanks.clear();
    }
}
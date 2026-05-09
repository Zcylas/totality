package zcylas.totality.api.rpg.skills.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache of the player's mastery unlock data.
 * Populated when a sync packet is received from the server.
 * Used by the SkillsMenuScreen.
 */
public final class ClientMasteriesManager {

    private static int masteryPoints = 0;
    private static final Map<String, Integer> unlockedRanks = new HashMap<>();

    private ClientMasteriesManager() {}

    public static void apply(PlayerMasteries masteries) {
        masteryPoints = masteries.getMasteryPoints();
        unlockedRanks.clear();
        unlockedRanks.putAll(masteries.getAllUnlockedRanks());
    }

    public static int getMasteryPoints() { return masteryPoints; }

    public static int getUnlockedRank(String masteryId) {
        return unlockedRanks.getOrDefault(masteryId, 0);
    }

    public static boolean isUnlocked(String masteryId) {
        return getUnlockedRank(masteryId) > 0;
    }
}
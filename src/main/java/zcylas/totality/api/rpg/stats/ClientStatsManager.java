package zcylas.totality.api.rpg.stats;

/**
 * Client-side cache of the local player's RPG stats.
 * Populated when a sync packet is received from the server.
 * Used by HUD renderers and GUI screens.
 */
public final class ClientStatsManager {

    private static int level = 1;
    private static int characterXp = 0;
    private static int xpRequired = 100;
    private static int unspentPoints = 0;
    private static final int[] scores = new int[AbilityScore.values().length];

    static {
        // Initialize all scores to base value
        for (int i = 0; i < scores.length; i++) {
            scores[i] = PlayerStats.BASE_SCORE;
        }
    }

    private ClientStatsManager() {}

    /**
     * Called from applySyncPacket to update the client cache.
     */
    public static void apply(PlayerStats stats) {
        level = stats.getLevel();
        characterXp = stats.getCharacterXp();
        xpRequired = stats.getXpRequiredForNextLevel();
        unspentPoints = stats.getUnspentAttributePoints();
        AbilityScore[] scoreValues = AbilityScore.values();
        for (int i = 0; i < scoreValues.length; i++) {
            scores[i] = stats.getScore(scoreValues[i]);
        }
    }

    public static int getLevel() { return level; }
    public static int getCharacterXp() { return characterXp; }
    public static int getXpRequired() { return xpRequired; }
    public static int getUnspentPoints() { return unspentPoints; }

    public static int getScore(AbilityScore score) {
        return scores[score.ordinal()];
    }

    public static int getModifier(AbilityScore score) {
        return AbilityScore.getModifier(getScore(score));
    }
}
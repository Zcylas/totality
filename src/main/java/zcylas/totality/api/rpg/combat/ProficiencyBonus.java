package zcylas.totality.api.rpg.combat;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatsComponents;

/**
 * Proficiency bonus lookup for Totality's 25-class-level system.
 * Increases every 4 class levels, from +2 at level 1 to +7 at level 21+.
 *
 * To get a player's class level, use the Class API (PlayerClass.getLevelFor(player)).
 */
public final class ProficiencyBonus {

    private ProficiencyBonus() {}

    public static int forClassLevel(int classLevel) {
        return switch (Math.clamp(classLevel, 1, Integer.MAX_VALUE)) {
            case 1, 2, 3, 4     -> 2;
            case 5, 6, 7, 8     -> 3;
            case 9, 10, 11, 12  -> 4;
            case 13, 14, 15, 16 -> 5;
            case 17, 18, 19, 20 -> 6;
            default             -> 7; // 21–25
        };
    }

    /**
     * Convenience: derives class level from player level using the 4:1 ratio.
     *
     * TODO: replace with PlayerClass.getLevelFor(player) once the Class API exists.
     */
    public static int forPlayer(ServerPlayer player) {
        PlayerStats stats = StatsComponents.getStats(player);
        int playerLevel   = stats != null ? stats.getLevel() : 1;
        int classLevel    = Math.clamp((playerLevel - 1) / 4 + 1, 1, 25);
        return forClassLevel(classLevel);
    }
}
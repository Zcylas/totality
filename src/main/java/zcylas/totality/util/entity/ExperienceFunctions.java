package zcylas.totality.util.entity;

import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Utilities for reading and modifying a player's total vanilla XP as a flat
 * integer, bypassing the awkward level + progress split in {@link Player}.
 *
 * <p>Ported from Collective (Serilum).</p>
 *
 * <p>Useful for skill levelling costs, XP-consuming abilities, ritual costs, etc.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * // Spend 50 XP to level up One-Handed:
 * if (ExperienceFunctions.canConsumeXp(player, 50)) {
 *     ExperienceFunctions.consumeXp(player, 50);
 *     // award skill level...
 * }
 * }</pre>
 */
public final class ExperienceFunctions {

    private ExperienceFunctions() {}

    // -----------------------------------------------------------------------
    // Check
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} if the player currently has at least {@code xp}
     * experience points (creative players always return {@code true}).
     */
    public static boolean canConsumeXp(Player player, int xp) {
        if (player.isCreative()) return true;
        return xp <= 0 || getPlayerXP(player) >= xp;
    }

    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------

    /**
     * Get the player's total accumulated XP as a single integer
     * (level XP + partial-level progress).
     */
    public static int getPlayerXP(Player player) {
        return (int) (getExperienceForLevel(player.experienceLevel)
                + player.experienceProgress * player.getXpNeededForNextLevel());
    }

    // -----------------------------------------------------------------------
    // Modify
    // -----------------------------------------------------------------------

    /**
     * Subtract {@code xp} from the player's total XP.
     * If the player doesn't have enough XP, nothing happens.
     * Triggers the XP pickup animation/sound on the server.
     */
    public static void consumeXp(Player player, int xp) {
        if (xp <= 0) return;
        int current = getPlayerXP(player);
        if (current < xp) return;

        addPlayerXP(player, -xp);

        if (player instanceof ServerPlayer sp) {
            sp.connection.send(new ClientboundEntityEventPacket(player, (byte) 9));
        }
    }

    /**
     * Add (or subtract) a flat amount of XP to the player,
     * updating level and progress bar to match.
     *
     * @param amount may be negative to remove XP
     */
    public static void addPlayerXP(Player player, int amount) {
        int experience = Math.max(0, getPlayerXP(player) + amount);
        player.totalExperience    = experience;
        player.experienceLevel    = getLevelForExperience(experience);
        int expForLevel           = getExperienceForLevel(player.experienceLevel);
        player.experienceProgress = (float) (experience - expForLevel)
                                  / (float) player.getXpNeededForNextLevel();
    }

    // -----------------------------------------------------------------------
    // Level math (mirrors vanilla formulas)
    // -----------------------------------------------------------------------

    /**
     * Returns how many total XP points are needed to reach exactly {@code level}
     * from 0, using vanilla's piecewise formula.
     */
    public static int getExperienceForLevel(int level) {
        if (level == 0)    return 0;
        if (level <= 15)   return sum(level, 7, 2);
        if (level <= 30)   return 315 + sum(level - 15, 37, 5);
        return 1395 + sum(level - 30, 112, 9);
    }

    /**
     * Returns the level a player is at given {@code totalXp} accumulated XP.
     */
    public static int getLevelForExperience(int totalXp) {
        int level = 0;
        while (true) {
            int toNext = xpBarCap(level);
            if (totalXp < toNext) return level;
            level++;
            totalXp -= toNext;
        }
    }

    /**
     * XP required to advance from {@code level} to {@code level + 1}
     * (vanilla's {@code getXpNeededForNextLevel} equivalent, as a static helper).
     */
    public static int xpBarCap(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37  + (level - 15) * 5;
        return 7 + level * 2;
    }

    // arithmetic series: n*(2*a0 + (n-1)*d) / 2
    private static int sum(int n, int a0, int d) {
        return n * (2 * a0 + (n - 1) * d) / 2;
    }
}

package zcylas.totality.api.rpg.check;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.combat.RollModifierRegistry;
import zcylas.totality.api.rpg.stats.StatsComponents;

/**
 * Server-side resolver for ability checks.
 * Always call this on the server — never on the client.
 *
 * Usage:
 *   AbilityCheck check = new AbilityCheck(id("read_tome"), AbilityScore.INT, 15);
 *   AbilityCheckResult result = AbilityCheckResolver.roll(player, check);
 *   if (result.success()) { ... }
 */
public final class AbilityCheckResolver {

    public enum RollMode { NORMAL, ADVANTAGE, DISADVANTAGE }

    public static AbilityCheckResult roll(ServerPlayer player, AbilityCheck check) {
        RollMode mode = RollModifierRegistry.resolveCheck(player, check.score(), RollMode.NORMAL);
        return roll(player, check, mode);
    }

    public static AbilityCheckResult roll(ServerPlayer player, AbilityCheck check, RollMode mode) {
        int roll = switch (mode) {
            case ADVANTAGE    -> Math.max(d20(player), d20(player));
            case DISADVANTAGE -> Math.min(d20(player), d20(player));
            case NORMAL       -> d20(player);
        };
        int modifier = StatsComponents.getStats(player).getModifier(check.score());
        int proficiency = check.proficient() ? getProficiencyBonus(player) : 0;
        int total = roll + modifier + proficiency;
        return new AbilityCheckResult(roll, modifier, total, total >= check.dc());
    }

    private static int d20(ServerPlayer player) {
        return player.getRandom().nextInt(20) + 1;
    }

    private static int getProficiencyBonus(ServerPlayer player) {
        int level = StatsComponents.getStats(player).getLevel();
        return 2 + (level - 1) / 4; // +2 at 1, +3 at 5, +4 at 9, etc.
    }

    private AbilityCheckResolver() {}
}
package zcylas.totality.api.mining;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatsComponents;

/**
 * Turns a player into the three independent mining axes for one target block.
 *
 * <pre>
 *  Source   : PLAYER_TOOL if the held item has a TOOL component, else BARE_HANDS   (MiningTier.isToolSource)
 *  Tier     : tool -> the tool's own Tier only;  hands -> DEX-derived Tier only.   (never max(tool, hand))
 *  Power    : max(min, base + STR modifier) x (1 + force)
 *             base = intrinsic tool speed x 6   (tool)   |   1   (hands)
 *  Cadence  : ratio of the vanilla break-speed stack to the intrinsic speed        (cadenceRatio)
 * </pre>
 * {@code Player.getDestroySpeed} is used ONLY by {@link #cadenceRatio}.
 */
public final class PlayerMiningPower {

    /**
     * @param damage           structural damage of this impact (already includes the Power multiplier)
     * @param damageMultiplier the Power multiplier that was applied (1 for a normal swing, up to 2)
     * @param forceLoad        physical load put through the tool (stress only; independent of damage)
     */
    public record Result(float damage, int tier, MiningSource.Kind kind, float damageMultiplier, float forceLoad) {}

    private PlayerMiningPower() {}

    private static PlayerStats stats(ServerPlayer player) { return StatsComponents.getStats(player); }

    public static int strength(ServerPlayer player) { return stats(player).getScore(AbilityScore.STR); }

    /** @param force requested force 0..1 (0 = a normal, safe swing) */
    public static Result compute(ServerPlayer player, BlockState state, float force) {
        PlayerStats stats = stats(player);
        ItemStack held = player.getMainHandItem();
        boolean tool = MiningTier.isToolSource(held);

        float base = tool
                ? MiningTuning.toolBaseDamage(MiningTier.intrinsicSpeed(held, state))
                : MiningTuning.BARE_HAND_BASE_DAMAGE;
        int tier = tool
                ? MiningTier.ofTool(held)
                : MiningTuning.bareHandTier(stats.getScore(AbilityScore.DEX));

        float multiplier = MiningTuning.powerDamageMultiplier(force);
        float damage = MiningTuning.impactDamage(base, stats.getModifier(AbilityScore.STR)) * multiplier;
        float load = MiningTuning.forceLoad(force, stats.getScore(AbilityScore.STR));
        return new Result(damage, tier, tool ? MiningSource.Kind.PLAYER_TOOL : MiningSource.Kind.BARE_HANDS, multiplier, load);
    }

    /**
     * Cadence ratio for normal swings: the player's full vanilla break speed divided by the held item's
     * intrinsic speed. It therefore inherits exactly what {@code Player.getDestroySpeed} adds on top of the
     * tool: MINING_EFFICIENCY attribute (only when intrinsic speed &gt; 1), Haste, Mining Fatigue,
     * BLOCK_BREAK_SPEED attribute, SUBMERGED_MINING_SPEED (eyes in water) and the airborne /5.
     * Clamped by {@link MiningTuning#clampCadenceRatio}.
     */
    public static float cadenceRatio(ServerPlayer player, BlockState state) {
        float intrinsic = MiningTier.intrinsicSpeed(player.getMainHandItem(), state);
        if (intrinsic <= 0f) return 1f;
        return MiningTuning.clampCadenceRatio(player.getDestroySpeed(state) / intrinsic);
    }
}

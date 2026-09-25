package zcylas.totality.api.mining;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatsComponents;
import zcylas.totality.init.ModEnchantments;

/**
 * Turns a player into the three independent mining axes for one target block.
 *
 * <pre>
 *  Source   : PLAYER_TOOL if the held item has a TOOL component, else BARE_HANDS   (MiningTier.isToolSource)
 *  Tier     : the tool's own Tier only (never max(tool, hand)); hands use DEX-derived Tier. For a
 *             PROFILED tool this is the authored MATERIAL Tier from {@link ResolvedMiningSource}
 *             (identical for a Pickaxe and an Axe of the same material — see
 *             {@link MiningSourceProfile}); non-profiled (specialized) tools use
 *             {@link MiningTier#specializedTier}. Tier is never used to encode Pickaxe-vs-Axe suitability —
 *             that is Target Effectiveness's job, entirely separate (see Damage/Cadence below).
 *  Damage   : PROFILED tool -> authored Mining Damage + Impact (+ Power zone STR bonus), THEN
 *             scaled by {@link TargetEffectiveness} (1.00x matching / 0.10x wrong tool, tightened
 *             from 0.25x in the playtest-correction pass) as the final step — never before
 *             Impact/Power STR resolve, never encoded into Tier.
 *             non-profiled tool / hands (legacy, UNCHANGED) -> max(min, base + STR modifier), base =
 *             intrinsic tool speed x 6 (tool) | 1 (hands); no target effectiveness applied.
 *  Cadence  : profiled tools -> authored Mining Speed (+Efficiency/+Haste/cap), see
 *             {@link #effectiveMiningSpeed}, THEN scaled by {@link TargetEffectiveness}'s speed
 *             multiplier AFTER the cap. Non-profiled tools/hands (legacy, UNCHANGED) -> ratio of the
 *             vanilla break-speed stack to the intrinsic speed (cadenceRatio), fed by
 *             {@code Player.getDestroySpeed} exactly as before; no target effectiveness applied.
 * </pre>
 */
public final class PlayerMiningPower {

    /**
     * @param damage           structural damage of this impact (already includes the Power contribution)
     * @param damageMultiplier the legacy Power multiplier that was applied (1 for a normal swing, up to 2);
     *                         always 1 for a profiled tool, whose Power contribution is additive, not multiplicative.
     * @param forceLoad        physical load put through the tool (legacy stress input; independent of damage)
     * @param band             this impact's Power presentation band (0 for a normal, non-Power swing)
     * @param profiled         true when {@code damage} came from a profiled tool's authored Mining Damage
     * @param strModifier      the player's STR modifier (for the profiled zone-based extra-wear calc)
     */
    public record Result(float damage, int tier, MiningSource.Kind kind, float damageMultiplier, float forceLoad,
                         int band, boolean profiled, int strModifier) {}

    private PlayerMiningPower() {}

    private static PlayerStats stats(ServerPlayer player) { return StatsComponents.getStats(player); }

    public static int strength(ServerPlayer player) { return stats(player).getScore(AbilityScore.STR); }

    /**
     * Convenience overload for callers with no explicit Power flag of their own (tests, tools):
     * infers Power from {@code force > 0}. The live strike path never uses this — a legitimate
     * Power release can land at force exactly 0 (the oscillating meter can read 0) and must still
     * be treated as Power Mining, not silently downgraded to an ordinary hit — see the 4-arg
     * overload and {@link PlayerMiningManager#strike}.
     *
     * @param force requested force 0..1 (0 = a normal, safe swing)
     */
    public static Result compute(ServerPlayer player, BlockState state, float force) {
        return compute(player, state, force > 0f, force);
    }

    /**
     * @param power explicit Power Mining flag, passed by the caller rather than inferred from
     *              {@code force > 0} (force can legitimately be exactly 0 for a Power swing)
     * @param force requested force 0..1 (0 = a normal, safe swing, or a zero-force Power release)
     */
    public static Result compute(ServerPlayer player, BlockState state, boolean power, float force) {
        PlayerStats stats = stats(player);
        ItemStack held = player.getMainHandItem();
        boolean tool = MiningTier.isToolSource(held);
        int strModifier = stats.getModifier(AbilityScore.STR);

        var profile = tool ? MiningSourceProfile.resolve(held) : java.util.Optional.<MiningSourceProfile.Entry>empty();
        if (profile.isPresent()) {
            ResolvedMiningSource source = ResolvedMiningSource.of(held, state);
            int impactLevel = EnchantLevel.of(held, ModEnchantments.IMPACT);
            float normalDamage = MiningDamageCalculator.compute(source, impactLevel).value();
            int band = power ? MiningTuning.presentationBand(force, false) : MiningTuning.BAND_DEFAULT;
            // Power's STR contribution resolves BEFORE target effectiveness (§4 of the wrong-tool
            // fix pass): pre-effectiveness damage = base + Impact (+ Power zone STR bonus), THEN
            // scaled by the target multiplier as the final step.
            float preEffectivenessDamage = power
                    ? normalDamage + MiningTuning.powerZoneDamageBonus(band, strModifier)
                    : normalDamage;
            float damage = Math.max(MiningTuning.MIN_IMPACT_DAMAGE,
                    preEffectivenessDamage * TargetEffectiveness.resolve(held, state).damageMultiplier());
            float load = MiningTuning.forceLoad(force, stats.getScore(AbilityScore.STR));
            return new Result(damage, source.tier(), MiningSource.Kind.PLAYER_TOOL, 1f, load, band, true, strModifier);
        }

        // Legacy path (bare hands, and specialized non-profiled tools: swords, shears, modded) — UNCHANGED from V1,
        // except that a specialized tool's Tier no longer blocks the blocks its own rules are made for (Pass 2).
        float base = tool
                ? MiningTuning.toolBaseDamage(MiningTier.intrinsicSpeed(held, state))
                : MiningTuning.BARE_HAND_BASE_DAMAGE;
        int tier = tool
                ? MiningTier.specializedTier(held, state)
                : MiningTuning.bareHandTier(stats.getScore(AbilityScore.DEX));

        float multiplier = MiningTuning.powerDamageMultiplier(force);
        float damage = MiningTuning.impactDamage(base, strModifier) * multiplier;
        float load = MiningTuning.forceLoad(force, stats.getScore(AbilityScore.STR));
        boolean overloaded = power && tool && load > MiningTuning.forceTolerance(held);
        int band = power ? MiningTuning.presentationBand(force, overloaded) : MiningTuning.BAND_DEFAULT;
        return new Result(damage, tier, tool ? MiningSource.Kind.PLAYER_TOOL : MiningSource.Kind.BARE_HANDS,
                multiplier, load, band, false, strModifier);
    }

    /** Active Haste level (amplifier + 1), or null when Haste isn't active. */
    private static Integer hasteLevel(ServerPlayer player) {
        if (!player.hasEffect(MobEffects.HASTE)) return null;
        return player.getEffect(MobEffects.HASTE).getAmplifier() + 1;
    }

    /**
     * Legitimate cadence modifiers OTHER than Efficiency/Haste, replicated directly from the same
     * MobEffect/Attribute vanilla {@code Player.getDestroySpeed} reads — never by calling that
     * method itself, so Efficiency/Haste (now applied by {@link MiningSpeedCalculator}) can never
     * be double-counted.
     */
    private static float environmentalCadenceMultiplier(ServerPlayer player) {
        float mult = 1f;
        if (player.hasEffect(MobEffects.MINING_FATIGUE)) {
            mult *= switch (player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 8.1e-4f;
            };
        }
        mult *= (float) player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
        if (player.isEyeInFluid(FluidTags.WATER)) {
            mult *= (float) player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
        }
        if (!player.onGround()) mult /= 5f;
        return mult;
    }

    /**
     * Usable (capped) Mining Speed for a PROFILED tool, feeding the live cadence path. The target
     * effectiveness multiplier (§2-3 of the wrong-tool fix pass) is applied AFTER the speed cap —
     * "final mining cadence = effective authored speed x target effectiveness" — so a wrong-tool
     * setup can never retain the same capped rate a matching tool would.
     */
    public static float effectiveMiningSpeed(ServerPlayer player, ItemStack tool, BlockState state, ResolvedMiningSource source) {
        int efficiency = EnchantLevel.of(tool, Enchantments.EFFICIENCY);
        Integer haste = hasteLevel(player);
        float environmental = environmentalCadenceMultiplier(player);
        float usable = MiningSpeedCalculator.compute(source, efficiency, haste, environmental).value();
        return usable * TargetEffectiveness.resolve(tool, state).speedMultiplier();
    }

    /**
     * Cadence ratio for normal swings on a NON-PROFILED tool/bare hands: the player's full vanilla
     * break speed divided by the held item's intrinsic speed. It therefore inherits exactly what
     * {@code Player.getDestroySpeed} adds on top of the tool: MINING_EFFICIENCY attribute (only
     * when intrinsic speed &gt; 1), Haste, Mining Fatigue, BLOCK_BREAK_SPEED attribute,
     * SUBMERGED_MINING_SPEED (eyes in water) and the airborne /5. Clamped by
     * {@link MiningTuning#clampCadenceRatio}. Profiled tools never use this — see
     * {@link #effectiveMiningSpeed}.
     */
    public static float cadenceRatio(ServerPlayer player, BlockState state) {
        float intrinsic = MiningTier.intrinsicSpeed(player.getMainHandItem(), state);
        if (intrinsic <= 0f) return 1f;
        return MiningTuning.clampCadenceRatio(player.getDestroySpeed(state) / intrinsic);
    }
}

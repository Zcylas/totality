package zcylas.totality.api.mining;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The four primary stats of a conventional-tool mining source, resolved from wherever each one's
 * existing authority already lives — nothing here is a second source of truth:
 * <ul>
 *   <li>Mining Damage / Mining Speed / Mining Tier — {@link MiningSourceProfile} when the tool has
 *       an authored row, else the pre-V2 legacy formulas ({@link MiningTuning#toolBaseDamage} /
 *       {@link MiningTier#ofTool}, for swords, shears or any other non-profiled tool).</li>
 *   <li>Force Tolerance — the existing, untouched {@link MiningTuning#forceTolerance}.</li>
 * </ul>
 * Gameplay ({@link PlayerMiningPower}) and the tooltip ({@code MiningToolContributor}) both read
 * through this one view so they can never disagree about where a stat comes from — including Tier:
 * a profiled Axe's Tier comes from the SAME authored row its Pickaxe counterpart uses (see
 * {@link MiningSourceProfile}), never re-derived here, so there is exactly one Tier value per
 * profiled source, never two competing ones.
 *
 * <p><b>Mining Tier vs Target Effectiveness — two deliberately separate concepts (Block Breaking
 * V2 review-fix, wrong-tool-Tier pass).</b> Mining Tier is a MATERIAL/capability gate ("is this
 * source materially capable of working a block of this required tier at all") — identical for a
 * Pickaxe and an Axe of the same material. Whether the tool is the block's *preferred* tool
 * (Pickaxe vs {@code #minecraft:mineable/pickaxe}, Axe vs {@code #minecraft:mineable/axe}) is an
 * entirely different question, answered by {@link TargetEffectiveness} and applied only inside
 * {@link PlayerMiningPower} — never here, and Mining Tier is never reused to encode it. {@code
 * state} is accepted here only for the legacy fallback formulas (non-profiled tools/hands); a
 * profiled tool's Damage/Speed/Tier never depend on it — they stay target-independent at THIS
 * layer (which is exactly why the tooltip, which reads straight from this record with a
 * placeholder block, is unaffected by target effectiveness, per the brief). Target effectiveness
 * is a real, implemented penalty applied one layer up, in {@link PlayerMiningPower} — a Pickaxe on
 * Wood, or an Axe on Stone, is scaled by {@link TargetEffectiveness#WRONG_TOOL} there, not here.
 *
 * @param authored true when {@code miningDamage}/{@code miningSpeed}/{@code tier} came from
 *                 {@link MiningSourceProfile} (Impact/Efficiency/Haste/target-effectiveness apply);
 *                 false means the legacy fallback (swords, shears, non-tool).
 */
public record ResolvedMiningSource(float miningDamage, float miningSpeed, int tier, float forceTolerance, boolean authored) {

    public static ResolvedMiningSource of(ItemStack tool, BlockState state) {
        var profile = MiningSourceProfile.resolve(tool);
        float damage = profile.map(MiningSourceProfile.Entry::miningDamage)
                .orElseGet(() -> MiningTuning.toolBaseDamage(MiningTier.intrinsicSpeed(tool, state)));
        float speed = profile.map(MiningSourceProfile.Entry::miningSpeed)
                .orElseGet(() -> legacyBaselineSpeed(tool));
        int tier = profile.map(MiningSourceProfile.Entry::tier)
                .orElseGet(() -> MiningTier.ofTool(tool));
        return new ResolvedMiningSource(damage, speed, tier, MiningTuning.forceTolerance(tool), profile.isPresent());
    }

    /** A non-profiled tool's own unbuffed hit rate (cadence ratio 1) — not consulted by the live cadence
     *  path (which keeps using {@link PlayerMiningPower#cadenceRatio} for these tools) but keeps this
     *  view internally consistent rather than leaving the field undefined. */
    private static float legacyBaselineSpeed(ItemStack tool) {
        int duration = tool.getSwingAnimation().duration();
        return 20f / MiningTuning.cycleTicks(duration, 1f);
    }
}

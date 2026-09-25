package zcylas.totality.client.tooltip.contributor;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.mining.EnchantLevel;
import zcylas.totality.api.mining.MiningDamageCalculator;
import zcylas.totality.api.mining.MiningSourceProfile;
import zcylas.totality.api.mining.MiningSpeedCalculator;
import zcylas.totality.api.mining.ResolvedMiningSource;
import zcylas.totality.api.mining.StatBreakdown;
import zcylas.totality.client.tooltip.group.TooltipGroup;
import zcylas.totality.client.tooltip.group.TooltipGroups;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.section.TooltipSection;
import zcylas.totality.init.ModEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Effective Mining Damage/Speed/Tier for a PROFILED conventional
 * tool (Block Breaking V2 §9-10) — reads through the exact same {@link ResolvedMiningSource}/
 * {@link MiningDamageCalculator}/{@link MiningSpeedCalculator} gameplay uses, so the tooltip can
 * never disagree with what a swing actually does. Fires only when {@link MiningSourceProfile#resolve}
 * is present (Pickaxes, Axes, Shovels and Hoes of every conventional material, Gold included): bare
 * hands, swords, shears and any other non-profiled tool get no mining rows.
 *
 * <p><b>Environment note (tracked, not decided this pass):</b> the displayed Mining Speed includes
 * authored base + Efficiency + Haste + the 10/s cap, but is always computed with
 * {@code environmentalMultiplier = 1f} — it does NOT reflect Mining Fatigue, the
 * {@code BLOCK_BREAK_SPEED} attribute, submerged mining speed, or the airborne penalty, even when
 * one of those is currently active on the hovering player. The tooltip is therefore not always
 * the exact live cadence while an environmental modifier applies. Whether the normal tooltip
 * should (A) show the fully live effective cadence, or (B) show tool/buff Mining Speed
 * independent of temporary environment, is a decision for after the visual/playtest pass — do not
 * assume/claim (A) until that decision is made.
 */
public final class MiningToolContributor implements TooltipContributor {

    private static final int DAMAGE_COLOR = 0xFFE0A060;
    private static final int SPEED_COLOR = 0xFF66BB6A;
    private static final int TIER_COLOR = 0xFF42A5F5;

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        ItemStack stack = ctx.stack();
        if (MiningSourceProfile.resolve(stack).isEmpty()) return List.of();

        // Damage/Speed/Tier/Force Tolerance never depend on the target block for a PROFILED tool
        // (that's the point of the authored stat) — this placeholder state is never actually read.
        ResolvedMiningSource source = ResolvedMiningSource.of(stack, Blocks.STONE.defaultBlockState());
        boolean details = ctx.disclosure().includes(TooltipDisclosureLevel.DETAILS);

        StatBreakdown damage = MiningDamageCalculator.compute(source, EnchantLevel.of(stack, ModEnchantments.IMPACT));
        StatBreakdown speed = MiningSpeedCalculator.compute(source,
                EnchantLevel.of(stack, Enchantments.EFFICIENCY), activeHasteLevel(ctx), 1f);

        List<TooltipSection> sections = new ArrayList<>();

        sections.add(new TooltipSection.IconStatRow(new TooltipSection.StatIcon.Item(new ItemStack(Items.IRON_PICKAXE)),
                "Mining Damage", MiningStatFormat.integer(damage.value()), DAMAGE_COLOR));
        if (details) sections.add(provenance(damage, ""));

        sections.add(new TooltipSection.IconStatRow(new TooltipSection.StatIcon.Item(new ItemStack(Items.SUGAR)),
                "Mining Speed", MiningStatFormat.decimal(speed.value()) + "/s", SPEED_COLOR));
        if (details) sections.add(provenance(speed, "/s"));

        sections.add(new TooltipSection.IconStatRow(new TooltipSection.StatIcon.Effect(MobEffects.JUMP_BOOST),
                "Mining Tier", String.valueOf(source.tier()), TIER_COLOR));
        if (details) {
            String material = MiningSourceProfile.displayName(stack);
            sections.add(new TooltipSection.ProvenanceGroup(List.of(new TooltipSection.ProvenanceLine(
                    material != null ? material : "Tool", "Tier " + source.tier()))));
        }

        // Force Tolerance (ResolvedMiningSource.forceTolerance) is deliberately NOT shown (Block Breaking V2 Pass 2):
        // profiled tools take Power Mining wear from the four zone bands, so the number has no mechanical meaning for
        // them and displaying it would mislead. The field is preserved for a future Force Tolerance design.

        return sections;
    }

    /**
     * @param unit appended to every BASE/ADD/MIN value ({@code "/s"} for Mining Speed, {@code ""}
     *             for Mining Damage, which has no unit) — MULTIPLY (Haste) never takes a unit,
     *             it's a dimensionless factor (§11 fix pass: Speed provenance now carries units).
     */
    private static TooltipSection.ProvenanceGroup provenance(StatBreakdown breakdown, String unit) {
        List<TooltipSection.ProvenanceLine> lines = new ArrayList<>();
        for (StatBreakdown.Contribution c : breakdown.contributions()) {
            String value = switch (c.op()) {
                case BASE -> trimmedAmount(c.amount()) + unit;
                case ADD -> (c.amount() >= 0 ? "+" : "") + trimmedAmount(c.amount()) + unit;
                case MULTIPLY -> "x" + MiningStatFormat.decimal(c.amount());
                case MIN -> MiningStatFormat.decimal(c.amount()) + unit;
            };
            lines.add(new TooltipSection.ProvenanceLine(c.label(), value));
        }
        return new TooltipSection.ProvenanceGroup(lines);
    }

    private static String trimmedAmount(float v) {
        return v == Math.round(v) ? MiningStatFormat.integer(v) : MiningStatFormat.decimal(v);
    }

    /** Active Haste level (amplifier + 1) of the hovering player, or null — never fabricated without a player (§10). */
    @Nullable
    private static Integer activeHasteLevel(TooltipContext ctx) {
        Player player = ctx.player();
        if (player == null || !player.hasEffect(MobEffects.HASTE)) return null;
        return player.getEffect(MobEffects.HASTE).getAmplifier() + 1;
    }

    @Override
    public Set<TooltipDisclosureLevel> availableDisclosureLevels(TooltipContext ctx) {
        return MiningSourceProfile.resolve(ctx.stack()).isPresent() ? Set.of(TooltipDisclosureLevel.DETAILS) : Set.of();
    }

    @Override
    public TooltipGroup bodyGroup(TooltipContext ctx) {
        return TooltipGroups.MINING;
    }
}

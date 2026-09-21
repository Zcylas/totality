package zcylas.totality.client.tooltip.contributor;

import zcylas.totality.api.magic.grimoire.GrimoireCaster;
import zcylas.totality.api.magic.grimoire.MagicComponents;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipVisibility;
import zcylas.totality.client.tooltip.section.TooltipSection;
import zcylas.totality.item.magic.GrimoireItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Migrated from {@code GrimoireItem}'s previous direct {@code TooltipExtension} implementation.
 * Reads only the narrow {@link GrimoireCaster#spellName()} accessor — never the underlying
 * {@code ArcaneFormula}/rune internals — so this contributor does not couple the Tooltip API to
 * the Spell API's implementation details. A future richer Spell API can enlarge what
 * {@code GrimoireCaster} exposes without this class needing to change.
 *
 * All three Grimoire tiers share this single contributor; tier is a constructor argument on
 * {@code GrimoireItem}, not a subclass or enum, so there is nothing to special-case per tier.
 *
 * Tier is an obvious physical fact (a thicker, more ornate tome) and stays
 * {@link TooltipVisibility#ALWAYS}. The selected-spell state is magical internal state — what's
 * actually prepared inside the tome — and is identified-only. {@code GrimoireItem} does not
 * implement {@code TotalityItem}, so {@code TooltipKnowledgeView.of(...)} always reports fully
 * identified for it today; this gating only takes effect once Grimoires (or their base class)
 * participate in identification.
 */
public final class GrimoireContributor implements TooltipContributor {

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        if (!(ctx.stack().getItem() instanceof GrimoireItem grimoire)) return List.of();

        List<TooltipSection> sections = new ArrayList<>();
        sections.add(new TooltipSection.StatRow(null, 0xFF9966FF, "Tier",
                toRoman(grimoire.getMaxTier()), 0xFF9966FF));

        GrimoireCaster caster = ctx.stack().getOrDefault(MagicComponents.GRIMOIRE_CASTER, GrimoireCaster.EMPTY);
        if (!caster.spellName().isEmpty()) {
            sections.add(new TooltipSection.StatRow(null, 0xFFAAAAFF, "Active", caster.spellName(), 0xFFAAAAFF)
                    .withVisibility(TooltipVisibility.WHEN_IDENTIFIED));
        } else {
            sections.add(new TooltipSection.StatRow(null, 0xFF666666, "Spell", "No spell active", 0xFF666666)
                    .withVisibility(TooltipVisibility.WHEN_IDENTIFIED));
        }

        return sections;
    }

    private static String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }
}

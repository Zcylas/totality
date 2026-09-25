package zcylas.totality.client.tooltip.contributor;

import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.TooltipVisibility;
import zcylas.totality.client.tooltip.group.TooltipGroup;
import zcylas.totality.client.tooltip.group.TooltipGroups;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The ENCHANTMENTS group: one row per enchantment actually on the stack (applied enchantments and an enchanted book's
 * stored enchantments), from Minecraft's own registry data via {@link TooltipEnchantments}, in vanilla's tooltip order.
 * Presentation only — nothing about acquiring, applying or managing enchantments (the future Enchanting API).
 *
 * <ul>
 *   <li>DEFAULT: the localized enchantment name and its actual level ({@code Sharpness  V}).</li>
 *   <li>SHIFT/DETAILS: the level against the enchantment's registered maximum ({@code III / V}) — registry data,
 *       nothing invented.</li>
 *   <li>Single-level enchantments (maximum level 1: {@code Mending}, {@code Silk Touch}, the curses) show the name
 *       alone in both views — no redundant {@code I} or {@code I / I}.</li>
 *   <li>Curses are marked in red (name and any level) rather than read as ordinary positive enchantments.</li>
 * </ul>
 * Rows are identified-only ({@link TooltipVisibility#WHEN_IDENTIFIED}), the same gate the raw vanilla lines they
 * replace carried, so an identification system that conceals them keeps concealing them. Enchantments never feed
 * ItemRarity or classification. {@link ExternalContentContributor} drops exactly the raw vanilla lines represented
 * here, so no enchantment is shown twice.
 */
public final class EnchantmentsContributor implements TooltipContributor {

    private static final int ENCHANTMENT_COLOR = 0xFFB9A8F0;
    private static final int CURSE_COLOR = 0xFFFF5555;

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        List<TooltipEnchantments.Entry> entries = TooltipEnchantments.of(ctx.stack(),
                ctx.level() == null ? null : ctx.level().registryAccess());
        if (entries.isEmpty()) return List.of();

        boolean details = ctx.disclosure().includes(TooltipDisclosureLevel.DETAILS);
        List<TooltipSection> sections = new ArrayList<>();
        for (TooltipEnchantments.Entry entry : entries) {
            TooltipSection.StatRow row = new TooltipSection.StatRow(null, 0, entry.enchantment().value().description().getString(),
                    value(entry.level(), entry.maxLevel(), details), entry.curse() ? CURSE_COLOR : ENCHANTMENT_COLOR)
                    .withVisibility(TooltipVisibility.WHEN_IDENTIFIED);
            sections.add(entry.curse() ? row.withLabelColor(CURSE_COLOR) : row);
        }
        return sections;
    }

    /**
     * The row's level text: nothing for a single-level enchantment at its only level (implied by its name, in both
     * DEFAULT and SHIFT — the same rule vanilla's {@code Enchantment.getFullname} applies), otherwise the actual level,
     * and under DETAILS the level against the registered maximum.
     */
    static String value(int level, int maxLevel, boolean details) {
        if (maxLevel == 1 && level == 1) return "";
        String text = TooltipEnchantments.levelText(level);
        return details ? text + " / " + TooltipEnchantments.levelText(maxLevel) : text;
    }

    /** SHIFT reveals each level against its registered maximum, so the SHIFT panel is offered whenever there is a row. */
    @Override
    public Set<TooltipDisclosureLevel> availableDisclosureLevels(TooltipContext ctx) {
        return TooltipEnchantments.of(ctx.stack(), ctx.level() == null ? null : ctx.level().registryAccess()).isEmpty()
                ? Set.of() : Set.of(TooltipDisclosureLevel.DETAILS);
    }

    @Override
    public TooltipGroup bodyGroup(TooltipContext ctx) {
        return TooltipGroups.ENCHANTMENTS;
    }
}

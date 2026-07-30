package zcylas.totality.client.tooltip.contributor;

import zcylas.totality.api.core.rpgutils.RpgDisplayUtils;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.section.TooltipSection;
import zcylas.totality.item.potion.dnd.HealingAmount;
import zcylas.totality.item.potion.dnd.HealingPotionItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Explicitly opts the standalone D&D Potion of Healing into Totality tooltip presentation.
 * Independent of Totality's Alchemy API by design — reads only {@link HealingPotionItem}'s own
 * registered {@link HealingAmount} formula and use duration, the same authoritative values
 * {@code finishUsingItem} actually rolls against, so the tooltip can never drift from what the
 * item really does. Presentation-scale HP figures go through {@link RpgDisplayUtils#toDisplayHp}
 * — the mod's single authoritative Health conversion — rather than a hardcoded multiplier.
 */
public final class HealingPotionContributor implements TooltipContributor {

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        if (!(ctx.stack().getItem() instanceof HealingPotionItem potion)) return List.of();

        HealingAmount amount = potion.getHealingAmount();
        int useDurationTicks = potion.getUseDurationTicks();
        double useSeconds = useDurationTicks / 20.0;

        List<TooltipSection> sections = new ArrayList<>();
        sections.add(new TooltipSection.StatRow(null, 0xFFE05A5A, "Restores",
                formula(amount) + " HP", 0xFFE05A5A));
        sections.add(new TooltipSection.StatRow(null, 0xFF888888, "Use Time",
                String.format(Locale.ROOT, "%.1f seconds", useSeconds), 0xFF888888));

        if (ctx.disclosure().atLeast(TooltipDisclosureLevel.DETAILS) && amount instanceof HealingAmount.DiceHealing dice) {
            int minVanilla = dice.diceCount() * 1 + dice.bonus();
            int maxVanilla = dice.diceCount() * dice.die().getSides() + dice.bonus();
            sections.add(new TooltipSection.StatRow(null, 0xFF888888, "Min Roll",
                    RpgDisplayUtils.toDisplayHp(minVanilla) + " HP", 0xFF888888));
            sections.add(new TooltipSection.StatRow(null, 0xFF888888, "Max Roll",
                    RpgDisplayUtils.toDisplayHp(maxVanilla) + " HP", 0xFF888888));
            sections.add(new TooltipSection.StatRow(null, 0xFF888888, "Use Duration",
                    useDurationTicks + " ticks (" + String.format(Locale.ROOT, "%.1f", useSeconds) + "s)", 0xFF888888));
        }

        return sections;
    }

    /**
     * Declared unconditionally for a dice-based formula, independent of the currently-selected
     * disclosure level — {@link #contribute} only emits the extra Min/Max/Duration rows when
     * already viewing Details, so inferring availability from its output at Default view would
     * always find nothing and never offer the "SHIFT: Details" footer hint.
     */
    @Override
    public Set<TooltipDisclosureLevel> availableDisclosureLevels(TooltipContext ctx) {
        if (!(ctx.stack().getItem() instanceof HealingPotionItem potion)) return Set.of();
        return potion.getHealingAmount() instanceof HealingAmount.DiceHealing
                ? Set.of(TooltipDisclosureLevel.DETAILS)
                : Set.of();
    }

    private static String formula(HealingAmount amount) {
        return switch (amount) {
            case HealingAmount.Fixed fixed -> String.valueOf(RpgDisplayUtils.toDisplayHp(fixed.amount()));
            case HealingAmount.DiceHealing dice -> dice.diceCount() + dice.die().getLabel()
                    + (dice.bonus() > 0 ? " + " + dice.bonus() : "");
        };
    }
}

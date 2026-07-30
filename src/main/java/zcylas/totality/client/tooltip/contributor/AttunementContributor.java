package zcylas.totality.client.tooltip.contributor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.item.TotalityArmorItem;
import zcylas.totality.api.item.TotalityItem;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipSectionGroup;
import zcylas.totality.client.tooltip.TooltipVisibility;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared semantic contributor for Attunement — the single place attunement status is now
 * rendered. Replaces {@code TotalityItem}'s old {@code TooltipExtension} default AND the
 * hand-duplicated attunement lines previously written directly inside
 * {@code RingOfProtectionItem.addTooltipLines}. Does not change Attunement mechanics — only
 * reads the existing {@link TotalityItem#requiresAttunement()}/{@link TotalityItem#isAttuned}
 * contract.
 *
 * Also surfaces an item's AC/save bonus as a plain requirement line, derived directly from
 * {@link TotalityArmorItem#getAcBonus()}/{@link TotalityArmorItem#getSaveBonus()} rather than a
 * hand-typed string — closing the drift risk the Tooltip API audit flagged for
 * {@code RingOfProtectionItem} (its old text could silently disagree with the real bonus value).
 * This does not redesign AC or Attunement — it only reads two existing getters for display.
 *
 * Per the locked unidentified-item policy, all attunement requirement/status lines and the
 * attunement-gated AC/save bonus line are identified-only
 * ({@link TooltipVisibility#WHEN_IDENTIFIED}) — an unidentified item's attunement nature and any
 * bonus it grants are exactly the kind of "hidden magical property" the policy calls out.
 */
public final class AttunementContributor implements TooltipContributor {

    @Override
    public TooltipSectionGroup sectionGroup() {
        return TooltipSectionGroup.ATTUNEMENT;
    }

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        ItemStack stack = ctx.stack();
        if (!(stack.getItem() instanceof TotalityItem totalityItem)) return List.of();

        List<TooltipSection> sections = new ArrayList<>();

        if (stack.getItem() instanceof TotalityArmorItem armor) {
            int ac = armor.getAcBonus();
            int save = armor.getSaveBonus();
            if (ac != 0 || save != 0) {
                StringBuilder text = new StringBuilder();
                if (ac != 0) text.append(ac > 0 ? "+" : "").append(ac).append(" AC Bonus");
                if (save != 0) {
                    if (!text.isEmpty()) text.append(", ");
                    text.append(save > 0 ? "+" : "").append(save).append(" Save Bonus");
                }
                sections.add(new TooltipSection.Requirement(text.toString(), false)
                        .withVisibility(TooltipVisibility.WHEN_IDENTIFIED));
            }
        }

        if (!totalityItem.requiresAttunement()) {
            sections.add(new TooltipSection.StatRow(null, 0xFF888888, "Attunement", "Free", 0xFF888888)
                    .withVisibility(TooltipVisibility.WHEN_IDENTIFIED));
            return sections;
        }

        sections.add(new TooltipSection.Requirement("Requires Attunement", false)
                .withVisibility(TooltipVisibility.WHEN_IDENTIFIED));

        Player player = ctx.player();
        UUID attunedTo = totalityItem.getAttunedTo(stack);
        boolean attuned = attunedTo != null && player != null && attunedTo.equals(player.getUUID());
        int color = attuned ? 0xFFFFD700 : 0xFFFF5555;
        sections.add(new TooltipSection.StatRow(null, color, "Attunement",
                attuned ? "Attuned" : "Not Attuned", color)
                .withVisibility(TooltipVisibility.WHEN_IDENTIFIED));

        return sections;
    }
}

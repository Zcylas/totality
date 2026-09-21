package zcylas.totality.client.tooltip.contributor;

import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.core.rpgutils.WeightComponent;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipSectionGroup;
import zcylas.totality.client.tooltip.TotalityIcons;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.List;
import java.util.Locale;

/**
 * Renders the shared {@link WeightComponent} generically. No other contributor duplicates weight
 * rendering — energy, weapon, and item-specific contributors all rely on this one.
 */
public final class WeightContributor implements TooltipContributor {

    @Override
    public TooltipSectionGroup sectionGroup() {
        return TooltipSectionGroup.WEIGHT;
    }

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        ItemStack stack = ctx.stack();
        var weightType = ItemComponents.getWeight();
        if (weightType == null || !stack.has(weightType)) return List.of();

        WeightComponent weight = stack.get(weightType);
        if (weight == null) return List.of();

        String value = String.format(Locale.ROOT, "%.1f", weight.weight());
        return List.of(new TooltipSection.StatRow(TotalityIcons.WEIGHT, 0xFF888888, "Weight", value, 0xFF888888));
    }
}

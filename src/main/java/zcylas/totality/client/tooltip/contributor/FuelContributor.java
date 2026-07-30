package zcylas.totality.client.tooltip.contributor;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipSectionGroup;
import zcylas.totality.client.tooltip.TotalityIcons;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.List;

/**
 * Migrated from the fuel row inside the old {@code TooltipStatBlock}. Kept as its own
 * contributor, separate from {@link EnergyContributor} — vanilla fuel burn time and Unified
 * Energy are different resources and an item could in principle expose both.
 */
public final class FuelContributor implements TooltipContributor {

    @Override
    public TooltipSectionGroup sectionGroup() {
        return TooltipSectionGroup.RESOURCES;
    }

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        ItemStack stack = ctx.stack();
        if (!ItemComponents.classificationsOf(stack).contains(ItemType.FUEL)) return List.of();

        Level level = ctx.level();
        if (level == null) return List.of();

        int ticks = level.fuelValues().burnDuration(stack);
        if (ticks <= 0) return List.of();

        int seconds = ticks / 20;
        return List.of(new TooltipSection.StatRow(TotalityIcons.FLAME, 0xFFFF6600, "Burn Time",
                seconds + "s", 0xFFFF6600));
    }
}

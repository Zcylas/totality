package zcylas.totality.client.tooltip.contributor;

import net.minecraft.world.item.ItemStack;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.group.TooltipGroup;
import zcylas.totality.client.tooltip.group.TooltipGroups;
import zcylas.totality.client.tooltip.renderer.TooltipResourceColors;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.List;

/**
 * The DURABILITY resource section: the item's physical wear, read from the ordinary item-damage state
 * ({@link ItemStack#isDamageableItem}, {@code getMaxDamage() - getDamageValue()}), so it always agrees with the
 * vanilla durability bar and with everything that already modifies damage (Unbreaking, Mending, ...). Unbreakable
 * items (e.g. UE armor) are not damageable and get no section. Presentation only.
 *
 * <p>Distinct from Block Durability, which is how much a placed block withstands and is a Properties entry.
 */
public final class DurabilityContributor implements TooltipContributor {

    private static final int FIGURES_COLOR = 0xFFC8CCD2;

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        ItemStack stack = ctx.stack();
        if (!stack.isDamageableItem()) return List.of();
        int max = stack.getMaxDamage();
        if (max <= 0) return List.of();
        int remaining = Math.max(0, max - stack.getDamageValue());
        float fraction = ResourceFormat.fraction(remaining, max);
        return List.of(new TooltipSection.ResourceGauge(remaining, max, TooltipResourceColors.durability(fraction),
                ResourceFormat.figures(remaining, max, "", true), FIGURES_COLOR));
    }

    @Override
    public TooltipGroup bodyGroup(TooltipContext ctx) {
        return TooltipGroups.DURABILITY;
    }
}

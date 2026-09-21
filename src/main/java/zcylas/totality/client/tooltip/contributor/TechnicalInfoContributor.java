package zcylas.totality.client.tooltip.contributor;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipDisclosureLevel;
import zcylas.totality.client.tooltip.TooltipSectionGroup;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.List;
import java.util.Set;

/**
 * Registry id, item class, and data-component count — only ever visible at
 * {@link TooltipDisclosureLevel#TECHNICAL}, matching {@link TooltipSection.TechnicalInfo}'s own
 * default visibility gate. Deliberately limited to information already public on the client
 * (registry id, component count) — no server-only or sensitive state is exposed here.
 */
public final class TechnicalInfoContributor implements TooltipContributor {

    @Override
    public TooltipSectionGroup sectionGroup() {
        return TooltipSectionGroup.TECHNICAL;
    }

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        if (!ctx.disclosure().atLeast(TooltipDisclosureLevel.TECHNICAL)) return List.of();

        ItemStack stack = ctx.stack();
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        DataComponentMap components = stack.getComponents();

        List<String> lines = List.of(
                "Registry: " + id,
                "Class: " + stack.getItem().getClass().getSimpleName(),
                "Components: " + components.size()
        );

        return List.of(new TooltipSection.TechnicalInfo(lines));
    }

    /**
     * Technical info is available for every opted-in item, unconditionally — must not require
     * Ctrl to already be held merely for the document to know Technical exists, otherwise the
     * footer could never offer the "CTRL: Technical" hint at Default/Details view in the first
     * place (a document built with Ctrl NOT held would never see {@link #contribute}'s gated
     * output and would wrongly conclude Technical has nothing to show).
     */
    @Override
    public Set<TooltipDisclosureLevel> availableDisclosureLevels(TooltipContext ctx) {
        return Set.of(TooltipDisclosureLevel.TECHNICAL);
    }
}

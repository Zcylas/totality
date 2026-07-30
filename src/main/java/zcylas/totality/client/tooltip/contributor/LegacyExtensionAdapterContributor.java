package zcylas.totality.client.tooltip.contributor;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import zcylas.totality.api.item.TotalityItem;
import zcylas.totality.client.tooltip.TooltipContext;
import zcylas.totality.client.tooltip.TooltipExtension;
import zcylas.totality.client.tooltip.TooltipSectionGroup;
import zcylas.totality.client.tooltip.section.TooltipSection;

import java.util.ArrayList;
import java.util.List;

/**
 * Transitional compatibility adapter — NOT the final contributor API. Translates any remaining
 * item that still implements the old {@link TooltipExtension} hook directly (and has not been
 * migrated to a dedicated contributor) into an {@link TooltipSection.ExternalContent} section,
 * so existing {@code addTooltipLines} implementations keep rendering without being rewritten
 * immediately.
 *
 * Skips anything implementing {@link TotalityItem}: TotalityItem-based items are fully served by
 * {@link AttunementContributor} (and any dedicated contributor for their concrete type), so
 * routing them through this adapter too would duplicate their attunement lines.
 */
public final class LegacyExtensionAdapterContributor implements TooltipContributor {

    @Override
    public TooltipSectionGroup sectionGroup() {
        return TooltipSectionGroup.EXTERNAL;
    }

    @Override
    public List<TooltipSection> contribute(TooltipContext ctx) {
        if (!(ctx.stack().getItem() instanceof TooltipExtension ext)) return List.of();
        if (ctx.stack().getItem() instanceof TotalityItem) return List.of();

        List<Component> lines = new ArrayList<>();
        ext.addTooltipLines(ctx.stack(), Minecraft.getInstance().font, lines);
        if (lines.isEmpty()) return List.of();

        return List.of(new TooltipSection.ExternalContent(lines));
    }
}

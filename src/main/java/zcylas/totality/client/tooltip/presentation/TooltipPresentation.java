package zcylas.totality.client.tooltip.presentation;

import zcylas.totality.api.core.rpgutils.rarity.TooltipCompanionPreview;
import zcylas.totality.api.core.rpgutils.rarity.TooltipDividerStyle;
import zcylas.totality.api.core.rpgutils.rarity.TooltipPreviewMode;
import zcylas.totality.api.core.rpgutils.rarity.TooltipPreviewMotion;
import zcylas.totality.api.core.rpgutils.rarity.TooltipVignetteStyle;

/**
 * Fully resolved answer to "HOW is this tooltip presented?" — never AUTO, never null. Produced by
 * {@link TooltipPresentationResolver}; consumed by the renderer. It owns no content: the item's
 * stats, lore and sections still come exclusively from the contributors.
 */
public record TooltipPresentation(
        TooltipPreviewMode previewMode,
        TooltipPreviewMotion previewMotion,
        TooltipCompanionPreview companionPreview,
        TooltipVignetteStyle vignetteStyle,
        TooltipDividerStyle dividerStyle
) {
    public TooltipPresentation {
        if (previewMode == TooltipPreviewMode.AUTO || previewMotion == TooltipPreviewMotion.AUTO) {
            throw new IllegalArgumentException("a resolved presentation must not contain AUTO");
        }
    }

    public boolean rotates() {
        return previewMotion == TooltipPreviewMotion.SLOW_ROTATE;
    }
}

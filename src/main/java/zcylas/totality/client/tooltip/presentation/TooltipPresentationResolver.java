package zcylas.totality.client.tooltip.presentation;

import zcylas.totality.api.core.rpgutils.rarity.TooltipCompanionPreview;
import zcylas.totality.api.core.rpgutils.rarity.TooltipDividerStyle;
import zcylas.totality.api.core.rpgutils.rarity.TooltipPreviewMode;
import zcylas.totality.api.core.rpgutils.rarity.TooltipPreviewMotion;
import zcylas.totality.api.core.rpgutils.rarity.TooltipProfileComponent;
import zcylas.totality.api.core.rpgutils.rarity.TooltipVignetteStyle;

/**
 * Resolves an item's authored {@link TooltipProfileComponent} into a concrete {@link TooltipPresentation}.
 * Pure: no Minecraft client state, so the precedence rules are unit-testable.
 *
 * <p>Precedence, per field:
 * <ol>
 *   <li>an explicit value authored on the item (its {@code TooltipProfileComponent}) always wins;</li>
 *   <li>otherwise AUTO inference from {@link AutoFacts} about the item's own resolved GUI model;</li>
 *   <li>otherwise the Totality default ({@link #DEFAULT_VIGNETTE}, {@link #DEFAULT_DIVIDER}).</li>
 * </ol>
 * Rarity and classification are deliberately not inputs: rarity themes the tooltip (colors, frame,
 * name effects) but never chooses sprite vs. model vs. block.
 *
 * <p>AUTO never picks {@code BLOCK_MODEL}: a block's default placed state can differ from its inventory
 * model (a fence's default state is a bare post), so the placed-block view is only shown when authored.
 * AUTO motion is always {@code STATIC}; rotation is only shown when authored.
 *
 * <p>Technical constraints are applied after authoring, not instead of it: a {@code SPRITE} preview is
 * a flat GUI-atlas image and cannot rotate, so its motion is always {@code STATIC}; a {@code BLOCK_MODEL}
 * preview needs a block-state model to draw, so an item without one resolves to {@code ITEM_MODEL} (the
 * resolved mode always names what is actually drawn); and a companion preview is only shown for items
 * that can actually be worn in a humanoid armor slot.
 */
public final class TooltipPresentationResolver {

    /** Current V2 default vignette for every theme unless an item explicitly overrides it. */
    public static final TooltipVignetteStyle DEFAULT_VIGNETTE = TooltipVignetteStyle.RADIAL;
    /** Current V2 default header/body divider, intentionally consistent across ordinary tooltips. */
    public static final TooltipDividerStyle DEFAULT_DIVIDER = TooltipDividerStyle.GRADIENT_ORNAMENT;

    /**
     * Facts about the item that AUTO inference and the technical constraints may use.
     *
     * @param flatGuiModel    the item's GUI model is a flat, front-lit sprite (not a 3D/side-lit model)
     * @param hasBlockModel   the item places a block whose placed state resolves to a non-empty block model;
     *                        only meaningful when {@link #needsBlockModelFact} is true (otherwise never read)
     * @param wornInArmorSlot the item is equippable in a humanoid armor slot (head/chest/legs/feet)
     */
    public record AutoFacts(boolean flatGuiModel, boolean hasBlockModel, boolean wornInArmorSlot) {}

    /**
     * Whether resolving {@code authored} can depend on {@link AutoFacts#hasBlockModel}. AUTO never picks
     * {@code BLOCK_MODEL}, so the fact — which costs a block-model resolution — is only needed when
     * {@code BLOCK_MODEL} is explicitly authored.
     */
    public static boolean needsBlockModelFact(TooltipProfileComponent authored) {
        return authored.previewMode() == TooltipPreviewMode.BLOCK_MODEL;
    }

    public static TooltipPresentation resolve(TooltipProfileComponent authored, AutoFacts facts) {
        TooltipPreviewMode mode = authored.previewMode() != TooltipPreviewMode.AUTO
                ? authored.previewMode()
                : autoPreviewMode(facts);
        if (mode == TooltipPreviewMode.BLOCK_MODEL && !facts.hasBlockModel()) mode = TooltipPreviewMode.ITEM_MODEL;

        TooltipPreviewMotion motion = authored.previewMotion() != TooltipPreviewMotion.AUTO
                ? authored.previewMotion()
                : TooltipPreviewMotion.STATIC;
        if (mode == TooltipPreviewMode.SPRITE) motion = TooltipPreviewMotion.STATIC;

        TooltipCompanionPreview companion = authored.companionPreview() == TooltipCompanionPreview.EQUIPPED_PLAYER
                && facts.wornInArmorSlot()
                ? TooltipCompanionPreview.EQUIPPED_PLAYER
                : TooltipCompanionPreview.NONE;

        return new TooltipPresentation(mode, motion, companion,
                authored.vignetteStyle().orElse(DEFAULT_VIGNETTE),
                authored.dividerStyle().orElse(DEFAULT_DIVIDER));
    }

    /** Flat sprites stay sprites; everything else shows its own 3D item model. */
    static TooltipPreviewMode autoPreviewMode(AutoFacts facts) {
        return facts.flatGuiModel() ? TooltipPreviewMode.SPRITE : TooltipPreviewMode.ITEM_MODEL;
    }

    private TooltipPresentationResolver() {}
}

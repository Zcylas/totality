package zcylas.totality.client.tooltip.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3x2f;
import org.joml.Vector3f;
import zcylas.totality.client.renderer.gui.TotalityGuiGraphics;
import zcylas.totality.client.tooltip.presentation.TooltipPresentation;
import zcylas.totality.client.tooltip.presentation.TooltipPresentationResolver;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.TooltipPreviewMode;
import zcylas.totality.api.core.rpgutils.rarity.TooltipProfileComponent;

/**
 * Draws the large preview at the top of a Totality tooltip into a fixed viewport, according to the
 * resolved {@link TooltipPresentation}:
 * <ul>
 *   <li>{@code SPRITE} — the item's normal GUI-atlas image at an integer magnification (pixel art stays
 *       crisp), submitted as an ordinary GUI item;</li>
 *   <li>{@code ITEM_MODEL} — the item's real 3D GUI model through Totality's picture-in-picture renderer
 *       ({@link TooltipModelPreviewRenderer}), scale-to-fit, optionally on a slow turntable;</li>
 *   <li>{@code BLOCK_MODEL} — the placed block-state model ({@link TooltipBlockPreview}) through
 *       {@link TooltipBlockPreviewRenderer}, in the standard GUI block view, scale-to-fit, optionally
 *       turning about the block's own vertical axis.</li>
 * </ul>
 * The item's GUI render state is resolved once per frame (exactly what vanilla does for every GUI item
 * draw) and reused for both AUTO inference and drawing. Nothing here is retained between frames, so a
 * hover change, world change or reconnect cannot leave stale preview state behind.
 */
public final class TooltipHeaderPreview {

    /** Resolves the item's GUI render state — the same resolution vanilla performs for {@code graphics.item(...)}. */
    public static TrackingItemStackRenderState resolveRenderState(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        TrackingItemStackRenderState state = new TrackingItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, mc.level, mc.player, 0);
        return state;
    }

    /**
     * Resolves how this stack's tooltip is presented: its authored {@link TooltipProfileComponent} (or the
     * all-AUTO {@link TooltipProfileComponent#STANDARD} when none is authored), with AUTO facts taken from
     * the already-resolved GUI render state.
     */
    public static TooltipPresentation resolvePresentation(ItemStack stack, TrackingItemStackRenderState state) {
        var type = ItemComponents.getTooltipProfile();
        TooltipProfileComponent authored = type == null ? null : stack.get(type);
        if (authored == null) authored = TooltipProfileComponent.STANDARD;
        return TooltipPresentationResolver.resolve(authored, autoFacts(stack, state, authored));
    }

    /**
     * Facts AUTO presentation may use, taken from the item's own model and components. The block model is
     * only resolved when the profile explicitly asks for {@code BLOCK_MODEL} — AUTO never picks it.
     */
    public static TooltipPresentationResolver.AutoFacts autoFacts(ItemStack stack, TrackingItemStackRenderState state,
                                                                   TooltipProfileComponent authored) {
        boolean flat = !state.usesBlockLight();
        boolean hasBlockModel = TooltipPresentationResolver.needsBlockModelFact(authored)
                && TooltipBlockPreview.resolve(stack) != null;
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        boolean wornInArmorSlot = equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
        return new TooltipPresentationResolver.AutoFacts(flat, hasBlockModel, wornInArmorSlot);
    }

    /** Draws the preview centred in the viewport {@code (x, y, w, h)}. A missing/empty model draws nothing. */
    public static void draw(GuiGraphicsExtractor graphics, ItemStack stack, TrackingItemStackRenderState state,
                            TooltipPresentation presentation, int x, int y, int w, int h, long timeMs) {
        if (w <= 0 || h <= 0) return;
        TotalityGuiGraphics gg = TotalityGuiGraphics.of(graphics);

        if (presentation.previewMode() == TooltipPreviewMode.BLOCK_MODEL) {
            // The resolver only yields BLOCK_MODEL for items with a block model; never substitute the item model.
            TooltipBlockPreview.Resolved block = TooltipBlockPreview.resolve(stack);
            if (block == null) return;
            AABB box = block.bounds();
            float scale;
            if (presentation.rotates()) {
                float radius = (float) Math.sqrt(box.getXsize() * box.getXsize() + box.getYsize() * box.getYsize()
                        + box.getZsize() * box.getZsize()) / 2f;
                scale = TooltipPreviewLayout.rotatingFitScale(radius, w, h);
            } else {
                float[] extents = TooltipPreviewLayout.blockViewExtents(
                        (float) box.getXsize(), (float) box.getYsize(), (float) box.getZsize());
                scale = TooltipPreviewLayout.fitScale(extents[0], extents[1], w, h);
            }
            Vector3f center = new Vector3f((float) box.getCenter().x, (float) box.getCenter().y, (float) box.getCenter().z);
            float spin = presentation.rotates() ? TooltipPreviewLayout.turntableDegrees(timeMs) : 0f;
            gg.guiRenderState().addPicturesInPictureState(new TooltipBlockPreviewRenderState(
                    block.parts(), x, y, x + w, y + h, scale, center, spin, gg.peekScissor()));
            return;
        }

        if (state.isEmpty()) return;

        if (presentation.previewMode() == TooltipPreviewMode.SPRITE) {
            int k = TooltipPreviewLayout.spriteScale(w, h);
            int size = 16 * k;
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + (w - size) / 2f, y + (h - size) / 2f);
            graphics.pose().scale(k, k);
            gg.guiRenderState().addItem(new GuiItemRenderState(new Matrix3x2f(graphics.pose()), state, 0, 0, gg.peekScissor()));
            graphics.pose().popMatrix();
            return;
        }

        AABB box = state.getModelBoundingBox();
        Vector3f center = new Vector3f((float) box.getCenter().x, (float) box.getCenter().y, (float) box.getCenter().z);
        float scale;
        if (presentation.rotates()) {
            float[] radius = {0f};
            state.visitExtents(p -> radius[0] = Math.max(radius[0], p.distance(center)));
            scale = TooltipPreviewLayout.rotatingFitScale(radius[0], w, h);
        } else {
            scale = TooltipPreviewLayout.fitScale((float) box.getXsize(), (float) box.getYsize(), w, h);
        }
        float spin = presentation.rotates() ? TooltipPreviewLayout.turntableDegrees(timeMs) : 0f;
        gg.guiRenderState().addPicturesInPictureState(new TooltipModelPreviewRenderState(
                state, x, y, x + w, y + h, scale, center, spin, gg.peekScissor()));
    }

    private TooltipHeaderPreview() {}
}

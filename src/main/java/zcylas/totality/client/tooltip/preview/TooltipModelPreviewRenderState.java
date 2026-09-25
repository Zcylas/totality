package zcylas.totality.client.tooltip.preview;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

/**
 * Picture-in-picture request for a true 3D item model preview inside the tooltip header
 * ({@code ITEM_MODEL}; {@code BLOCK_MODEL} uses {@link TooltipBlockPreviewRenderState}). Rendered by {@link TooltipModelPreviewRenderer}, which
 * Totality registers through Fabric's {@code PictureInPictureRendererRegistry}.
 *
 * <p>A pure per-frame value: it holds the item's already-resolved GUI render state (the same object
 * vanilla builds for every GUI item draw), never an entity, player, level or other long-lived state.
 *
 * @param itemState   the item's resolved GUI-context render state
 * @param x0          viewport left (GUI units)
 * @param y0          viewport top
 * @param x1          viewport right
 * @param y1          viewport bottom
 * @param scale       GUI pixels per model unit (scale-to-fit result, stable across frames)
 * @param modelCenter centre of the model's GUI-space bounds, so the subject is centred in the viewport
 * @param spinDegrees turntable angle (0 when static)
 */
public record TooltipModelPreviewRenderState(
        TrackingItemStackRenderState itemState,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        Vector3fc modelCenter,
        float spinDegrees,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {

    public TooltipModelPreviewRenderState(TrackingItemStackRenderState itemState, int x0, int y0, int x1, int y1,
                                          float scale, Vector3fc modelCenter, float spinDegrees,
                                          @Nullable ScreenRectangle scissorArea) {
        this(itemState, x0, y0, x1, y1, scale, modelCenter, spinDegrees, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}

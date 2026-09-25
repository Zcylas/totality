package zcylas.totality.client.tooltip.preview;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3fc;

import java.util.List;

/**
 * Picture-in-picture request for a {@code BLOCK_MODEL} preview: the hovered block item's placed
 * block-state model ({@link TooltipBlockPreview}), rendered by {@link TooltipBlockPreviewRenderer}.
 * A pure per-frame value holding already-resolved block-model render states, never a level or entity.
 *
 * @param parts       the resolved block-model pieces
 * @param x0          viewport left (GUI units)
 * @param y0          viewport top
 * @param x1          viewport right
 * @param y1          viewport bottom
 * @param scale       GUI pixels per block (scale-to-fit result, stable across frames)
 * @param center      centre of the pieces' combined bounds in block units, so the block is centred
 * @param spinDegrees turntable angle about the block's vertical axis (0 when static)
 */
public record TooltipBlockPreviewRenderState(
        List<TooltipBlockPreview.Part> parts,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        Vector3fc center,
        float spinDegrees,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {

    public TooltipBlockPreviewRenderState(List<TooltipBlockPreview.Part> parts, int x0, int y0, int x1, int y1,
                                          float scale, Vector3fc center, float spinDegrees,
                                          @Nullable ScreenRectangle scissorArea) {
        this(parts, x0, y0, x1, y1, scale, center, spinDegrees, scissorArea,
                PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }
}

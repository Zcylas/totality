package zcylas.totality.client.tooltip.preview;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a {@link TooltipModelPreviewRenderState} — the tooltip header's true 3D item model
 * preview — through Minecraft 26.2's picture-in-picture path: the model is drawn into an off-screen
 * texture sized to the preview viewport at the real GUI scale, then blitted into the tooltip's own
 * GUI stratum. Structured after vanilla's {@code OversizedItemRenderer}, which does the same for
 * oversized GUI items. No live entity, no private accessors, no 3D GUI pose tricks.
 *
 * <p>Lifecycle: registered once from {@code TotalityClient} via Fabric's
 * {@code PictureInPictureRendererRegistry}; instances are created, pooled and closed by the game's
 * {@code GuiRenderer} (and Fabric's per-frame renderer pool), exactly like vanilla PIP renderers.
 * The only state kept here is what decides whether last frame's texture can be reused.
 */
public class TooltipModelPreviewRenderer extends PictureInPictureRenderer<TooltipModelPreviewRenderState> {

    private static final int FULL_BRIGHT = 15728880;

    private @Nullable Object lastModelIdentity;
    private float lastSpin = Float.NaN;
    private float lastScale = Float.NaN;

    @Override
    public Class<TooltipModelPreviewRenderState> getRenderStateClass() {
        return TooltipModelPreviewRenderState.class;
    }

    @Override
    protected void renderToTexture(TooltipModelPreviewRenderState state, PoseStack poseStack,
                                   SubmitNodeCollector submitNodeCollector) {
        // Same GUI-to-model flip vanilla's oversized-item PIP applies.
        poseStack.scale(1.0F, -1.0F, -1.0F);
        if (state.spinDegrees() != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(state.spinDegrees()));
        }
        poseStack.translate(-state.modelCenter().x(), -state.modelCenter().y(), -state.modelCenter().z());

        TrackingItemStackRenderState itemState = state.itemState();
        Minecraft.getInstance().gameRenderer.lighting().setupFor(
                itemState.usesBlockLight() ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
        itemState.submit(poseStack, submitNodeCollector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);

        lastModelIdentity = itemState.getModelIdentity();
        lastSpin = state.spinDegrees();
        lastScale = state.scale();
    }

    /** A static, non-animated preview of the same model at the same scale reuses last frame's texture. */
    @Override
    protected boolean textureIsReadyToBlit(TooltipModelPreviewRenderState state) {
        TrackingItemStackRenderState itemState = state.itemState();
        return !itemState.isAnimated()
                && state.spinDegrees() == lastSpin
                && state.scale() == lastScale
                && itemState.getModelIdentity().equals(lastModelIdentity);
    }

    /** Centre the subject vertically in the texture (vanilla's default anchors entities at the bottom). */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "totality_tooltip_model_preview";
    }
}

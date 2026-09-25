package zcylas.totality.client.tooltip.preview;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Renders a {@link TooltipBlockPreviewRenderState} — the {@code BLOCK_MODEL} header preview — through
 * Minecraft 26.2's picture-in-picture path, submitting each resolved {@code BlockModelRenderState} with
 * {@code BlockModelRenderState.submit} (which issues {@code SubmitNodeCollector.submitBlockModel} for the
 * block-state model parts, and the special renderer for built-in block models such as chests).
 *
 * <p>The view is the standard GUI block view (the same {@code [30, 225, 0]} rotation vanilla's block items
 * use in inventories), so a block reads the same way as its inventory icon; the turntable spins about the
 * block's own vertical axis inside that view.
 *
 * <p>Registered once from {@code TotalityClient} via Fabric's {@code PictureInPictureRendererRegistry}.
 * It never reuses the previous texture: a block-model render state exposes no identity (unlike an item's
 * {@code getModelIdentity()}) that would cover animated block textures or a resource reload, so the one
 * small block is re-rendered each frame — the same cost as a rotating preview.
 */
public class TooltipBlockPreviewRenderer extends PictureInPictureRenderer<TooltipBlockPreviewRenderState> {

    private static final int FULL_BRIGHT = 15728880;

    @Override
    public Class<TooltipBlockPreviewRenderState> getRenderStateClass() {
        return TooltipBlockPreviewRenderState.class;
    }

    @Override
    protected void renderToTexture(TooltipBlockPreviewRenderState state, PoseStack poseStack,
                                   SubmitNodeCollector submitNodeCollector) {
        // Same GUI-to-model flip as the item preview, then the standard GUI block view about the block's centre.
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(TooltipPreviewLayout.BLOCK_VIEW_PITCH_DEGREES));
        poseStack.mulPose(Axis.YP.rotationDegrees(TooltipPreviewLayout.BLOCK_VIEW_YAW_DEGREES + state.spinDegrees()));
        poseStack.translate(-state.center().x(), -state.center().y(), -state.center().z());

        Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
        for (TooltipBlockPreview.Part part : state.parts()) {
            poseStack.pushPose();
            poseStack.translate(0.0F, part.yOffset(), 0.0F);
            part.model().submit(poseStack, submitNodeCollector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    /** Centre the subject vertically in the texture (vanilla's default anchors entities at the bottom). */
    @Override
    protected float getTranslateY(int height, int guiScale) {
        return height / 2.0F;
    }

    @Override
    protected String getTextureLabel() {
        return "totality_tooltip_block_preview";
    }
}

package zcylas.totality.client.renderer.fluid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.fluid.TotalityFluidStorage;
import zcylas.totality.blockentity.fluid.FluidTankBlockEntity;

public class FluidTankRenderer implements BlockEntityRenderer<FluidTankBlockEntity, FluidTankRenderer.FluidTankRenderState> {

    // Bounds match Blockbench model (3-13 on X/Z, 0-15 on Y)
    private static final float MIN_X = (3f / 16f) + 0.01f;
    private static final float MAX_X = (13f / 16f) - 0.01f;
    private static final float MIN_Z = (3f / 16f) + 0.01f;
    private static final float MAX_Z = (13f / 16f) - 0.01f;
    private static final float MIN_Y = 0.01f;
    private static final float MAX_Y = (15f / 16f) - 0.01f;

    public FluidTankRenderer(BlockEntityRendererProvider.Context context) {}

    // Custom render state carrying only what we need for rendering
    public static class FluidTankRenderState extends BlockEntityRenderState {
        public FluidVariant fluidVariant = FluidVariant.blank();
        public double fillFraction = 0.0;
        public @Nullable BlockAndTintGetter level = null;
        public BlockPos pos = BlockPos.ZERO;
    }

    @Override
    public FluidTankRenderState createRenderState() {
        return new FluidTankRenderState();
    }

    @Override
    public void extractRenderState(FluidTankBlockEntity blockEntity, FluidTankRenderState state,
                                   float partialTicks, Vec3 cameraPosition,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);

        TotalityFluidStorage storage = blockEntity.getFluidStorage();
        state.fluidVariant = storage.isEmpty() ? FluidVariant.blank() : storage.variant;
        state.fillFraction = storage.getFillFraction();
        state.level = blockEntity.getLevel() instanceof BlockAndTintGetter bat ? bat : null;
        state.pos = blockEntity.getBlockPos();
    }

    @Override
    public void submit(FluidTankRenderState state, PoseStack poseStack,
                       SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.fluidVariant.isBlank() || state.fillFraction <= 0) return;

        Fluid fluid = state.fluidVariant.getFluid();
        FluidState fluidState = fluid.defaultFluidState();

        FluidModel model = Minecraft.getInstance()
                .getModelManager()
                .getFluidStateModelSet()
                .get(fluidState);

        if (model == null) return;

        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        int tintColor = getTintColor(model, state);

        float alpha = ((tintColor >> 24) & 0xFF) / 255f;
        float red   = ((tintColor >> 16) & 0xFF) / 255f;
        float green = ((tintColor >>  8) & 0xFF) / 255f;
        float blue  = ( tintColor        & 0xFF) / 255f;

        float fluidHeight = MIN_Y + (float)(state.fillFraction * (MAX_Y - MIN_Y));

        // Use submitCustomGeometry with translucent render type
        RenderType renderType = RenderTypes.entityTranslucent(sprite.atlasLocation());

        submitNodeCollector.submitCustomGeometry(poseStack, renderType,
                (pose, consumer) -> renderFluid(pose.pose(), consumer, sprite,
                        fluidHeight, red, green, blue, alpha, state.lightCoords));
    }

    private void renderFluid(Matrix4f matrix, VertexConsumer consumer,
                             TextureAtlasSprite sprite, float fluidHeight,
                             float r, float g, float b, float a, int light) {
        renderTop(matrix, consumer, sprite,
                MIN_X, MAX_X, MIN_Z, MAX_Z, fluidHeight, r, g, b, a, light);
        renderBottom(matrix, consumer, sprite,
                MIN_X, MAX_X, MIN_Z, MAX_Z, MIN_Y, r, g, b, a, light);
        renderNorth(matrix, consumer, sprite,
                MIN_X, MAX_X, MIN_Z, MIN_Y, fluidHeight, r, g, b, a, light);
        renderSouth(matrix, consumer, sprite,
                MIN_X, MAX_X, MAX_Z, MIN_Y, fluidHeight, r, g, b, a, light);
        renderWest(matrix, consumer, sprite,
                MIN_X, MIN_Z, MAX_Z, MIN_Y, fluidHeight, r, g, b, a, light);
        renderEast(matrix, consumer, sprite,
                MAX_X, MIN_Z, MAX_Z, MIN_Y, fluidHeight, r, g, b, a, light);
    }

    private int getTintColor(FluidModel model, FluidTankRenderState state) {
        if (model.tintSource() == null) return 0xFFFFFFFF;

        BlockState blockState = state.fluidVariant.getFluid()
                .defaultFluidState().createLegacyBlock();

        int color;
        if (state.level != null) {
            color = model.tintSource().colorInWorld(blockState, state.level, state.pos);
        } else {
            color = model.tintSource().color(blockState);
        }
        return 0xFF000000 | (color & 0xFFFFFF);
    }

    private void renderTop(Matrix4f matrix, VertexConsumer consumer,
                           TextureAtlasSprite sprite,
                           float minX, float maxX, float minZ, float maxZ,
                           float y, float r, float g, float b, float a, int light) {
        float u0 = sprite.getU(minX);
        float u1 = sprite.getU(maxX);
        float v0 = sprite.getV(minZ);
        float v1 = sprite.getV(maxZ);

        consumer.addVertex(matrix, minX, y, minZ).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(matrix, minX, y, maxZ).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(matrix, maxX, y, maxZ).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
        consumer.addVertex(matrix, maxX, y, minZ).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 1, 0);
    }

    private void renderBottom(Matrix4f matrix, VertexConsumer consumer,
                              TextureAtlasSprite sprite,
                              float minX, float maxX, float minZ, float maxZ,
                              float y, float r, float g, float b, float a, int light) {
        float u0 = sprite.getU(minX);
        float u1 = sprite.getU(maxX);
        float v0 = sprite.getV(minZ);
        float v1 = sprite.getV(maxZ);

        consumer.addVertex(matrix, minX, y, maxZ).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, -1, 0);
        consumer.addVertex(matrix, minX, y, minZ).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, -1, 0);
        consumer.addVertex(matrix, maxX, y, minZ).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, -1, 0);
        consumer.addVertex(matrix, maxX, y, maxZ).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, -1, 0);
    }

    private void renderNorth(Matrix4f matrix, VertexConsumer consumer,
                             TextureAtlasSprite sprite,
                             float minX, float maxX, float z,
                             float minY, float maxY,
                             float r, float g, float b, float a, int light) {
        float u0 = sprite.getU(minX);
        float u1 = sprite.getU(maxX);
        float v0 = sprite.getV(minY);
        float v1 = sprite.getV(maxY);

        consumer.addVertex(matrix, maxX, maxY, z).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, -1);
        consumer.addVertex(matrix, maxX, minY, z).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, -1);
        consumer.addVertex(matrix, minX, minY, z).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, -1);
        consumer.addVertex(matrix, minX, maxY, z).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, -1);
    }

    private void renderSouth(Matrix4f matrix, VertexConsumer consumer,
                             TextureAtlasSprite sprite,
                             float minX, float maxX, float z,
                             float minY, float maxY,
                             float r, float g, float b, float a, int light) {
        float u0 = sprite.getU(minX);
        float u1 = sprite.getU(maxX);
        float v0 = sprite.getV(minY);
        float v1 = sprite.getV(maxY);

        consumer.addVertex(matrix, minX, maxY, z).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        consumer.addVertex(matrix, minX, minY, z).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        consumer.addVertex(matrix, maxX, minY, z).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
        consumer.addVertex(matrix, maxX, maxY, z).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, 1);
    }

    private void renderWest(Matrix4f matrix, VertexConsumer consumer,
                            TextureAtlasSprite sprite,
                            float x, float minZ, float maxZ,
                            float minY, float maxY,
                            float r, float g, float b, float a, int light) {
        float u0 = sprite.getU(minZ);
        float u1 = sprite.getU(maxZ);
        float v0 = sprite.getV(minY);
        float v1 = sprite.getV(maxY);

        consumer.addVertex(matrix, x, maxY, minZ).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(-1, 0, 0);
        consumer.addVertex(matrix, x, minY, minZ).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(-1, 0, 0);
        consumer.addVertex(matrix, x, minY, maxZ).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(-1, 0, 0);
        consumer.addVertex(matrix, x, maxY, maxZ).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(-1, 0, 0);
    }

    private void renderEast(Matrix4f matrix, VertexConsumer consumer,
                            TextureAtlasSprite sprite,
                            float x, float minZ, float maxZ,
                            float minY, float maxY,
                            float r, float g, float b, float a, int light) {
        float u0 = sprite.getU(minZ);
        float u1 = sprite.getU(maxZ);
        float v0 = sprite.getV(minY);
        float v1 = sprite.getV(maxY);

        consumer.addVertex(matrix, x, maxY, maxZ).setColor(r, g, b, a).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1, 0, 0);
        consumer.addVertex(matrix, x, minY, maxZ).setColor(r, g, b, a).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1, 0, 0);
        consumer.addVertex(matrix, x, minY, minZ).setColor(r, g, b, a).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1, 0, 0);
        consumer.addVertex(matrix, x, maxY, minZ).setColor(r, g, b, a).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1, 0, 0);
    }
}
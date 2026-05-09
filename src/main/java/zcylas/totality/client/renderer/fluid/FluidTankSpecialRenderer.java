package zcylas.totality.client.renderer.fluid;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import zcylas.totality.block.fluid.FluidTankBlock;
import zcylas.totality.item.fluid.FluidTankItem;

import java.util.function.Consumer;

public class FluidTankSpecialRenderer
        implements SpecialModelRenderer<FluidTankSpecialRenderer.RenderData> {

    // Same bounds as FluidTankRenderer
    private static final float MIN_X = (3f / 16f) + 0.01f;
    private static final float MAX_X = (13f / 16f) - 0.01f;
    private static final float MIN_Z = (3f / 16f) + 0.01f;
    private static final float MAX_Z = (13f / 16f) - 0.01f;
    private static final float MIN_Y = 0.01f;
    private static final float MAX_Y = (15f / 16f) - 0.01f;

    public static final FluidTankSpecialRenderer INSTANCE = new FluidTankSpecialRenderer();

    @Override
    public @Nullable RenderData extractArgument(ItemStack stack) {
        var beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (beData == null) return null;

        CompoundTag tag = beData.copyTagWithoutId();
        long amount = tag.getLongOr("Amount", 0L);
        if (amount <= 0) return null;

        FluidVariant variant = tag.read("Fluid", FluidVariant.CODEC)
                .orElse(FluidVariant.blank());
        if (variant.isBlank()) return null;

        long capacity = 8_000L * 81L;
        if (stack.getItem() instanceof FluidTankItem tankItem) {
            capacity = ((FluidTankBlock) tankItem.getBlock()).getCapacityMb() * 81L;
        }

        return new RenderData(variant, (double) amount / capacity);
    }

    @Override
    public void submit(@Nullable RenderData data, PoseStack poseStack,
                       SubmitNodeCollector collector, int lightCoords,
                       int overlayCoords, boolean hasFoil, int outlineColor) {

        // Render tank walls
        Identifier tankTextureId = Identifier.fromNamespaceAndPath("totality", "block/basic_fluid_tank");
        TextureAtlasSprite tankSprite = Minecraft.getInstance()
                .getAtlasManager()
                .get(new SpriteId(TextureAtlas.LOCATION_BLOCKS, tankTextureId));

        collector.submitCustomGeometry(poseStack,
                RenderTypes.entityCutout(tankSprite.atlasLocation()),
                (pose, consumer) -> renderTankWalls(
                        pose.pose(), consumer, tankSprite, lightCoords));

        // Render fluid
        if (data == null) return;
        FluidVariant variant = data.variant();
        double fillFraction = data.fillFraction();
        if (fillFraction <= 0 || variant.isBlank()) return;

        Fluid fluid = variant.getFluid();
        FluidState fluidState = fluid.defaultFluidState();

        FluidModel model = Minecraft.getInstance()
                .getModelManager()
                .getFluidStateModelSet()
                .get(fluidState);
        if (model == null) return;

        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        int tintColor = getTintColor(model, fluidState.createLegacyBlock());

        float alpha = ((tintColor >> 24) & 0xFF) / 255f;
        float red   = ((tintColor >> 16) & 0xFF) / 255f;
        float green = ((tintColor >>  8) & 0xFF) / 255f;
        float blue  = ( tintColor        & 0xFF) / 255f;

        float fluidHeight = MIN_Y + (float)(fillFraction * (MAX_Y - MIN_Y));

        collector.submitCustomGeometry(poseStack,
                RenderTypes.entityTranslucent(sprite.atlasLocation()),
                (pose, consumer) -> renderFluid(pose.pose(), consumer, sprite,
                        fluidHeight, red, green, blue, alpha, lightCoords));
    }

    private void renderTankWalls(Matrix4f matrix, VertexConsumer consumer,
                                 TextureAtlasSprite sprite, int light) {
        float minX = 3f / 16f;
        float maxX = 13f / 16f;
        float minZ = 3f / 16f;
        float maxZ = 13f / 16f;
        float minY = 0f;
        float maxY = 15f / 16f;
        int overlay = OverlayTexture.NO_OVERLAY;

        // North
        float u0=sprite.getU(minX), u1=sprite.getU(maxX);
        float v0=sprite.getV(minY), v1=sprite.getV(maxY);
        consumer.addVertex(matrix, maxX, maxY, minZ).setColor(1f,1f,1f,1f).setUv(u0,v0).setOverlay(overlay).setLight(light).setNormal(0,0,-1);
        consumer.addVertex(matrix, maxX, minY, minZ).setColor(1f,1f,1f,1f).setUv(u0,v1).setOverlay(overlay).setLight(light).setNormal(0,0,-1);
        consumer.addVertex(matrix, minX, minY, minZ).setColor(1f,1f,1f,1f).setUv(u1,v1).setOverlay(overlay).setLight(light).setNormal(0,0,-1);
        consumer.addVertex(matrix, minX, maxY, minZ).setColor(1f,1f,1f,1f).setUv(u1,v0).setOverlay(overlay).setLight(light).setNormal(0,0,-1);

        // East
        u0=sprite.getU(minZ); u1=sprite.getU(maxZ);
        v0=sprite.getV(minY); v1=sprite.getV(maxY);
        consumer.addVertex(matrix, maxX, maxY, maxZ).setColor(1f,1f,1f,1f).setUv(u0,v0).setOverlay(overlay).setLight(light).setNormal(1,0,0);
        consumer.addVertex(matrix, maxX, minY, maxZ).setColor(1f,1f,1f,1f).setUv(u0,v1).setOverlay(overlay).setLight(light).setNormal(1,0,0);
        consumer.addVertex(matrix, maxX, minY, minZ).setColor(1f,1f,1f,1f).setUv(u1,v1).setOverlay(overlay).setLight(light).setNormal(1,0,0);
        consumer.addVertex(matrix, maxX, maxY, minZ).setColor(1f,1f,1f,1f).setUv(u1,v0).setOverlay(overlay).setLight(light).setNormal(1,0,0);

        // South
        u0=sprite.getU(minX); u1=sprite.getU(maxX);
        v0=sprite.getV(minY); v1=sprite.getV(maxY);
        consumer.addVertex(matrix, minX, maxY, maxZ).setColor(1f,1f,1f,1f).setUv(u0,v0).setOverlay(overlay).setLight(light).setNormal(0,0,1);
        consumer.addVertex(matrix, minX, minY, maxZ).setColor(1f,1f,1f,1f).setUv(u0,v1).setOverlay(overlay).setLight(light).setNormal(0,0,1);
        consumer.addVertex(matrix, maxX, minY, maxZ).setColor(1f,1f,1f,1f).setUv(u1,v1).setOverlay(overlay).setLight(light).setNormal(0,0,1);
        consumer.addVertex(matrix, maxX, maxY, maxZ).setColor(1f,1f,1f,1f).setUv(u1,v0).setOverlay(overlay).setLight(light).setNormal(0,0,1);

        // West
        u0=sprite.getU(minZ); u1=sprite.getU(maxZ);
        v0=sprite.getV(minY); v1=sprite.getV(maxY);
        consumer.addVertex(matrix, minX, maxY, minZ).setColor(1f,1f,1f,1f).setUv(u0,v0).setOverlay(overlay).setLight(light).setNormal(-1,0,0);
        consumer.addVertex(matrix, minX, minY, minZ).setColor(1f,1f,1f,1f).setUv(u0,v1).setOverlay(overlay).setLight(light).setNormal(-1,0,0);
        consumer.addVertex(matrix, minX, minY, maxZ).setColor(1f,1f,1f,1f).setUv(u1,v1).setOverlay(overlay).setLight(light).setNormal(-1,0,0);
        consumer.addVertex(matrix, minX, maxY, maxZ).setColor(1f,1f,1f,1f).setUv(u1,v0).setOverlay(overlay).setLight(light).setNormal(-1,0,0);

        // Up
        u0=sprite.getU(minX); u1=sprite.getU(maxX);
        v0=sprite.getV(minZ); v1=sprite.getV(maxZ);
        consumer.addVertex(matrix, minX, maxY, minZ).setColor(1f,1f,1f,1f).setUv(u0,v0).setOverlay(overlay).setLight(light).setNormal(0,1,0);
        consumer.addVertex(matrix, minX, maxY, maxZ).setColor(1f,1f,1f,1f).setUv(u0,v1).setOverlay(overlay).setLight(light).setNormal(0,1,0);
        consumer.addVertex(matrix, maxX, maxY, maxZ).setColor(1f,1f,1f,1f).setUv(u1,v1).setOverlay(overlay).setLight(light).setNormal(0,1,0);
        consumer.addVertex(matrix, maxX, maxY, minZ).setColor(1f,1f,1f,1f).setUv(u1,v0).setOverlay(overlay).setLight(light).setNormal(0,1,0);

        // Down
        u0=sprite.getU(minX); u1=sprite.getU(maxX);
        v0=sprite.getV(minZ); v1=sprite.getV(maxZ);
        consumer.addVertex(matrix, minX, minY, maxZ).setColor(1f,1f,1f,1f).setUv(u0,v1).setOverlay(overlay).setLight(light).setNormal(0,-1,0);
        consumer.addVertex(matrix, minX, minY, minZ).setColor(1f,1f,1f,1f).setUv(u0,v0).setOverlay(overlay).setLight(light).setNormal(0,-1,0);
        consumer.addVertex(matrix, maxX, minY, minZ).setColor(1f,1f,1f,1f).setUv(u1,v0).setOverlay(overlay).setLight(light).setNormal(0,-1,0);
        consumer.addVertex(matrix, maxX, minY, maxZ).setColor(1f,1f,1f,1f).setUv(u1,v1).setOverlay(overlay).setLight(light).setNormal(0,-1,0);
    }


    private int getTintColor(FluidModel model, BlockState blockState) {
        if (model.tintSource() == null) return 0xFFFFFFFF;
        int color = model.tintSource().color(blockState);
        return 0xFF000000 | (color & 0xFFFFFF);
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

    // All face methods identical to FluidTankRenderer
    private void renderTop(Matrix4f matrix, VertexConsumer consumer,
                           TextureAtlasSprite sprite,
                           float minX, float maxX, float minZ, float maxZ,
                           float y, float r, float g, float b, float a, int light) {
        float u0 = sprite.getU(minX); float u1 = sprite.getU(maxX);
        float v0 = sprite.getV(minZ); float v1 = sprite.getV(maxZ);
        consumer.addVertex(matrix, minX, y, minZ).setColor(r,g,b,a).setUv(u0,v0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,1,0);
        consumer.addVertex(matrix, minX, y, maxZ).setColor(r,g,b,a).setUv(u0,v1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,1,0);
        consumer.addVertex(matrix, maxX, y, maxZ).setColor(r,g,b,a).setUv(u1,v1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,1,0);
        consumer.addVertex(matrix, maxX, y, minZ).setColor(r,g,b,a).setUv(u1,v0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,1,0);
    }

    private void renderBottom(Matrix4f matrix, VertexConsumer consumer,
                              TextureAtlasSprite sprite,
                              float minX, float maxX, float minZ, float maxZ,
                              float y, float r, float g, float b, float a, int light) {
        float u0 = sprite.getU(minX); float u1 = sprite.getU(maxX);
        float v0 = sprite.getV(minZ); float v1 = sprite.getV(maxZ);
        consumer.addVertex(matrix, minX, y, maxZ).setColor(r,g,b,a).setUv(u0,v1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,-1,0);
        consumer.addVertex(matrix, minX, y, minZ).setColor(r,g,b,a).setUv(u0,v0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,-1,0);
        consumer.addVertex(matrix, maxX, y, minZ).setColor(r,g,b,a).setUv(u1,v0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,-1,0);
        consumer.addVertex(matrix, maxX, y, maxZ).setColor(r,g,b,a).setUv(u1,v1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,-1,0);
    }

    private void renderNorth(Matrix4f matrix, VertexConsumer consumer,
                             TextureAtlasSprite sprite,
                             float minX, float maxX, float z,
                             float minY, float maxY,
                             float r, float g, float b, float a, int light) {
        float u0 = sprite.getU(minX); float u1 = sprite.getU(maxX);
        float v0 = sprite.getV(minY); float v1 = sprite.getV(maxY);
        consumer.addVertex(matrix, maxX, maxY, z).setColor(r,g,b,a).setUv(u0,v0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,0,-1);
        consumer.addVertex(matrix, maxX, minY, z).setColor(r,g,b,a).setUv(u0,v1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,0,-1);
        consumer.addVertex(matrix, minX, minY, z).setColor(r,g,b,a).setUv(u1,v1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,0,-1);
        consumer.addVertex(matrix, minX, maxY, z).setColor(r,g,b,a).setUv(u1,v0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,0,-1);
    }

    private void renderSouth(Matrix4f matrix, VertexConsumer consumer,
                             TextureAtlasSprite sprite,
                             float minX, float maxX, float z,
                             float minY, float maxY,
                             float r, float g, float b, float a, int light) {
        float u0 = sprite.getU(minX); float u1 = sprite.getU(maxX);
        float v0 = sprite.getV(minY); float v1 = sprite.getV(maxY);
        consumer.addVertex(matrix, minX, maxY, z).setColor(r,g,b,a).setUv(u0,v0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,0,1);
        consumer.addVertex(matrix, minX, minY, z).setColor(r,g,b,a).setUv(u0,v1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,0,1);
        consumer.addVertex(matrix, maxX, minY, z).setColor(r,g,b,a).setUv(u1,v1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,0,1);
        consumer.addVertex(matrix, maxX, maxY, z).setColor(r,g,b,a).setUv(u1,v0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,0,1);
    }

    private void renderWest(Matrix4f matrix, VertexConsumer consumer,
                            TextureAtlasSprite sprite,
                            float x, float minZ, float maxZ,
                            float minY, float maxY,
                            float r, float g, float b, float a, int light) {
        float u0 = sprite.getU(minZ); float u1 = sprite.getU(maxZ);
        float v0 = sprite.getV(minY); float v1 = sprite.getV(maxY);
        consumer.addVertex(matrix, x, maxY, minZ).setColor(r,g,b,a).setUv(u0,v0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(-1,0,0);
        consumer.addVertex(matrix, x, minY, minZ).setColor(r,g,b,a).setUv(u0,v1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(-1,0,0);
        consumer.addVertex(matrix, x, minY, maxZ).setColor(r,g,b,a).setUv(u1,v1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(-1,0,0);
        consumer.addVertex(matrix, x, maxY, maxZ).setColor(r,g,b,a).setUv(u1,v0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(-1,0,0);
    }

    private void renderEast(Matrix4f matrix, VertexConsumer consumer,
                            TextureAtlasSprite sprite,
                            float x, float minZ, float maxZ,
                            float minY, float maxY,
                            float r, float g, float b, float a, int light) {
        float u0 = sprite.getU(minZ); float u1 = sprite.getU(maxZ);
        float v0 = sprite.getV(minY); float v1 = sprite.getV(maxY);
        consumer.addVertex(matrix, x, maxY, maxZ).setColor(r,g,b,a).setUv(u0,v0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1,0,0);
        consumer.addVertex(matrix, x, minY, maxZ).setColor(r,g,b,a).setUv(u0,v1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1,0,0);
        consumer.addVertex(matrix, x, minY, minZ).setColor(r,g,b,a).setUv(u1,v1).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1,0,0);
        consumer.addVertex(matrix, x, maxY, minZ).setColor(r,g,b,a).setUv(u1,v0).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(light).setNormal(1,0,0);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new org.joml.Vector3f(0, 0, 0));
        output.accept(new org.joml.Vector3f(1, 1, 1));
    }

    public record RenderData(FluidVariant variant, double fillFraction) {}

    // Unbaked inner class for registration
    public static class Unbaked
            implements SpecialModelRenderer.Unbaked<RenderData> {

        public static final MapCodec<Unbaked> CODEC =
                MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<? extends SpecialModelRenderer.Unbaked<RenderData>> type() {
            return CODEC;
        }

        @Override
        public @Nullable SpecialModelRenderer<RenderData> bake(BakingContext context) {
            return INSTANCE;
        }
    }
}
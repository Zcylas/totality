package zcylas.totality.client.renderer.energy;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import zcylas.totality.api.energy.base.SimpleSidedUEContainer;
import zcylas.totality.api.item.ItemSideMode;
import zcylas.totality.client.config.ItemSideModeClientCache;
import zcylas.totality.client.config.SideModeClientCache;
import zcylas.totality.client.gui.tab.GuiTab;

import java.util.Map;

public class SidedOverlayRenderer {

    private static final float INSET = -0.002f;
    private static final float MIN = 0.0f + INSET;
    private static final float MAX = 1.0f - INSET;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("totality", "pipeline/side_overlay"))
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(
                            CompareOp.LESS_THAN_OR_EQUAL, false, -4.0f, -100.0f))
                    .build()
    );

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static MappableRingBuffer vertexBuffer;

    public static void register() {
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(context -> {
            Vec3 camera = context.levelState().cameraRenderState.pos;
            PoseStack matrices = context.poseStack();

            // ── Energy overlay ────────────────────────────────────────────────
            for (BlockPos pos : SideModeClientCache.getAllPositions()) {
                if (!GuiTab.isPinned(pos, "energy")) continue;

                Map<Direction, SimpleSidedUEContainer.SideMode> modes = SideModeClientCache.getAll(pos);
                boolean anyNonNone = modes.values().stream()
                        .anyMatch(m -> m != SimpleSidedUEContainer.SideMode.NONE);
                if (!anyNonNone) continue;

                BufferBuilder buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());

                matrices.pushPose();
                matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

                for (Direction dir : Direction.values()) {
                    SimpleSidedUEContainer.SideMode mode = modes.getOrDefault(dir, SimpleSidedUEContainer.SideMode.NONE);
                    if (mode == SimpleSidedUEContainer.SideMode.NONE) continue;
                    int color = mode.getColor();
                    float r = ((color >> 16) & 0xFF) / 255f;
                    float g = ((color >> 8) & 0xFF) / 255f;
                    float b = (color & 0xFF) / 255f;
                    renderFace(matrices.last().pose(), buffer, dir, r, g, b, 0.6f);
                }

                matrices.popPose();

                MeshData built = buffer.build();
                if (built != null) draw(Minecraft.getInstance(), built);
            }

            // ── Item overlay ──────────────────────────────────────────────────
            for (BlockPos pos : ItemSideModeClientCache.getAllPositions()) {
                if (!GuiTab.isPinned(pos, "items")) continue;

                Map<Direction, ItemSideMode> modes = ItemSideModeClientCache.getAll(pos);
                boolean anyNonNone = modes.values().stream()
                        .anyMatch(m -> m != ItemSideMode.NONE);
                if (!anyNonNone) continue;

                BufferBuilder buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());

                matrices.pushPose();
                matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

                for (Direction dir : Direction.values()) {
                    ItemSideMode mode = modes.getOrDefault(dir, ItemSideMode.NONE);
                    if (mode == ItemSideMode.NONE) continue;
                    int color = mode.getColor();
                    float r = ((color >> 16) & 0xFF) / 255f;
                    float g = ((color >> 8) & 0xFF) / 255f;
                    float b = (color & 0xFF) / 255f;
                    renderFace(matrices.last().pose(), buffer, dir, r, g, b, 0.6f);
                }

                matrices.popPose();

                MeshData built = buffer.build();
                if (built != null) draw(Minecraft.getInstance(), built);
            }
        });
    }

    private static void draw(Minecraft client, MeshData builtBuffer) {
        MeshData.DrawState drawParameters = builtBuffer.drawState();
        VertexFormat format = drawParameters.format();

        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> "totality side overlay", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }

        GpuBuffer vertices = vertexBuffer.currentBuffer();
        GpuBuffer indices;
        VertexFormat.IndexType indexType;

        if (PIPELINE.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
            builtBuffer.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
            indices = PIPELINE.getVertexFormat().uploadImmediateIndexBuffer(builtBuffer.indexBuffer());
            indexType = builtBuffer.drawState().indexType();
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(PIPELINE.getVertexFormatMode());
            indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(),
                        COLOR_MODULATOR,
                        MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "totality side overlay rendering",
                        client.getMainRenderTarget().getColorTextureView(),
                        java.util.OptionalInt.empty(),
                        client.getMainRenderTarget().getDepthTextureView(),
                        java.util.OptionalDouble.empty())) {
            renderPass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);
            renderPass.drawIndexed(0 / format.getVertexSize(), 0, drawParameters.indexCount(), 1);
        }

        builtBuffer.close();
        vertexBuffer.rotate();
    }

    private static void renderFace(Matrix4fc matrix, BufferBuilder buffer,
                                   Direction dir, float r, float g, float b, float a) {
        switch (dir) {
            case UP ->    quad(matrix, buffer, MIN,MAX,MIN, MIN,MAX,MAX, MAX,MAX,MAX, MAX,MAX,MIN, r,g,b,a);
            case DOWN ->  quad(matrix, buffer, MIN,MIN,MAX, MIN,MIN,MIN, MAX,MIN,MIN, MAX,MIN,MAX, r,g,b,a);
            case NORTH -> quad(matrix, buffer, MAX,MAX,MIN, MAX,MIN,MIN, MIN,MIN,MIN, MIN,MAX,MIN, r,g,b,a);
            case SOUTH -> quad(matrix, buffer, MIN,MAX,MAX, MIN,MIN,MAX, MAX,MIN,MAX, MAX,MAX,MAX, r,g,b,a);
            case WEST ->  quad(matrix, buffer, MIN,MAX,MIN, MIN,MIN,MIN, MIN,MIN,MAX, MIN,MAX,MAX, r,g,b,a);
            case EAST ->  quad(matrix, buffer, MAX,MAX,MAX, MAX,MIN,MAX, MAX,MIN,MIN, MAX,MAX,MIN, r,g,b,a);
        }
    }

    private static void quad(Matrix4fc m, BufferBuilder b,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float r, float g, float bl, float a) {
        b.addVertex(m, x0, y0, z0).setColor(r, g, bl, a);
        b.addVertex(m, x1, y1, z1).setColor(r, g, bl, a);
        b.addVertex(m, x2, y2, z2).setColor(r, g, bl, a);
        b.addVertex(m, x3, y3, z3).setColor(r, g, bl, a);
    }

    public static void close() {
        ALLOCATOR.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}
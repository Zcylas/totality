package zcylas.totality.client.renderer.energy;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
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
import zcylas.totality.api.industrial.energy.base.SimpleSidedUEContainer;
import zcylas.totality.api.industrial.item.ItemSideMode;
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
            Matrix4f viewMatrix = context.levelState().cameraRenderState.viewRotationMatrix;

            // Each loop below batches every pinned position into a single BufferBuilder/draw call
            // rather than one draw per position: MappableRingBuffer only has 3 slots, and each
            // slot's fence (created by rotate()) is awaited by the next currentBuffer() call on
            // that same slot. Four or more pinned overlays visible in one frame would wrap back to
            // a slot whose fence was created moments earlier in this same not-yet-submitted frame —
            // MC 26.2's GL fence throws IllegalStateException("Cannot wait on a fence for the
            // current submit") instead of the older backend's lenient wait.

            // ── Energy overlay ────────────────────────────────────────────────
            BufferBuilder energyBuffer = new BufferBuilder(ALLOCATOR, PIPELINE.getPrimitiveTopology(), PIPELINE.getVertexFormatBinding(0));
            boolean anyEnergyFace = false;
            for (BlockPos pos : SideModeClientCache.getAllPositions()) {
                if (!GuiTab.isPinned(pos, "energy")) continue;

                Map<Direction, SimpleSidedUEContainer.SideMode> modes = SideModeClientCache.getAll(pos);
                boolean anyNonNone = modes.values().stream()
                        .anyMatch(m -> m != SimpleSidedUEContainer.SideMode.NONE);
                if (!anyNonNone) continue;

                matrices.pushPose();
                matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

                for (Direction dir : Direction.values()) {
                    SimpleSidedUEContainer.SideMode mode = modes.getOrDefault(dir, SimpleSidedUEContainer.SideMode.NONE);
                    if (mode == SimpleSidedUEContainer.SideMode.NONE) continue;
                    int color = mode.getColor();
                    float r = ((color >> 16) & 0xFF) / 255f;
                    float g = ((color >> 8) & 0xFF) / 255f;
                    float b = (color & 0xFF) / 255f;
                    renderFace(matrices.last().pose(), energyBuffer, dir, r, g, b, 0.6f);
                    anyEnergyFace = true;
                }

                matrices.popPose();
            }
            if (anyEnergyFace) {
                MeshData built = energyBuffer.build();
                if (built != null) draw(Minecraft.getInstance(), built, viewMatrix);
            }

            // ── Item overlay ──────────────────────────────────────────────────
            BufferBuilder itemBuffer = new BufferBuilder(ALLOCATOR, PIPELINE.getPrimitiveTopology(), PIPELINE.getVertexFormatBinding(0));
            boolean anyItemFace = false;
            for (BlockPos pos : ItemSideModeClientCache.getAllPositions()) {
                if (!GuiTab.isPinned(pos, "items")) continue;

                Map<Direction, ItemSideMode> modes = ItemSideModeClientCache.getAll(pos);
                boolean anyNonNone = modes.values().stream()
                        .anyMatch(m -> m != ItemSideMode.NONE);
                if (!anyNonNone) continue;

                matrices.pushPose();
                matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);

                for (Direction dir : Direction.values()) {
                    ItemSideMode mode = modes.getOrDefault(dir, ItemSideMode.NONE);
                    if (mode == ItemSideMode.NONE) continue;
                    int color = mode.getColor();
                    float r = ((color >> 16) & 0xFF) / 255f;
                    float g = ((color >> 8) & 0xFF) / 255f;
                    float b = (color & 0xFF) / 255f;
                    renderFace(matrices.last().pose(), itemBuffer, dir, r, g, b, 0.6f);
                    anyItemFace = true;
                }

                matrices.popPose();
            }
            if (anyItemFace) {
                MeshData built = itemBuffer.build();
                if (built != null) draw(Minecraft.getInstance(), built, viewMatrix);
            }
        });
    }

    private static void draw(Minecraft client, MeshData builtBuffer, Matrix4f viewMatrix) {
        MeshData.DrawState drawParameters = builtBuffer.drawState();
        VertexFormat format = drawParameters.format();

        int vertexBufferSize = drawParameters.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> "totality side overlay", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }

        try (GpuBufferSlice.MappedView mappedView = vertexBuffer.currentBuffer()
                .slice(0, builtBuffer.vertexBuffer().remaining())
                .map(false, true)) {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }

        GpuBuffer vertices = vertexBuffer.currentBuffer();
        GpuBuffer indices;
        com.mojang.blaze3d.IndexType indexType;
        boolean ownsIndices = false;

        if (PIPELINE.getPrimitiveTopology() == PrimitiveTopology.QUADS) {
            builtBuffer.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
            indices = RenderSystem.getDevice().createBuffer(
                    () -> "totality side overlay indices", GpuBuffer.USAGE_INDEX, builtBuffer.indexBuffer());
            indexType = builtBuffer.drawState().indexType();
            ownsIndices = true;
        } else {
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(PIPELINE.getPrimitiveTopology());
            indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
            indexType = shapeIndexBuffer.type();
        }

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(viewMatrix,
                        COLOR_MODULATOR,
                        MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(() -> "totality side overlay rendering",
                        client.gameRenderer.mainRenderTarget().getColorTextureView(),
                        java.util.Optional.empty(),
                        client.gameRenderer.mainRenderTarget().getDepthTextureView(),
                        java.util.OptionalDouble.empty())) {
            renderPass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, vertices.slice());
            renderPass.setIndexBuffer(indices, indexType);
            renderPass.drawIndexed(drawParameters.indexCount(), 1, 0, 0, 0);
        }

        builtBuffer.close();
        if (ownsIndices) indices.close();
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
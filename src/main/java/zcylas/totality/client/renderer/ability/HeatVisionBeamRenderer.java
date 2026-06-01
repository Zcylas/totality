// client/renderer/ability/HeatVisionBeamRenderer.java
package zcylas.totality.client.renderer.ability;

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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import zcylas.totality.networking.ability.ClientAbilityManager;

public final class HeatVisionBeamRenderer {

    private static final Identifier HEAT_VISION_ID =
            Identifier.fromNamespaceAndPath("totality", "heat_vision");

    private static final float RANGE = 20.0f;

    // ── Beam box sizes (half-width of square cross-section) ───────────────────
    private static final float CORE_SIZE  = 0.006f;
    private static final float MID_SIZE   = 0.015f;
    private static final float OUTER_SIZE = 0.030f;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final float CORE_R  = 1.0f, CORE_G  = 0.9f, CORE_B  = 0.8f, CORE_A  = 1.0f;
    private static final float MID_R   = 1.0f, MID_G   = 0.2f, MID_B   = 0.0f, MID_A   = 0.5f;
    private static final float OUTER_R = 0.6f, OUTER_G = 0.0f, OUTER_B = 0.0f, OUTER_A = 0.2f;

    // ── Pipeline ──────────────────────────────────────────────────────────────
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("totality", "pipeline/heat_vision_beam"))
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(
                            CompareOp.LESS_THAN_OR_EQUAL, false, -4.0f, -100.0f))
                    .build()
    );

    private static final ByteBufferBuilder ALLOCATOR =
            new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET    = new Vector3f();
    private static final Matrix4f  TEXTURE_MATRIX = new Matrix4f();
    private static MappableRingBuffer vertexBuffer;

    private HeatVisionBeamRenderer() {}

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register() {
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            if (!ClientAbilityManager.isChanneling(HEAT_VISION_ID)) return;

            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

            Vec3 eyePos  = mc.player.getEyePosition(partialTick);
            Vec3 lookDir = mc.player.getViewVector(partialTick);
            Vec3 endPos  = eyePos.add(lookDir.scale(RANGE));
            Vec3 hitPos  = clientRaycast(mc, eyePos, endPos);
            Vec3 camera  = context.levelState().cameraRenderState.pos;

            // ── Key fix: use viewRotationMatrix instead of RenderSystem.getModelViewMatrix() ──
            // viewRotationMatrix is the pure camera rotation WITHOUT head bob.
            // RenderSystem.getModelViewMatrix() includes the head bob rotation,
            // which causes the beam to swing when walking.
            Matrix4f viewMatrix = context.levelState().cameraRenderState.viewRotationMatrix;

            // Right vector from player rotation — stable, not affected by camera bob
            Vec3 worldUp = new Vec3(0, 1, 0);
            Vec3 right = lookDir.cross(worldUp).normalize();
            if (right.lengthSqr() < 1e-6) {
                right = new Vec3(1, 0, 0);
            }

            // Up vector — perpendicular to beam and right
            Vec3 beamDir = hitPos.subtract(eyePos).normalize();
            Vec3 up = right.cross(beamDir).normalize();

            // Push render start forward so beam is visible in first person
            float eyeSep = 0.06f;
            Vec3 renderStart = camera.add(lookDir.scale(0.5));
            Vec3 leftStart   = renderStart.subtract(right.scale(eyeSep));
            Vec3 rightStart  = renderStart.add(right.scale(eyeSep));

            PoseStack matrices = context.poseStack();
            renderBeamLayers(matrices, camera, leftStart,  hitPos, right, up, viewMatrix);
            renderBeamLayers(matrices, camera, rightStart, hitPos, right, up, viewMatrix);
        });
    }

    // ── Beam layers ───────────────────────────────────────────────────────────

    private static void renderBeamLayers(PoseStack matrices, Vec3 camera,
                                         Vec3 start, Vec3 end,
                                         Vec3 right, Vec3 up,
                                         Matrix4f viewMatrix) {
        renderBox(matrices, camera, start, end, right, up,
                OUTER_SIZE, OUTER_R, OUTER_G, OUTER_B, OUTER_A, viewMatrix);
        renderBox(matrices, camera, start, end, right, up,
                MID_SIZE,   MID_R,   MID_G,   MID_B,   MID_A,   viewMatrix);
        renderBox(matrices, camera, start, end, right, up,
                CORE_SIZE,  CORE_R,  CORE_G,  CORE_B,  CORE_A,  viewMatrix);
    }

    // ── Box renderer ──────────────────────────────────────────────────────────

    private static void renderBox(PoseStack matrices, Vec3 camera,
                                  Vec3 start, Vec3 end,
                                  Vec3 right, Vec3 up,
                                  float half,
                                  float r, float g, float b, float a,
                                  Matrix4f viewMatrix) {
        BufferBuilder buffer = new BufferBuilder(
                ALLOCATOR, PIPELINE.getVertexFormatMode(), PIPELINE.getVertexFormat());

        matrices.pushPose();
        Matrix4fc pose = new Matrix4f(); // identity — no bob

        // Camera-relative positions
        float sx = (float)(start.x - camera.x);
        float sy = (float)(start.y - camera.y);
        float sz = (float)(start.z - camera.z);
        float ex = (float)(end.x - camera.x);
        float ey = (float)(end.y - camera.y);
        float ez = (float)(end.z - camera.z);

        float rx = (float)(right.x * half), ry = (float)(right.y * half), rz = (float)(right.z * half);
        float ux = (float)(up.x    * half), uy = (float)(up.y    * half), uz = (float)(up.z    * half);

        // 8 corners of the box prism
        float s_tr_x = sx+rx+ux, s_tr_y = sy+ry+uy, s_tr_z = sz+rz+uz;
        float s_tl_x = sx-rx+ux, s_tl_y = sy-ry+uy, s_tl_z = sz-rz+uz;
        float s_bl_x = sx-rx-ux, s_bl_y = sy-ry-uy, s_bl_z = sz-rz-uz;
        float s_br_x = sx+rx-ux, s_br_y = sy+ry-uy, s_br_z = sz+rz-uz;
        float e_tr_x = ex+rx+ux, e_tr_y = ey+ry+uy, e_tr_z = ez+rz+uz;
        float e_tl_x = ex-rx+ux, e_tl_y = ey-ry+uy, e_tl_z = ez-rz+uz;
        float e_bl_x = ex-rx-ux, e_bl_y = ey-ry-uy, e_bl_z = ez-rz-uz;
        float e_br_x = ex+rx-ux, e_br_y = ey+ry-uy, e_br_z = ez+rz-uz;

        // 6 faces
        quad(buffer, pose, s_tr_x,s_tr_y,s_tr_z, s_tl_x,s_tl_y,s_tl_z, e_tl_x,e_tl_y,e_tl_z, e_tr_x,e_tr_y,e_tr_z, r,g,b,a);
        quad(buffer, pose, s_br_x,s_br_y,s_br_z, e_br_x,e_br_y,e_br_z, e_bl_x,e_bl_y,e_bl_z, s_bl_x,s_bl_y,s_bl_z, r,g,b,a);
        quad(buffer, pose, s_tr_x,s_tr_y,s_tr_z, e_tr_x,e_tr_y,e_tr_z, e_br_x,e_br_y,e_br_z, s_br_x,s_br_y,s_br_z, r,g,b,a);
        quad(buffer, pose, s_tl_x,s_tl_y,s_tl_z, s_bl_x,s_bl_y,s_bl_z, e_bl_x,e_bl_y,e_bl_z, e_tl_x,e_tl_y,e_tl_z, r,g,b,a);
        quad(buffer, pose, s_tl_x,s_tl_y,s_tl_z, s_tr_x,s_tr_y,s_tr_z, s_br_x,s_br_y,s_br_z, s_bl_x,s_bl_y,s_bl_z, r,g,b,a);
        quad(buffer, pose, e_tr_x,e_tr_y,e_tr_z, e_tl_x,e_tl_y,e_tl_z, e_bl_x,e_bl_y,e_bl_z, e_br_x,e_br_y,e_br_z, r,g,b,a);

        matrices.popPose();

        MeshData built = buffer.build();
        if (built != null) draw(Minecraft.getInstance(), built, viewMatrix);
    }

    private static void quad(BufferBuilder buf, Matrix4fc pose,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        buf.addVertex(pose, x0, y0, z0).setColor(r, g, b, a);
        buf.addVertex(pose, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(pose, x2, y2, z2).setColor(r, g, b, a);
        buf.addVertex(pose, x3, y3, z3).setColor(r, g, b, a);
    }

    // ── Client raycast ────────────────────────────────────────────────────────

    private static Vec3 clientRaycast(Minecraft mc, Vec3 start, Vec3 end) {
        AABB searchBox = mc.player.getBoundingBox()
                .expandTowards(mc.player.getLookAngle().scale(RANGE))
                .inflate(1.0);

        Vec3 closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity entity : mc.level.getEntities(mc.player, searchBox,
                e -> e != mc.player && e.isAlive())) {
            AABB box = entity.getBoundingBox().inflate(0.1);
            var result = box.clip(start, end);
            if (result.isPresent()) {
                double dist = start.distanceTo(result.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = result.get();
                }
            }
        }

        if (closest != null) return closest;

        BlockHitResult blockHit = mc.level.clip(new ClipContext(
                start, end,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        if (blockHit.getType() != HitResult.Type.MISS) {
            return blockHit.getLocation();
        }

        return end;
    }

    // ── GPU draw ──────────────────────────────────────────────────────────────

    /**
     * Draws the mesh using the provided transform matrix.
     *
     * Using viewRotationMatrix (no head bob) instead of RenderSystem.getModelViewMatrix()
     * (which includes head bob) fixes the beam swinging when walking.
     */
    @SuppressWarnings("resource")
    private static void draw(Minecraft mc, MeshData built, Matrix4f viewMatrix) {
        MeshData.DrawState drawState = built.drawState();
        VertexFormat format = drawState.format();

        int size = drawState.vertexCount() * format.getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < size) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(
                    () -> "totality heat vision beam",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                    size);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView view = encoder.mapBuffer(
                vertexBuffer.currentBuffer().slice(0, built.vertexBuffer().remaining()),
                false, true)) {
            MemoryUtil.memCopy(built.vertexBuffer(), view.data());
        }

        GpuBuffer vertices = vertexBuffer.currentBuffer();
        RenderSystem.AutoStorageIndexBuffer indexBuffer =
                RenderSystem.getSequentialBuffer(PIPELINE.getVertexFormatMode());
        GpuBuffer indices = indexBuffer.getBuffer(drawState.indexCount());

        // Use viewRotationMatrix — pure camera rotation without head bob
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(viewMatrix, COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "totality heat vision beam rendering",
                        mc.getMainRenderTarget().getColorTextureView(),
                        java.util.OptionalInt.empty(),
                        mc.getMainRenderTarget().getDepthTextureView(),
                        java.util.OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices, indexBuffer.type());
            pass.drawIndexed(0, 0, drawState.indexCount(), 1);
        }

        built.close();
        vertexBuffer.rotate();
    }

    public static void close() {
        ALLOCATOR.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}
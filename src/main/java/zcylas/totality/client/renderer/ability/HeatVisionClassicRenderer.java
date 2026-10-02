package zcylas.totality.client.renderer.ability;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.List;

/**
 * The pre-V2 Heat Vision renderer, kept <b>for development A/B comparison only</b> (VFX Experiment 2): it is used only
 * when {@code /totalityvfx heatvision classic on} is set in a development client. Pipeline (location, depth state) and
 * geometry are those of the original renderer: three nested boxes per eye, flat colours, starting half a block in front
 * of the camera, depth test {@code LESS_THAN_OR_EQUAL} with bias (-4, -100).
 */
final class HeatVisionClassicRenderer {

    private static final float CORE_SIZE = 0.006f;
    private static final float MID_SIZE = 0.015f;
    private static final float OUTER_SIZE = 0.030f;
    private static final float CORE_R = 1.0f, CORE_G = 0.9f, CORE_B = 0.8f, CORE_A = 1.0f;
    private static final float MID_R = 1.0f, MID_G = 0.2f, MID_B = 0.0f, MID_A = 0.5f;
    private static final float OUTER_R = 0.6f, OUTER_G = 0.0f, OUTER_B = 0.0f, OUTER_A = 0.2f;
    private static final float RANGE = 20.0f;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("totality", "pipeline/heat_vision_beam"))
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(
                            CompareOp.LESS_THAN_OR_EQUAL, false, -4.0f, -100.0f))
                    .build()
    );

    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static MappableRingBuffer vertexBuffer;

    private HeatVisionClassicRenderer() {}

    /** Draws the local player's beam the original way (if {@code playerBeam}) plus {@code testBeams}; returns draws. */
    static int draw(Minecraft mc, List<HeatVisionBeam> testBeams, CameraRenderState camera, boolean playerBeam) {
        BufferBuilder buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getPrimitiveTopology(), PIPELINE.getVertexFormatBinding(0));
        Vec3 cam = camera.pos;
        if (playerBeam && mc.player != null) {
            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            Vec3 eyePos = mc.player.getEyePosition(partialTick);
            Vec3 lookDir = mc.player.getViewVector(partialTick);
            Vec3 hitPos = HeatVisionBeamRenderer.raycastOrEnd(mc, eyePos, eyePos.add(lookDir.scale(RANGE)));
            Vec3 right = lookDir.cross(new Vec3(0, 1, 0)).normalize();
            if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
            Vec3 up = right.cross(hitPos.subtract(eyePos).normalize()).normalize();
            Vec3 renderStart = cam.add(lookDir.scale(0.5));
            layers(buffer, cam, renderStart.subtract(right.scale(0.06)), hitPos, right, up);
            layers(buffer, cam, renderStart.add(right.scale(0.06)), hitPos, right, up);
        }
        for (HeatVisionBeam beam : testBeams) {
            Vec3 dir = beam.end().subtract(beam.start()).normalize();
            Vec3 right = dir.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
            Vec3 up = right.cross(dir).normalize();
            layers(buffer, cam, beam.start(), beam.end(), right, up);
        }
        MeshData built = buffer.build();
        if (built == null) return 0;
        vertexBuffer = HeatVisionBeamRenderer.draw(mc, PIPELINE, built, vertexBuffer, "classic", camera.viewRotationMatrix);
        return 1;
    }

    private static void layers(BufferBuilder buffer, Vec3 camera, Vec3 start, Vec3 end, Vec3 right, Vec3 up) {
        box(buffer, camera, start, end, right, up, OUTER_SIZE, OUTER_R, OUTER_G, OUTER_B, OUTER_A);
        box(buffer, camera, start, end, right, up, MID_SIZE, MID_R, MID_G, MID_B, MID_A);
        box(buffer, camera, start, end, right, up, CORE_SIZE, CORE_R, CORE_G, CORE_B, CORE_A);
    }

    private static void box(BufferBuilder buffer, Vec3 camera, Vec3 start, Vec3 end, Vec3 right, Vec3 up,
                            float half, float r, float g, float b, float a) {
        Matrix4fc pose = new Matrix4f();
        float sx = (float) (start.x - camera.x), sy = (float) (start.y - camera.y), sz = (float) (start.z - camera.z);
        float ex = (float) (end.x - camera.x), ey = (float) (end.y - camera.y), ez = (float) (end.z - camera.z);
        float rx = (float) (right.x * half), ry = (float) (right.y * half), rz = (float) (right.z * half);
        float ux = (float) (up.x * half), uy = (float) (up.y * half), uz = (float) (up.z * half);
        float[][] s = {{sx + rx + ux, sy + ry + uy, sz + rz + uz}, {sx - rx + ux, sy - ry + uy, sz - rz + uz},
                {sx - rx - ux, sy - ry - uy, sz - rz - uz}, {sx + rx - ux, sy + ry - uy, sz + rz - uz}};
        float[][] e = {{ex + rx + ux, ey + ry + uy, ez + rz + uz}, {ex - rx + ux, ey - ry + uy, ez - rz + uz},
                {ex - rx - ux, ey - ry - uy, ez - rz - uz}, {ex + rx - ux, ey + ry - uy, ez + rz - uz}};
        // Same six faces, same winding as the original renderer (tr, tl, bl, br).
        quad(buffer, pose, s[0], s[1], e[1], e[0], r, g, b, a);
        quad(buffer, pose, s[3], e[3], e[2], s[2], r, g, b, a);
        quad(buffer, pose, s[0], e[0], e[3], s[3], r, g, b, a);
        quad(buffer, pose, s[1], s[2], e[2], e[1], r, g, b, a);
        quad(buffer, pose, s[1], s[0], s[3], s[2], r, g, b, a);
        quad(buffer, pose, e[0], e[1], e[2], e[3], r, g, b, a);
    }

    private static void quad(BufferBuilder buf, Matrix4fc pose, float[] p0, float[] p1, float[] p2, float[] p3,
                             float r, float g, float b, float a) {
        buf.addVertex(pose, p0[0], p0[1], p0[2]).setColor(r, g, b, a);
        buf.addVertex(pose, p1[0], p1[1], p1[2]).setColor(r, g, b, a);
        buf.addVertex(pose, p2[0], p2[1], p2[2]).setColor(r, g, b, a);
        buf.addVertex(pose, p3[0], p3[1], p3[2]).setColor(r, g, b, a);
    }

    static long vertexBufferBytes() {
        return vertexBuffer == null ? 0 : 3L * vertexBuffer.size();
    }

    static void close() {
        ALLOCATOR.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}

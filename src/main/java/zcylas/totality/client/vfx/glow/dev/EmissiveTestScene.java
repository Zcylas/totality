package zcylas.totality.client.vfx.glow.dev;

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
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;
import zcylas.totality.client.vfx.glow.EmissiveBuffer;
import zcylas.totality.client.vfx.glow.EmissiveGlow;
import zcylas.totality.client.vfx.glow.EmissiveSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Development-only test objects for the emissive glow layer: small coloured cubes and thin "beam" pillars placed in the
 * world. Each object is drawn twice:
 *
 * <ul>
 *   <li><b>normal rendering</b>: an ordinary solid, shaded object in the world pass (always, glow on or off);</li>
 *   <li><b>emissive contribution</b>: the same shape added to the glow layer through {@link EmissiveSource}.</li>
 * </ul>
 *
 * So "glow off" shows exactly the plain objects and "glow on" shows object + glow. Nothing is placed in the world: the
 * objects exist only on this client and disappear with {@link #clear()}.
 */
public final class EmissiveTestScene implements EmissiveSource {

    public static final EmissiveTestScene INSTANCE = new EmissiveTestScene();

    private static final float[][] PALETTE = {
            {0.25f, 0.85f, 1.00f}, // cyan
            {1.00f, 0.30f, 0.90f}, // magenta
            {1.00f, 0.55f, 0.15f}, // orange
            {0.35f, 1.00f, 0.45f}, // green
    };
    /** Face shading of the normal object (top, bottom, the two side pairs) so it reads as a solid. */
    private static final float[] SHADE = {1.0f, 0.5f, 0.8f, 0.8f, 0.65f, 0.65f};

    private static final RenderPipeline BASE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("totality", "pipeline/vfx_glow_test_object"))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
                    .withCull(false)
                    .build());

    private record TestObject(Vec3 pos, float[] color, boolean pillar) {
        float halfX() { return pillar ? 0.06f : 0.25f; }
        float halfY() { return pillar ? 1.5f : 0.25f; }
    }

    private final List<TestObject> objects = new ArrayList<>();
    private final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private MappableRingBuffer vertexBuffer;
    private boolean registered;

    private EmissiveTestScene() {}

    /** Hooks the normal-object rendering (once). */
    static void register() {
        if (INSTANCE.registered) return;
        INSTANCE.registered = true;
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(context -> INSTANCE.renderObjects(context.levelState().cameraRenderState));
    }

    /**
     * Places {@code count} objects in front of {@code eye} along {@code forward}: up to four in a row with a beam pillar
     * in the middle, more as a grid (cubes only), for the multi-source measurements.
     */
    public void place(Vec3 eye, Vec3 forward, int count, boolean pillar) {
        clear();
        Vec3 flat = new Vec3(forward.x, 0, forward.z);
        flat = flat.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0, flat.x);
        if (count <= 4) {
            Vec3 centre = eye.add(flat.scale(4.0)).add(0, -0.3, 0);
            for (int i = 0; i < count; i++) {
                double offset = (i - (count - 1) / 2.0) * 1.3;
                objects.add(new TestObject(centre.add(right.scale(offset)), PALETTE[i % PALETTE.length], false));
            }
            if (pillar) objects.add(new TestObject(centre.add(flat.scale(1.5)).add(0, 0.6, 0), PALETTE[0], true));
        } else {
            int side = (int) Math.ceil(Math.sqrt(count));
            Vec3 centre = eye.add(flat.scale(7.0));
            for (int i = 0; i < count; i++) {
                double x = (i % side - (side - 1) / 2.0) * 0.7;
                double y = ((side - 1) / 2.0 - i / side) * 0.7;
                objects.add(new TestObject(centre.add(right.scale(x)).add(0, y, 0), PALETTE[i % PALETTE.length], false));
            }
        }
        EmissiveGlow.addSource(this);
    }

    public void clear() {
        objects.clear();
        EmissiveGlow.removeSource(this);
    }

    public int count() {
        return objects.size();
    }

    // ── Emissive contribution ─────────────────────────────────────────────────

    @Override
    public void emit(EmissiveBuffer buffer, Vec3 camera, float partialTick) {
        for (TestObject o : objects) {
            float[] c = o.color();
            buffer.box((float) (o.pos().x - camera.x), (float) (o.pos().y - camera.y), (float) (o.pos().z - camera.z),
                    o.halfX(), o.halfY(), o.halfX(), c[0], c[1], c[2], 1.0f);
        }
    }

    // ── Normal rendering ──────────────────────────────────────────────────────

    private void renderObjects(CameraRenderState camera) {
        if (objects.isEmpty()) return;
        BufferBuilder builder = new BufferBuilder(allocator, BASE_PIPELINE.getPrimitiveTopology(), BASE_PIPELINE.getVertexFormatBinding(0));
        Matrix4fc identity = new Matrix4f();
        for (TestObject o : objects) {
            float x = (float) (o.pos().x - camera.pos.x), y = (float) (o.pos().y - camera.pos.y), z = (float) (o.pos().z - camera.pos.z);
            shadedBox(builder, identity, x, y, z, o.halfX(), o.halfY(), o.color());
        }
        MeshData mesh = builder.build();
        if (mesh == null) return;
        draw(mesh, camera.viewRotationMatrix);
    }

    private static void shadedBox(BufferBuilder b, Matrix4fc pose, float x, float y, float z, float h, float hy, float[] c) {
        float x0 = x - h, x1 = x + h, y0 = y - hy, y1 = y + hy, z0 = z - h, z1 = z + h;
        float[][][] faces = {
                {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}},
                {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
                {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}},
                {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}},
                {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}},
        };
        for (int f = 0; f < faces.length; f++) {
            float s = SHADE[f] * 0.85f;
            for (float[] v : faces[f]) b.addVertex(pose, v[0], v[1], v[2]).setColor(c[0] * s, c[1] * s, c[2] * s, 1.0f);
        }
    }

    private void draw(MeshData mesh, Matrix4f view) {
        Minecraft mc = Minecraft.getInstance();
        MeshData.DrawState state = mesh.drawState();
        int size = state.vertexCount() * state.format().getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < size) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> "Totality glow test objects",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, Math.max(size, 4096));
        }
        try (GpuBufferSlice.MappedView mapped = vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()).map(false, true)) {
            MemoryUtil.memCopy(mesh.vertexBuffer(), mapped.data());
        }
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(BASE_PIPELINE.getPrimitiveTopology());
        GpuBuffer indexBuffer = indices.getBuffer(state.indexCount());
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(view);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Totality glow test objects",
                mc.gameRenderer.mainRenderTarget().getColorTextureView(), Optional.empty(),
                mc.gameRenderer.mainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(BASE_PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertexBuffer.currentBuffer().slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(state.indexCount(), 1, 0, 0, 0);
        }
        mesh.close();
        vertexBuffer.rotate();
    }
}

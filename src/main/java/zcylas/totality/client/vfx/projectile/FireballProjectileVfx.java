package zcylas.totality.client.vfx.projectile;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.TimerQuery;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import zcylas.totality.Totality;
import zcylas.totality.client.vfx.glow.EmissiveBuffer;
import zcylas.totality.client.vfx.glow.EmissiveGlow;
import zcylas.totality.client.vfx.glow.EmissiveSource;
import zcylas.totality.client.vfx.ribbon.RibbonGeometry;
import zcylas.totality.entity.magic.FireballProjectileEntity;
import zcylas.totality.entity.magic.FireballVfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Fireball V2's projectile (VFX Experiment 3, B3): <i>a small, intensely hot bead with a short, energetic fire
 * streak</i>, client presentation of the unchanged {@link FireballProjectileEntity} (1.2 blocks a tick, same collision).
 *
 * <ul>
 *   <li><b>Bead</b>: a camera-facing quad shaded by {@code core/vfx_fireball_projectile}: a white-hot core inside a
 *       flickering gold-orange halo, with a minimum and maximum size on screen.</li>
 *   <li><b>Streak</b>: up to {@link FireballProjectileTrack#STREAK_LENGTH} blocks behind the bead, a tapering
 *       camera-facing ribbon (the shared {@link RibbonGeometry}, Heat Vision V2's ribbon generalised with a width
 *       profile): white core, gold, orange, red, energy streaming backwards, the tail breaking into tongues. Unlike
 *       Heat Vision's continuous twin beams it is short, tapering and travels with the bead.</li>
 *   <li><b>Casting to flight</b>: a fireball whose cast this client saw grows out of the cast point (hidden for the
 *       first {@link FireballProjectileTrack#SHOW_AFTER} blocks, full size after {@link FireballProjectileTrack#GROW}
 *       more, the streak never reaching back past the cast point), its bead flaring white-gold as it emerges: the cast
 *       flash. One that came into tracking range mid-flight is drawn complete at once, without the flare.</li>
 *   <li><b>Impact</b>: when the projectile is removed, its streak collapses into the last bead position over
 *       {@link FireballProjectileTrack#IMPACT_SECONDS} while the bead flares and fades, bridging into the explosion's
 *       ignition.</li>
 *   <li><b>Glow</b>: the bead's core and the first metre of the streak feed the Emissive Rendering Layer (budget group
 *       {@code fireball}, see {@code FireballV2}).</li>
 * </ul>
 *
 * Every projectile of a frame is drawn in one draw: premultiplied alpha, reversed-Z depth test
 * ({@code GREATER_THAN_OR_EQUAL}), no depth writes, at {@code BEFORE_TRANSLUCENT_TERRAIN} like the explosion.
 * Development only: {@link FireballVfx#setLegacyProjectile} hands the projectile back to the V1 entity renderer.
 */
public final class FireballProjectileVfx {

    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "pipeline/vfx_fireball_projectile"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withVertexShader(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "core/vfx_fire"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "core/vfx_fireball_projectile"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build());

    static final double STREAK_HALF_WIDTH = 0.17;
    static final int STREAK_SEGMENTS = 10;
    static final double BEAD_HALF_SIZE = 0.42;
    static final RibbonGeometry.Limits LIMITS = new RibbonGeometry.Limits(0.0025, 0.03, 0.6, 2.0);
    static final double BEAD_MIN_HALF_ANGLE = 0.006;
    static final double BEAD_MAX_HALF_ANGLE = 0.06;
    static final RibbonGeometry.Profile TAPER = t -> STREAK_HALF_WIDTH * (0.15 + 0.85 * Math.pow(1.0 - t, 0.7));
    /** Emissive brightness of one projectile at full strength, and its share of the budget (a quarter of an explosion). */
    static final float[] GLOW_BEAD = {1.0f, 0.78f, 0.42f};
    static final float[] GLOW_STREAK = {1.0f, 0.42f, 0.1f};
    static final float DEMAND = 0.25f;
    static final double GLOW_MAX_HALF_ANGLE = 0.015;

    private static final Map<Integer, FireballProjectileTrack> TRACKS = new HashMap<>();
    private static final List<FireballProjectileTrack> FRAME = new ArrayList<>();
    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Glow GLOW = new Glow();
    private static MappableRingBuffer vertices;
    private static long frameId;

    // ── Development measurement ───────────────────────────────────────────────
    private static TimerQuery timer;
    private static boolean timing;
    private static boolean awaitingTimer;
    private static long gpuSum;
    private static int gpuSamples;
    private static long cpuSum;
    private static int cpuSamples;
    private static int lastProjectiles;
    private static int lastQuads;
    private static int lastDraws;
    private static int peakProjectiles;

    private FireballProjectileVfx() {}

    public static void register() {
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(context -> render(context.levelState().cameraRenderState));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
    }

    /** Game seconds (game time + partial tick), unaffected by the explosion's development slow-motion clock. */
    private static double now(Minecraft mc) {
        return (mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20.0;
    }

    private static void render(CameraRenderState camera) {
        Minecraft mc = Minecraft.getInstance();
        if (timer != null && awaitingTimer && timer.getStatus() == TimerQuery.Status.NOT_RECORDING) {
            gpuSum += timer.get();
            gpuSamples++;
            awaitingTimer = false;
        }
        lastDraws = 0;
        lastQuads = 0;
        if (mc.level == null || FireballVfx.legacyProjectile()) {
            TRACKS.clear();
            finish();
            return;
        }
        long cpuStart = System.nanoTime();
        double now = now(mc);
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        frameId++;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof FireballProjectileEntity f) || f.isRemoved()) continue;
            FireballProjectileTrack t = TRACKS.computeIfAbsent(f.getId(), id -> new FireballProjectileTrack());
            t.entity = f;
            t.pos = f.getPosition(partialTick);
            Vec3 v = f.getDeltaMovement();
            if (v.lengthSqr() > 1.0e-8) t.dir = v.normalize();
            t.castSeen = f.castSeen();
            Vec3 origin = f.visualOrigin();
            t.traveled = origin == null ? 0.0 : t.castSeen ? t.pos.distanceTo(origin) : Double.MAX_VALUE;
            t.seed = (f.getId() * 0.6180339f) % 1.0f;
            t.frame = frameId;
        }
        FRAME.clear();
        for (Iterator<FireballProjectileTrack> it = TRACKS.values().iterator(); it.hasNext(); ) {
            FireballProjectileTrack t = it.next();
            if (t.frame != frameId && Double.isNaN(t.removedAt)) {
                // Removed (hit, fizzle, out of range): collapse into where it ended.
                t.removedAt = now;
                Vec3 end = t.entity.position();
                if (end.distanceTo(t.pos) < 2.5) t.pos = end;
            }
            if (!Double.isNaN(t.removedAt) && now - t.removedAt >= FireballProjectileTrack.IMPACT_SECONDS) {
                it.remove();
                continue;
            }
            if (t.evaluate(now)) FRAME.add(t);
        }
        lastProjectiles = FRAME.size();
        peakProjectiles = Math.max(peakProjectiles, lastProjectiles);
        if (FRAME.isEmpty()) {
            finish();
            return;
        }
        Vec3 cam = camera.pos;
        FRAME.sort((a, b) -> Double.compare(b.pos.distanceToSqr(cam), a.pos.distanceToSqr(cam)));
        Vector3f right = camera.orientation.transform(new Vector3f(1, 0, 0));
        Vector3f up = camera.orientation.transform(new Vector3f(0, 1, 0));
        BufferBuilder buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getPrimitiveTopology(), PIPELINE.getVertexFormatBinding(0));
        RibbonGeometry.VertexSink sink = (x, y, z, u, vv, r, g, b, a) -> buffer.addVertex(x, y, z).setUv(u, vv).setColor(r, g, b, a);
        int[] quads = {0};
        RibbonGeometry.VertexSink counting = (x, y, z, u, vv, r, g, b, a) -> {
            sink.vertex(x, y, z, u, vv, r, g, b, a);
            quads[0]++;
        };
        for (FireballProjectileTrack t : FRAME) {
            if (t.streak > 0.05) {
                RibbonGeometry.ribbon(t.pos.subtract(t.dir.scale(0.05)), t.pos.subtract(t.dir.scale(t.streak)), cam, STREAK_SEGMENTS,
                        TAPER, LIMITS, 1.0, t.seed, (float) (t.streak / 4.0), 0.0f, t.strength, counting);
            }
            bead(t, cam, right, up, counting);
        }
        lastQuads = quads[0] / 4;
        MeshData mesh = buffer.build();
        cpuSum += System.nanoTime() - cpuStart;
        cpuSamples++;
        if (mesh != null) {
            boolean timed = timing && timer != null && !awaitingTimer && timer.getStatus() == TimerQuery.Status.NOT_RECORDING;
            if (timed) timer.beginProfile();
            draw(mc, mesh, camera.viewRotationMatrix);
            lastDraws = 1;
            if (timed) {
                timer.endProfile();
                awaitingTimer = true;
            }
        }
        GLOW.frame(right, up);
        EmissiveGlow.addSource(GLOW);
    }

    private static void finish() {
        lastProjectiles = 0;
        FRAME.clear();
        EmissiveGlow.removeSource(GLOW);
    }

    private static void bead(FireballProjectileTrack t, Vec3 cam, Vector3f right, Vector3f up, RibbonGeometry.VertexSink sink) {
        Vec3 rel = t.pos.subtract(cam);
        double dist = Math.max(rel.length(), 1.0e-4);
        double h = Math.min(Math.max(BEAD_HALF_SIZE * t.size, dist * BEAD_MIN_HALF_ANGLE), dist * BEAD_MAX_HALF_ANGLE);
        float a = t.strength * RibbonGeometry.nearFade(dist, LIMITS);
        if (a <= 0.003f) return;
        float x = (float) rel.x, y = (float) rel.y, z = (float) rel.z, hh = (float) h;
        float rx = right.x() * hh, ry = right.y() * hh, rz = right.z() * hh;
        float ux = up.x() * hh, uy = up.y() * hh, uz = up.z() * hh;
        sink.vertex(x - rx - ux, y - ry - uy, z - rz - uz, -1, -1, t.seed, t.flare, 1.0f, a);
        sink.vertex(x + rx - ux, y + ry - uy, z + rz - uz, 1, -1, t.seed, t.flare, 1.0f, a);
        sink.vertex(x + rx + ux, y + ry + uy, z + rz + uz, 1, 1, t.seed, t.flare, 1.0f, a);
        sink.vertex(x - rx + ux, y - ry + uy, z - rz + uz, -1, 1, t.seed, t.flare, 1.0f, a);
    }

    /** Uploads and draws once per frame (a ring-buffer slot must not be reused within a frame on 26.2). */
    @SuppressWarnings("resource")
    private static void draw(Minecraft mc, MeshData mesh, Matrix4f viewMatrix) {
        MeshData.DrawState drawState = mesh.drawState();
        VertexFormat format = drawState.format();
        int size = drawState.vertexCount() * format.getVertexSize();
        if (vertices == null || vertices.size() < size) {
            if (vertices != null) vertices.close();
            vertices = new MappableRingBuffer(() -> "totality fireball projectile", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                    Math.max(size, 16384));
        }
        try (GpuBufferSlice.MappedView view = vertices.currentBuffer().slice(0, mesh.vertexBuffer().remaining()).map(false, true)) {
            MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
        }
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(PIPELINE.getPrimitiveTopology());
        GpuBuffer indices = indexBuffer.getBuffer(drawState.indexCount());
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(viewMatrix);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "totality fireball projectile",
                mc.gameRenderer.mainRenderTarget().getColorTextureView(), Optional.empty(),
                mc.gameRenderer.mainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, vertices.currentBuffer().slice());
            pass.setIndexBuffer(indices, indexBuffer.type());
            pass.drawIndexed(drawState.indexCount(), 1, 0, 0, 0);
        }
        mesh.close();
        vertices.rotate();
    }

    /**
     * The projectiles' contribution to the Emissive Rendering Layer: each bead's hot core and the first metre of its
     * streak, at most {@link #GLOW_BEAD} / {@link #GLOW_STREAK} per projectile, in the {@code fireball} budget group.
     * Consumes the frame's projectiles once; unregistered when no projectile is drawn.
     */
    private static final class Glow implements EmissiveSource {
        private final Vector3f right = new Vector3f();
        private final Vector3f up = new Vector3f();
        private boolean pending;

        void frame(Vector3f r, Vector3f u) {
            right.set(r);
            up.set(u);
            pending = true;
        }

        @Override
        public float emissiveDemand() {
            float d = 0.0f;
            for (FireballProjectileTrack t : FRAME) d += DEMAND * t.strength;
            return d;
        }

        @Override
        public String budgetGroup() {
            return "fireball";
        }

        @Override
        public void emit(EmissiveBuffer buffer, Vec3 camera, float partialTick) {
            if (!pending) return;
            pending = false;
            for (FireballProjectileTrack t : FRAME) {
                Vec3 rel = t.pos.subtract(camera);
                double dist = rel.length();
                float fade = t.strength * RibbonGeometry.nearFade(dist, LIMITS);
                if (fade <= 0.003f) continue;
                // Limited on screen like the bead: right in front of the eyes it must not become a glowing square.
                float h = (float) Math.min(0.16 * t.size, dist * GLOW_MAX_HALF_ANGLE);
                float x = (float) rel.x, y = (float) rel.y, z = (float) rel.z;
                float rx = right.x() * h, ry = right.y() * h, rz = right.z() * h;
                float ux = up.x() * h, uy = up.y() * h, uz = up.z() * h;
                buffer.quad(x - rx - ux, y - ry - uy, z - rz - uz, x + rx - ux, y + ry - uy, z + rz - uz,
                        x + rx + ux, y + ry + uy, z + rz + uz, x - rx + ux, y - ry + uy, z - rz + uz,
                        GLOW_BEAD[0] * fade, GLOW_BEAD[1] * fade, GLOW_BEAD[2] * fade, 1.0f);
                double hot = Math.min(t.streak, 1.0);
                if (hot > 0.1) {
                    float[] q = new float[12];
                    int[] n = {0};
                    RibbonGeometry.ribbon(t.pos, t.pos.subtract(t.dir.scale(hot)), camera, 2, TAPER, LIMITS, 0.45,
                            1, 1, 1, t.strength, (vx, vy, vz, u, v, r, g, b, a) -> {
                                q[n[0] * 3] = vx;
                                q[n[0] * 3 + 1] = vy;
                                q[n[0] * 3 + 2] = vz;
                                if (++n[0] < 4) return;
                                n[0] = 0;
                                buffer.quad(q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], q[8], q[9], q[10], q[11],
                                        GLOW_STREAK[0] * a, GLOW_STREAK[1] * a, GLOW_STREAK[2] * a, 1.0f);
                            });
                }
            }
        }
    }

    // ── Development hooks and measurement ─────────────────────────────────────

    public static void setTiming(boolean on) {
        timing = on;
        if (on && timer == null) timer = new TimerQuery();
        resetMeasurements();
    }

    public static void resetMeasurements() {
        gpuSum = gpuSamples = 0;
        cpuSum = cpuSamples = 0;
        peakProjectiles = 0;
    }

    /** Average GPU time of the projectile draw since the last reset, in ms (-1 when nothing was measured). */
    public static double averageGpuMillis() {
        return gpuSamples == 0 ? -1 : gpuSum / 1.0e6 / gpuSamples;
    }

    public static double averageCpuMillis() {
        return cpuSamples == 0 ? -1 : cpuSum / 1.0e6 / cpuSamples;
    }

    public static int gpuSamples() {
        return gpuSamples;
    }

    public static int lastProjectiles() {
        return lastProjectiles;
    }

    public static int peakProjectiles() {
        return peakProjectiles;
    }

    public static int lastQuads() {
        return lastQuads;
    }

    public static int lastDraws() {
        return lastDraws;
    }

    /** Projectiles tracked (drawn or collapsing); 0 once every projectile and its impact collapse are gone. */
    public static int trackedProjectiles() {
        return TRACKS.size();
    }

    public static long vertexBufferBytes() {
        return vertices == null ? 0 : 3L * vertices.size();
    }

    public static void close() {
        ALLOCATOR.close();
        if (vertices != null) {
            vertices.close();
            vertices = null;
        }
        if (timer != null) {
            timer.close();
            timer = null;
        }
    }
}

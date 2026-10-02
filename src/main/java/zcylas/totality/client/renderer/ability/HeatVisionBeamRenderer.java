// client/renderer/ability/HeatVisionBeamRenderer.java
package zcylas.totality.client.renderer.ability;

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
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.networking.ability.ClientAbilityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Heat Vision beam, V2 (VFX Experiment 2). Client presentation only: the server-side ability (mana, damage, block
 * scorching) is unchanged.
 *
 * <ul>
 *   <li>The beams leave the player's eyes (first person: slightly below and beside the view, so they read as two
 *       converging beams instead of a dot at the crosshair; third person: from the face).</li>
 *   <li>Each beam is a camera-facing ribbon shaded by {@code core/heat_vision_beam} (white-hot core, orange body, red
 *       rim, energy flowing outwards); width never drops below {@link #MIN_HALF_ANGLE} on screen.</li>
 *   <li>An impact hotspot ({@code core/heat_vision_hotspot}) marks a hit, with a little smoke and flame.</li>
 *   <li>The hot core and the hotspot are contributed to the Totality Emissive Rendering Layer
 *       ({@link HeatVisionEmissive}), which gives the glow.</li>
 *   <li>The beam ignites (grows from the eyes over {@link #IGNITE_SECONDS}) and fades out ({@link #FADE_SECONDS}).</li>
 * </ul>
 *
 * Depth: reversed-Z {@code GREATER_THAN_OR_EQUAL}, no depth writes (additive light), so terrain and entities in front
 * hide the beam. All beams of a frame are drawn with one ribbon draw and one hotspot draw.
 */
public final class HeatVisionBeamRenderer {

    private static final Identifier HEAT_VISION_ID =
            Identifier.fromNamespaceAndPath("totality", "heat_vision");

    private static final float RANGE = 20.0f;

    // ── Shape ─────────────────────────────────────────────────────────────────
    static final int SEGMENTS = 24;
    /** Half-width of the ribbon in blocks (the shader's visible core is about a third of it). */
    static final double BASE_HALF_WIDTH = 0.05;
    /** Minimum half-width on screen, in radians (about 1.5 px at 1080p and a 70 degree field of view). */
    static final double MIN_HALF_ANGLE = 0.0018;
    static final double HOTSPOT_HALF_SIZE = 0.32;
    private static final double FIRST_PERSON_FORWARD = 0.30;
    private static final double FIRST_PERSON_SIDE = 0.11;
    private static final double FIRST_PERSON_DOWN = 0.09;
    private static final double THIRD_PERSON_FORWARD = 0.26;
    private static final double THIRD_PERSON_SIDE = 0.065;

    // ── Timing ────────────────────────────────────────────────────────────────
    static final float IGNITE_SECONDS = 0.18f;
    static final float FADE_SECONDS = 0.12f;

    // ── Pipelines ─────────────────────────────────────────────────────────────
    private static final RenderPipeline BEAM_PIPELINE = RenderPipelines.register(pipeline("heat_vision_v2_beam", "heat_vision_beam"));
    private static final RenderPipeline HOTSPOT_PIPELINE = RenderPipelines.register(pipeline("heat_vision_v2_hotspot", "heat_vision_hotspot"));

    private static final ByteBufferBuilder BEAM_ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final ByteBufferBuilder HOTSPOT_ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static MappableRingBuffer beamVertices;
    private static MappableRingBuffer hotspotVertices;

    // ── State ─────────────────────────────────────────────────────────────────
    private static float level;
    private static boolean igniting;
    private static long lastNanos = -1;
    private static Vec3 lastBlockHit;

    /** Development only: extra client-side beams (measurements, depth probe). Empty in normal play. */
    private static final List<HeatVisionBeam> TEST_BEAMS = new ArrayList<>();
    /** Development only: draw with the pre-V2 renderer for A/B comparison. */
    private static boolean classic;

    // ── Development measurement ───────────────────────────────────────────────
    private static TimerQuery timer;
    private static boolean timing;
    private static boolean awaitingTimer;
    private static long lastGpuNanos = -1;
    private static int lastDrawCalls;
    private static int lastBeamCount;

    private HeatVisionBeamRenderer() {}

    private static RenderPipeline pipeline(String name, String fragment) {
        return RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("totality", "pipeline/" + name))
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withVertexShader(Identifier.fromNamespaceAndPath("totality", "core/heat_vision"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("totality", "core/" + fragment))
                .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .withCull(false)
                .build();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register() {
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(context -> render(context.levelState().cameraRenderState));
        ClientTickEvents.END_CLIENT_TICK.register(client -> impactParticles());
    }

    private static void render(CameraRenderState camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            level = 0;
            HeatVisionEmissive.INSTANCE.submit(List.of(), SEGMENTS, BASE_HALF_WIDTH, MIN_HALF_ANGLE);
            return;
        }
        if (timer != null && awaitingTimer && timer.getStatus() == TimerQuery.Status.NOT_RECORDING) {
            lastGpuNanos = timer.get();
            awaitingTimer = false;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        boolean channeling = ClientAbilityManager.isChanneling(HEAT_VISION_ID);
        advance(channeling);

        List<HeatVisionBeam> beams = new ArrayList<>(TEST_BEAMS);
        lastBlockHit = null;
        if (level > 0.001f) addPlayerBeams(mc, mc.player, partialTick, beams);
        lastBeamCount = beams.size();
        lastDrawCalls = 0;
        if (beams.isEmpty() && !(classic && channeling)) {
            HeatVisionEmissive.INSTANCE.submit(List.of(), SEGMENTS, BASE_HALF_WIDTH, MIN_HALF_ANGLE);
            return;
        }
        boolean timed = timing && timer != null && !awaitingTimer && timer.getStatus() == TimerQuery.Status.NOT_RECORDING;
        if (timed) timer.beginProfile();
        if (classic) {
            lastDrawCalls = HeatVisionClassicRenderer.draw(mc, TEST_BEAMS, camera, channeling);
            HeatVisionEmissive.INSTANCE.submit(List.of(), SEGMENTS, BASE_HALF_WIDTH, MIN_HALF_ANGLE);
        } else {
            drawBeams(mc, beams, camera);
            HeatVisionEmissive.INSTANCE.submit(beams, SEGMENTS, BASE_HALF_WIDTH, MIN_HALF_ANGLE);
        }
        if (timed) {
            timer.endProfile();
            awaitingTimer = true;
        }
    }

    /** Ignition / fade envelope, time-based. */
    private static void advance(boolean channeling) {
        long now = System.nanoTime();
        float dt = lastNanos < 0 ? 0 : Math.min((now - lastNanos) / 1.0e9f, 0.1f);
        lastNanos = now;
        if (channeling) {
            if (level <= 0.001f) igniting = true;
            level = Math.min(1.0f, level + dt / IGNITE_SECONDS);
            if (level >= 1.0f) igniting = false;
        } else {
            igniting = false;
            level = Math.max(0.0f, level - dt / FADE_SECONDS);
        }
    }

    private static void addPlayerBeams(Minecraft mc, Player player, float partialTick, List<HeatVisionBeam> beams) {
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        Vec3 hitPos = clientRaycast(mc, eye, eye.add(look.scale(RANGE)));
        boolean hit = hitPos != null;
        Vec3 end = hit ? hitPos : eye.add(look.scale(RANGE));

        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        double forward = firstPerson ? FIRST_PERSON_FORWARD : THIRD_PERSON_FORWARD;
        double side = firstPerson ? FIRST_PERSON_SIDE : THIRD_PERSON_SIDE;
        double down = firstPerson ? FIRST_PERSON_DOWN : 0.0;
        Vec3 centre = eye.add(look.scale(forward)).subtract(up.scale(down));

        float strength = smooth(level);
        float length = igniting ? smooth(level) : 1.0f;
        beams.add(new HeatVisionBeam(centre.subtract(right.scale(side)), end, hit, strength, length));
        beams.add(new HeatVisionBeam(centre.add(right.scale(side)), end, hit, strength, length));
        if (hit && lastHitIsBlock) lastBlockHit = end;
    }

    private static float smooth(float x) {
        return x * x * (3.0f - 2.0f * x);
    }

    // ── Client raycast ────────────────────────────────────────────────────────

    private static boolean lastHitIsBlock;

    /** The first entity or block hit along the eye line, or null when nothing is within range. */
    private static Vec3 clientRaycast(Minecraft mc, Vec3 start, Vec3 end) {
        lastHitIsBlock = false;
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
            lastHitIsBlock = true;
            return blockHit.getLocation();
        }

        return null;
    }

    /** The hit point along the eye line, or {@code end} when nothing is within range (the pre-V2 behaviour). */
    static Vec3 raycastOrEnd(Minecraft mc, Vec3 start, Vec3 end) {
        Vec3 hit = clientRaycast(mc, start, end);
        return hit == null ? end : hit;
    }

    // ── Impact particles (local player's beam on a block) ─────────────────────

    private static int particleTick;

    private static void impactParticles() {
        Minecraft mc = Minecraft.getInstance();
        Vec3 at = lastBlockHit;
        if (at == null || mc.level == null || level < 0.5f) return;
        particleTick++;
        if (particleTick % 2 == 0) {
            mc.level.addParticle(ParticleTypes.SMOKE, at.x, at.y + 0.05, at.z, 0.0, 0.03, 0.0);
        }
        if (particleTick % 5 == 0) {
            mc.level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y + 0.05, at.z,
                    (mc.level.getRandom().nextDouble() - 0.5) * 0.04, 0.02, (mc.level.getRandom().nextDouble() - 0.5) * 0.04);
        }
    }

    // ── GPU draw ──────────────────────────────────────────────────────────────

    private static void drawBeams(Minecraft mc, List<HeatVisionBeam> beams, CameraRenderState camera) {
        BufferBuilder beamBuffer = new BufferBuilder(BEAM_ALLOCATOR, BEAM_PIPELINE.getPrimitiveTopology(),
                BEAM_PIPELINE.getVertexFormatBinding(0));
        BufferBuilder hotspotBuffer = new BufferBuilder(HOTSPOT_ALLOCATOR, HOTSPOT_PIPELINE.getPrimitiveTopology(),
                HOTSPOT_PIPELINE.getVertexFormatBinding(0));
        HeatVisionGeometry.VertexSink beamSink = (x, y, z, u, v, r, g, b, a) -> beamBuffer.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a);
        HeatVisionGeometry.VertexSink hotspotSink = (x, y, z, u, v, r, g, b, a) -> hotspotBuffer.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a);
        for (HeatVisionBeam beam : beams) {
            HeatVisionGeometry.ribbon(beam, camera.pos, SEGMENTS, BASE_HALF_WIDTH, MIN_HALF_ANGLE, 1.0, 1.0f, 1.0f, 1.0f, beamSink);
            HeatVisionGeometry.hotspot(beam, camera.pos, HOTSPOT_HALF_SIZE, MIN_HALF_ANGLE * 4.0, 1.0f, 1.0f, 1.0f, hotspotSink);
        }
        MeshData beamMesh = beamBuffer.build();
        if (beamMesh != null) {
            beamVertices = draw(mc, BEAM_PIPELINE, beamMesh, beamVertices, "beam", camera.viewRotationMatrix);
            lastDrawCalls++;
        }
        MeshData hotspotMesh = hotspotBuffer.build();
        if (hotspotMesh != null) {
            hotspotVertices = draw(mc, HOTSPOT_PIPELINE, hotspotMesh, hotspotVertices, "hotspot", camera.viewRotationMatrix);
            lastDrawCalls++;
        }
    }

    /**
     * Uploads {@code mesh} and draws it once. Each pipeline has its own ring buffer and draws once per frame: a
     * {@link MappableRingBuffer} slot must not be reused within one frame (26.2's fences reject that).
     */
    @SuppressWarnings("resource")
    static MappableRingBuffer draw(Minecraft mc, RenderPipeline pipeline, MeshData mesh, MappableRingBuffer ring,
                                   String label, Matrix4f viewMatrix) {
        MeshData.DrawState drawState = mesh.drawState();
        VertexFormat format = drawState.format();
        int size = drawState.vertexCount() * format.getVertexSize();
        if (ring == null || ring.size() < size) {
            if (ring != null) ring.close();
            ring = new MappableRingBuffer(() -> "totality heat vision " + label,
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, Math.max(size, 8192));
        }
        try (GpuBufferSlice.MappedView view = ring.currentBuffer()
                .slice(0, mesh.vertexBuffer().remaining())
                .map(false, true)) {
            MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
        }
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(pipeline.getPrimitiveTopology());
        GpuBuffer indices = indexBuffer.getBuffer(drawState.indexCount());
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(viewMatrix);
        try (RenderPass pass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "totality heat vision " + label,
                        mc.gameRenderer.mainRenderTarget().getColorTextureView(),
                        Optional.empty(),
                        mc.gameRenderer.mainRenderTarget().getDepthTextureView(),
                        OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);
            pass.setVertexBuffer(0, ring.currentBuffer().slice());
            pass.setIndexBuffer(indices, indexBuffer.type());
            pass.drawIndexed(drawState.indexCount(), 1, 0, 0, 0);
        }
        mesh.close();
        ring.rotate();
        return ring;
    }

    // ── Development hooks ─────────────────────────────────────────────────────

    /** Development only: replaces the extra client-side test beams (empty list = none). */
    public static void setTestBeams(List<HeatVisionBeam> beams) {
        TEST_BEAMS.clear();
        TEST_BEAMS.addAll(beams);
    }

    /** Development only: draw with the pre-V2 renderer (A/B comparison). Ignored outside a development environment. */
    public static void setClassic(boolean on) {
        classic = on && VerificationReporter.isDevEnvironment();
    }

    public static boolean classic() {
        return classic;
    }

    public static void setTiming(boolean on) {
        timing = on;
        if (on && timer == null) timer = new TimerQuery();
        lastGpuNanos = -1;
    }

    /** GPU time of the beam draws (average of the last measured frames), or -1 when not measured. */
    public static long lastGpuNanos() {
        return lastGpuNanos;
    }

    public static int lastDrawCalls() {
        return lastDrawCalls;
    }

    public static int lastBeamCount() {
        return lastBeamCount;
    }

    /** Bytes of the vertex ring buffers currently allocated (3 slots each). */
    public static long vertexBufferBytes() {
        long total = 0;
        if (beamVertices != null) total += 3L * beamVertices.size();
        if (hotspotVertices != null) total += 3L * hotspotVertices.size();
        return total + HeatVisionClassicRenderer.vertexBufferBytes();
    }

    public static void close() {
        BEAM_ALLOCATOR.close();
        HOTSPOT_ALLOCATOR.close();
        if (beamVertices != null) {
            beamVertices.close();
            beamVertices = null;
        }
        if (hotspotVertices != null) {
            hotspotVertices.close();
            hotspotVertices = null;
        }
        HeatVisionClassicRenderer.close();
        if (timer != null) {
            timer.close();
            timer = null;
        }
    }
}

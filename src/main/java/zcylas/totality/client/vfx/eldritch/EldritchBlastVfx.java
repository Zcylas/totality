package zcylas.totality.client.vfx.eldritch;

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
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
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
import zcylas.totality.client.vfx.screen.ScreenFx;
import zcylas.totality.client.vfx.screen.ScreenFxAudience;
import zcylas.totality.client.vfx.screen.ScreenFxChannel;
import zcylas.totality.client.vfx.screen.ScreenFxEnvelope;
import zcylas.totality.client.vfx.screen.ScreenFxFalloff;
import zcylas.totality.client.vfx.screen.ScreenFxPriority;
import zcylas.totality.client.vfx.screen.ScreenFxRequest;
import zcylas.totality.entity.magic.SpellBoltEntity;
import zcylas.totality.init.ModSounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Eldritch Blast V2: the client presentation of the unchanged Eldritch Blast bolt ({@link SpellBoltEntity} with
 * {@link SpellBoltEntity.VisualStyle#ELDRITCH}: same speed, collision, attack roll and damage). A discrete force
 * discharge rather than a continuous ray (after the BG3 reference): dark and teal energy coil in at the caster's hand,
 * a very bright, wide discharge snaps out from the hand to the bolt and contracts at once into a thin white-cored teal
 * streak that travels with it ({@link EldritchBeamTrack}), the hit throws a white-hot flash with sharp spikes and needle
 * sparks ({@link EldritchImpact}), and a black, thorny fracture lingers along the path. Each beam of a multi-beam cast is
 * its own bolt and track; consecutive beams of one caster alternate hands.
 *
 * <p>Sound is played here, not by the server, so it is locked to what is drawn: the cast sound where the flare starts
 * (its in-drawn swell lasts exactly {@link EldritchBeamTrack#RELEASE_DELAY}, so its crack lands on the release) and the
 * impact sound on the server's impact event. {@link SoundVariant} selects the original Totality sounds (default) or
 * the private reference extract (development comparison only).
 *
 * <p>Every element is drawn in one draw a frame (shader {@code core/vfx_eldritch}): premultiplied alpha, reversed-Z
 * depth test, no depth writes, at {@code BEFORE_TRANSLUCENT_TERRAIN} like Fireball V2. The beam core, head, flare and
 * impact flash feed the Emissive Rendering Layer (budget group {@link #GLOW_GROUP}). Screen FX: a slight kick for the
 * caster at the release and a small distance-limited shake at an impact; no flashes (a cantrip cast every second must
 * not strobe).
 */
public final class EldritchBlastVfx {

    /** Colours: teal (BG3 reference, default) or violet (the spell icon's; development option). */
    public enum Palette { VIOLET, TEAL }

    /** Which sound set is played (REFERENCE = private comparison material, see ModSounds). */
    public enum SoundVariant { CUSTOM, REFERENCE }

    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "pipeline/vfx_eldritch"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withVertexShader(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "core/vfx_fire"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "core/vfx_eldritch"))
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build());

    static final int MODE_BEAM = 0, MODE_RESIDUE = 1, MODE_HEAD = 2, MODE_FLARE = 3, MODE_IMPACT = 4, MODE_SPARK = 5;

    static final double BEAM_HALF_WIDTH = 0.08;
    static final double RESIDUE_HALF_WIDTH = 0.55;
    static final double HEAD_HALF_SIZE = 0.34;
    static final double FLARE_HALF_SIZE = 0.85;
    static final double IMPACT_HALF_SIZE = 1.3;
    static final double SPARK_HALF_WIDTH = 0.03;
    static final double SPARK_TAIL_SECONDS = 0.07;
    /** Thin limits: a beam starting at the caster's own hand must stay a thin line right in front of the eyes. */
    static final RibbonGeometry.Limits LIMITS = new RibbonGeometry.Limits(0.002, 0.03, 0.3, 0.8);
    static final double SPRITE_MIN_HALF_ANGLE = 0.004;
    static final double SPRITE_MAX_HALF_ANGLE = 0.10;
    /** Hand offsets from the eyes (forward, right, down), first person and as seen by others. */
    static final double[] HAND_FIRST_PERSON = {0.55, 0.30, 0.26};
    static final double[] HAND_THIRD_PERSON = {0.50, 0.32, 0.32};
    /** Consecutive beams of one caster within this many seconds alternate hands. */
    static final double ALTERNATE_WINDOW = 0.6;
    /** Owner within this distance of the first-seen bolt = this client saw the cast. */
    static final double CAST_SEEN_DISTANCE = 4.5;

    /** Built-in pack (resourcepacks/<name>, git-ignored) holding the private reference sounds. */
    static final String REFERENCE_PACK = "eldritch_reference_private";

    public static final String GLOW_GROUP = "eldritch";
    static final float GLOW_GROUP_LIMIT = 1.5f;
    static final float DEMAND_BEAM = 0.2f;
    static final float DEMAND_IMPACT = 0.3f;

    static final float CASTER_KICK = 0.12f;
    static final ScreenFxEnvelope KICK_ENVELOPE = new ScreenFxEnvelope(0.0, 0.03, 0.12);
    static final float IMPACT_SHAKE = 0.25f;
    static final ScreenFxEnvelope IMPACT_ENVELOPE = new ScreenFxEnvelope(0.01, 0.03, 0.2);
    static final ScreenFxFalloff IMPACT_FALLOFF = new ScreenFxFalloff(4.0, 24.0);

    private static final Map<Integer, EldritchBeamTrack> TRACKS = new HashMap<>();
    private static final List<EldritchImpact> IMPACTS = new ArrayList<>();
    private static final List<EldritchBeamTrack> FRAME = new ArrayList<>();
    private static final Map<UUID, double[]> LAST_CAST = new HashMap<>();
    private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private static final Glow GLOW = new Glow();
    private static MappableRingBuffer vertices;
    private static long frameId;
    private static long impactSeed;
    private static ClientLevel lastLevel;

    private static Palette palette = parse(Palette.class, System.getProperty("totality.eldritch.palette"), Palette.TEAL);
    private static SoundVariant soundVariant = parse(SoundVariant.class, System.getProperty("totality.eldritch.sound"), SoundVariant.CUSTOM);
    private static boolean emissiveEnabled = true;

    // ── Development measurement ───────────────────────────────────────────────
    private static TimerQuery timer;
    private static boolean timing;
    private static boolean awaitingTimer;
    private static long gpuSum;
    private static int gpuSamples;
    private static long cpuSum;
    private static int cpuSamples;
    private static int lastBeams;
    private static int lastImpacts;
    private static int lastQuads;
    private static int peakQuads;
    private static int castSounds;
    private static int impactSounds;
    private static final List<String> EVENTS = new ArrayList<>();

    private EldritchBlastVfx() {}

    public static void register() {
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(context -> render(context.levelState().cameraRenderState));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
        EmissiveGlow.setGroupLimit(GLOW_GROUP, GLOW_GROUP_LIMIT);
        registerPrivateReferencePack();
    }

    /**
     * The private reference sounds ({@link SoundVariant#REFERENCE}) live in a git-ignored built-in pack that exists only
     * on a local development copy; a clean checkout has no such folder, registers nothing and logs nothing.
     */
    private static void registerPrivateReferencePack() {
        FabricLoader.getInstance().getModContainer(Totality.MOD_ID)
                .filter(mod -> mod.findPath("resourcepacks/" + REFERENCE_PACK).isPresent())
                .ifPresent(mod -> ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath(Totality.MOD_ID, REFERENCE_PACK), mod,
                        PackActivationType.ALWAYS_ENABLED));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Game seconds (game time + partial tick). */
    private static double now(Minecraft mc) {
        return (mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)) / 20.0;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    /**
     * The server's impact event (ModParticles.ELDRITCH_IMPACT) at the exact hit point; a zero normal is an expiry in
     * the air. Ends the nearest matching beam there, starts the impact and plays the impact sound.
     */
    public static void impact(ClientLevel level, Vec3 at, Vec3 normal) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double now = now(mc);
        boolean fizzle = normal.lengthSqr() < 1.0e-6;
        EldritchBeamTrack best = null;
        double bestDist = 4.0;
        for (EldritchBeamTrack t : TRACKS.values()) {
            if (t.impactMatched) continue;
            double d = EldritchBeamTrack.distanceToSegment(at, t.origin, t.head.add(t.dir.scale(2.6)));
            if (d < bestDist) {
                bestDist = d;
                best = t;
            }
        }
        if (best != null) {
            best.impactMatched = true;
            best.fizzled = fizzle;
            best.head = at;
            if (!best.removed()) best.removedAt = now;
        }
        EVENTS.add(String.format(Locale.ROOT, "impact t=%.3f wall=%d fizzle=%s matched=%s", now, System.currentTimeMillis(), fizzle, best != null));
        if (fizzle) return;
        IMPACTS.add(new EldritchImpact(at, normal, now, ++impactSeed * 7919L, false));
        SoundEvent sound = soundVariant == SoundVariant.REFERENCE ? ModSounds.ELDRITCH_BLAST_IMPACT_REFERENCE : ModSounds.ELDRITCH_BLAST_IMPACT;
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0f, 0.95f + level.getRandom().nextFloat() * 0.1f, false);
        impactSounds++;
        ScreenFx.request(ScreenFxRequest.at(ScreenFxChannel.SHAKE, at, IMPACT_SHAKE, IMPACT_ENVELOPE, IMPACT_FALLOFF, 0, null));
    }

    private static EldritchBeamTrack newTrack(Minecraft mc, SpellBoltEntity bolt, Vec3 pos, double now) {
        EldritchBeamTrack t = new EldritchBeamTrack();
        t.entity = bolt;
        Vec3 v = bolt.getDeltaMovement();
        t.dir = v.lengthSqr() > 1.0e-8 ? v.normalize() : Vec3.directionFromRotation(bolt.getXRot(), bolt.getYRot());
        t.castAt = now;
        t.seed = (bolt.getId() * 0.6180339f) % 1.0f;
        Entity owner = bolt.getOwner();
        t.castSeen = owner != null && owner.getEyePosition().distanceTo(pos) <= CAST_SEEN_DISTANCE;
        if (t.castSeen) {
            double[] last = LAST_CAST.get(owner.getUUID());
            int side = last != null && now - last[0] < ALTERNATE_WINDOW ? (int) -last[1] : 1;
            LAST_CAST.put(owner.getUUID(), new double[]{now, side});
            boolean firstPerson = owner == mc.player && mc.options.getCameraType().isFirstPerson();
            t.origin = EldritchBeamTrack.hand(owner.getEyePosition(), t.dir, firstPerson ? HAND_FIRST_PERSON : HAND_THIRD_PERSON, side);
            SoundEvent sound = soundVariant == SoundVariant.REFERENCE ? ModSounds.ELDRITCH_BLAST_CAST_REFERENCE : ModSounds.ELDRITCH_BLAST_CAST;
            mc.level.playLocalSound(t.origin.x, t.origin.y, t.origin.z, sound, SoundSource.PLAYERS, 1.0f,
                    0.96f + mc.level.getRandom().nextFloat() * 0.08f, false);
            castSounds++;
            EVENTS.add(String.format(Locale.ROOT, "cast t=%.3f wall=%d id=%d side=%d", now, System.currentTimeMillis(), bolt.getId(), side));
        } else {
            t.origin = pos;
        }
        return t;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private static void render(CameraRenderState camera) {
        Minecraft mc = Minecraft.getInstance();
        if (timer != null && awaitingTimer && timer.getStatus() == TimerQuery.Status.NOT_RECORDING) {
            gpuSum += timer.get();
            gpuSamples++;
            awaitingTimer = false;
        }
        lastQuads = 0;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            TRACKS.clear();
            IMPACTS.clear();
            LAST_CAST.clear();
        }
        if (mc.level == null) {
            finish();
            return;
        }
        long cpuStart = System.nanoTime();
        double now = now(mc);
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        frameId++;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof SpellBoltEntity b) || b.isRemoved() || b.visualStyle() != SpellBoltEntity.VisualStyle.ELDRITCH) continue;
            Vec3 pos = b.getPosition(partialTick);
            EldritchBeamTrack t = TRACKS.get(b.getId());
            if (t == null) {
                t = newTrack(mc, b, pos, now);
                TRACKS.put(b.getId(), t);
            }
            if (!t.impactMatched) t.head = pos;
            t.frame = frameId;
        }
        FRAME.clear();
        for (Iterator<EldritchBeamTrack> it = TRACKS.values().iterator(); it.hasNext(); ) {
            EldritchBeamTrack t = it.next();
            if (t.frame != frameId && !t.removed()) t.removedAt = now; // gone: hit (event may still come), expiry, range
            if (!t.releaseFired && t.castSeen && t.released(now)) {
                t.releaseFired = true;
                Entity owner = t.entity.getOwner();
                if (owner == mc.player) {
                    ScreenFx.request(new ScreenFxRequest(ScreenFxChannel.SHAKE, null, CASTER_KICK, KICK_ENVELOPE, ScreenFxFalloff.NONE,
                            ScreenFxPriority.NORMAL, ScreenFxAudience.SUBJECT_ONLY, owner.getUUID(), 0, null));
                }
            }
            if (!t.evaluate(now)) {
                it.remove();
                continue;
            }
            FRAME.add(t);
        }
        IMPACTS.removeIf(i -> i.finished(now));
        lastBeams = FRAME.size();
        lastImpacts = IMPACTS.size();
        if (FRAME.isEmpty() && IMPACTS.isEmpty()) {
            finish();
            return;
        }
        Vec3 cam = camera.pos;
        Vector3f right = camera.orientation.transform(new Vector3f(1, 0, 0));
        Vector3f up = camera.orientation.transform(new Vector3f(0, 1, 0));
        BufferBuilder buffer = new BufferBuilder(ALLOCATOR, PIPELINE.getPrimitiveTopology(), PIPELINE.getVertexFormatBinding(0));
        int[] verts = {0};
        RibbonGeometry.VertexSink sink = (x, y, z, u, v, r, g, b, a) -> {
            buffer.addVertex(x, y, z).setUv(u, v).setColor(r, g, b, a);
            verts[0]++;
        };
        int pal = palette.ordinal();
        for (EldritchBeamTrack t : FRAME) emitTrack(t, cam, right, up, pal, sink);
        for (EldritchImpact i : IMPACTS) emitImpact(i, now, cam, right, up, pal, sink);
        lastQuads = verts[0] / 4;
        peakQuads = Math.max(peakQuads, lastQuads);
        MeshData mesh = buffer.build();
        cpuSum += System.nanoTime() - cpuStart;
        cpuSamples++;
        if (mesh != null) {
            boolean timed = timing && timer != null && !awaitingTimer && timer.getStatus() == TimerQuery.Status.NOT_RECORDING;
            if (timed) timer.beginProfile();
            draw(mc, mesh, camera.viewRotationMatrix);
            if (timed) {
                timer.endProfile();
                awaitingTimer = true;
            }
        }
        if (emissiveEnabled) {
            GLOW.frame(right, up, now);
            EmissiveGlow.addSource(GLOW);
        } else {
            EmissiveGlow.removeSource(GLOW);
        }
    }

    private static int segments(double length) {
        return (int) Math.clamp(Math.ceil(length / 1.5), 2, 32);
    }

    private static void emitTrack(EldritchBeamTrack t, Vec3 cam, Vector3f right, Vector3f up, int pal, RibbonGeometry.VertexSink sink) {
        if (t.residueAlpha > 0.003f) {
            double len = t.head.distanceTo(t.origin);
            RibbonGeometry.ribbon(t.origin, t.head, cam, segments(len), RibbonGeometry.constant(RESIDUE_HALF_WIDTH), LIMITS, 1.0,
                    t.seed, 0.0f, EldritchBeamTrack.code(MODE_RESIDUE, pal), t.residueAlpha, sink);
        }
        if (t.beamAlpha > 0.003f) {
            double len = t.head.distanceTo(t.tail);
            RibbonGeometry.ribbon(t.tail, t.head, cam, segments(len), RibbonGeometry.constant(BEAM_HALF_WIDTH), LIMITS, t.width,
                    t.seed, t.beamParam(), EldritchBeamTrack.code(MODE_BEAM, pal), t.beamAlpha, sink);
        }
        if (t.headAlpha > 0.003f) sprite(t.head, HEAD_HALF_SIZE, t.headAlpha, t.seed, 0.0f, EldritchBeamTrack.code(MODE_HEAD, pal), cam, right, up, sink);
        if (t.flare >= 0.0f) {
            sprite(t.origin, FLARE_HALF_SIZE, 1.0f, t.seed, t.flare, EldritchBeamTrack.code(MODE_FLARE, pal), cam, right, up, sink);
        }
    }

    private static void emitImpact(EldritchImpact i, double now, Vec3 cam, Vector3f right, Vector3f up, int pal, RibbonGeometry.VertexSink sink) {
        double g = i.progress(now);
        // Lifted off the surface toward the camera so the flash is not cut in half by the wall it hit.
        Vec3 at = i.at.add(i.normal.scale(0.3));
        if (g < 1.0) sprite(at, IMPACT_HALF_SIZE, 1.0f, i.seed, (float) g, EldritchBeamTrack.code(MODE_IMPACT, pal), cam, right, up, sink);
        double age = now - i.start;
        for (EldritchImpact.Spark s : i.sparks) {
            if (age >= s.life()) continue;
            float fade = (float) (1.0 - age / s.life());
            Vec3 head = i.sparkPos(s, age);
            Vec3 tail = i.sparkPos(s, Math.max(0.0, age - SPARK_TAIL_SECONDS));
            if (head.distanceToSqr(tail) < 1.0e-4) tail = head.subtract(s.velocity().normalize().scale(0.01));
            RibbonGeometry.ribbon(head, tail, cam, 1, RibbonGeometry.constant(SPARK_HALF_WIDTH), LIMITS, 1.0,
                    i.seed, (float) Math.min(1.0, head.distanceTo(tail)), EldritchBeamTrack.code(MODE_SPARK, pal), fade, sink);
        }
    }

    /** A camera-facing quad (UV = corner -1..1), limited in angular size. */
    private static void sprite(Vec3 pos, double half, float strength, float seed, float param, float code, Vec3 cam,
                               Vector3f right, Vector3f up, RibbonGeometry.VertexSink sink) {
        Vec3 rel = pos.subtract(cam);
        double dist = Math.max(rel.length(), 1.0e-4);
        double h = Math.min(Math.max(half, dist * SPRITE_MIN_HALF_ANGLE), dist * SPRITE_MAX_HALF_ANGLE);
        float a = strength * RibbonGeometry.nearFade(dist, LIMITS);
        if (a <= 0.003f) return;
        float x = (float) rel.x, y = (float) rel.y, z = (float) rel.z, hh = (float) h;
        float rx = right.x() * hh, ry = right.y() * hh, rz = right.z() * hh;
        float ux = up.x() * hh, uy = up.y() * hh, uz = up.z() * hh;
        sink.vertex(x - rx - ux, y - ry - uy, z - rz - uz, -1, -1, seed, param, code, a);
        sink.vertex(x + rx - ux, y + ry - uy, z + rz - uz, 1, -1, seed, param, code, a);
        sink.vertex(x + rx + ux, y + ry + uy, z + rz + uz, 1, 1, seed, param, code, a);
        sink.vertex(x - rx + ux, y - ry + uy, z - rz + uz, -1, 1, seed, param, code, a);
    }

    private static void finish() {
        lastBeams = 0;
        lastImpacts = 0;
        FRAME.clear();
        EmissiveGlow.removeSource(GLOW);
    }

    /** Uploads and draws once per frame (a ring-buffer slot must not be reused within a frame on 26.2). */
    @SuppressWarnings("resource")
    private static void draw(Minecraft mc, MeshData mesh, Matrix4f viewMatrix) {
        MeshData.DrawState drawState = mesh.drawState();
        VertexFormat format = drawState.format();
        int size = drawState.vertexCount() * format.getVertexSize();
        if (vertices == null || vertices.size() < size) {
            if (vertices != null) vertices.close();
            vertices = new MappableRingBuffer(() -> "totality eldritch blast", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE,
                    Math.max(size, 16384));
        }
        try (GpuBufferSlice.MappedView view = vertices.currentBuffer().slice(0, mesh.vertexBuffer().remaining()).map(false, true)) {
            MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
        }
        RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(PIPELINE.getPrimitiveTopology());
        GpuBuffer indices = indexBuffer.getBuffer(drawState.indexCount());
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(viewMatrix);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "totality eldritch blast",
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
     * The spell's contribution to the Emissive Rendering Layer: each beam's core line and head, the cast flare's flash
     * and the impact flash, in the {@link #GLOW_GROUP} budget group. Unregistered when nothing is drawn.
     */
    private static final class Glow implements EmissiveSource {
        private final Vector3f right = new Vector3f();
        private final Vector3f up = new Vector3f();
        private double now;
        private boolean pending;

        void frame(Vector3f r, Vector3f u, double time) {
            right.set(r);
            up.set(u);
            now = time;
            pending = true;
        }

        @Override
        public float emissiveDemand() {
            float d = 0.0f;
            for (EldritchBeamTrack t : FRAME) d += DEMAND_BEAM * t.beamAlpha * (1.0f + 2.0f * t.releaseFlash);
            for (EldritchImpact i : IMPACTS) d += DEMAND_IMPACT * (float) Math.max(0.0, 1.0 - i.progress(now));
            return d;
        }

        @Override
        public String budgetGroup() {
            return GLOW_GROUP;
        }

        @Override
        public void emit(EmissiveBuffer buffer, Vec3 camera, float partialTick) {
            if (!pending) return;
            pending = false;
            float[] c = palette == Palette.VIOLET ? new float[]{0.75f, 0.42f, 1.0f} : new float[]{0.35f, 1.0f, 0.75f};
            float[] q = new float[12];
            int[] n = {0};
            float[] tint = new float[4];
            RibbonGeometry.VertexSink quads = (vx, vy, vz, u, v, r, g, b, a) -> {
                q[n[0] * 3] = vx;
                q[n[0] * 3 + 1] = vy;
                q[n[0] * 3 + 2] = vz;
                if (++n[0] < 4) return;
                n[0] = 0;
                buffer.quad(q[0], q[1], q[2], q[3], q[4], q[5], q[6], q[7], q[8], q[9], q[10], q[11],
                        tint[0] * a, tint[1] * a, tint[2] * a, 1.0f);
            };
            for (EldritchBeamTrack t : FRAME) {
                if (t.beamAlpha > 0.003f) {
                    // The strongest light at the release, falling off as fast as the beam contracts.
                    float k = 0.8f * (1.0f + 2.0f * t.releaseFlash);
                    tint[0] = c[0] * k;
                    tint[1] = c[1] * k;
                    tint[2] = c[2] * k;
                    RibbonGeometry.ribbon(t.tail, t.head, camera, segments(t.head.distanceTo(t.tail)), RibbonGeometry.constant(0.04),
                            LIMITS, t.width, 0, 0, 0, t.beamAlpha, quads);
                }
                if (t.headAlpha > 0.003f) glowSprite(buffer, t.head, 0.14, t.headAlpha, camera, c);
                if (t.flare >= 0.0f) glowSprite(buffer, t.origin, 0.14, 1.0f - t.flare, camera, c);
            }
            for (EldritchImpact i : IMPACTS) {
                double g = i.progress(now);
                if (g < 1.0) glowSprite(buffer, i.at.add(i.normal.scale(0.3)), 0.45, (float) ((1.0 - g) * (1.0 - g)), camera, c);
            }
        }

        private void glowSprite(EmissiveBuffer buffer, Vec3 pos, double half, float strength, Vec3 camera, float[] c) {
            Vec3 rel = pos.subtract(camera);
            double dist = rel.length();
            float fade = strength * RibbonGeometry.nearFade(dist, LIMITS);
            if (fade <= 0.003f) return;
            float h = (float) Math.min(half, dist * 0.02);
            float x = (float) rel.x, y = (float) rel.y, z = (float) rel.z;
            float rx = right.x() * h, ry = right.y() * h, rz = right.z() * h;
            float ux = up.x() * h, uy = up.y() * h, uz = up.z() * h;
            buffer.quad(x - rx - ux, y - ry - uy, z - rz - uz, x + rx - ux, y + ry - uy, z + rz - uz,
                    x + rx + ux, y + ry + uy, z + rz + uz, x - rx + ux, y - ry + uy, z - rz + uz,
                    c[0] * fade, c[1] * fade, c[2] * fade, 1.0f);
        }
    }

    // ── Settings and development hooks ────────────────────────────────────────

    public static Palette palette() {
        return palette;
    }

    public static void setPalette(Palette p) {
        palette = p;
    }

    public static SoundVariant soundVariant() {
        return soundVariant;
    }

    public static void setSoundVariant(SoundVariant v) {
        soundVariant = v;
    }

    public static void setEmissiveEnabled(boolean on) {
        emissiveEnabled = on;
    }

    public static void setTiming(boolean on) {
        timing = on;
        if (on && timer == null) timer = new TimerQuery();
        resetMeasurements();
    }

    public static void resetMeasurements() {
        gpuSum = gpuSamples = 0;
        cpuSum = cpuSamples = 0;
        peakQuads = 0;
    }

    /** Average GPU time of the draw since the last reset, in ms (-1 when nothing was measured). */
    public static double averageGpuMillis() {
        return gpuSamples == 0 ? -1 : gpuSum / 1.0e6 / gpuSamples;
    }

    public static double averageCpuMillis() {
        return cpuSamples == 0 ? -1 : cpuSum / 1.0e6 / cpuSamples;
    }

    public static int gpuSamples() {
        return gpuSamples;
    }

    public static int lastBeams() {
        return lastBeams;
    }

    public static int lastImpacts() {
        return lastImpacts;
    }

    public static int lastQuads() {
        return lastQuads;
    }

    public static int peakQuads() {
        return peakQuads;
    }

    public static int trackedBeams() {
        return TRACKS.size();
    }

    public static int castSounds() {
        return castSounds;
    }

    public static int impactSounds() {
        return impactSounds;
    }

    /** Cast and impact events since the last call (game seconds and wall-clock ms), for timing evidence. */
    public static List<String> drainEvents() {
        List<String> out = new ArrayList<>(EVENTS);
        EVENTS.clear();
        return out;
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

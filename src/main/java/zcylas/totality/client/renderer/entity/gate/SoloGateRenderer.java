package zcylas.totality.client.renderer.entity.gate;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import zcylas.totality.Totality;
import zcylas.totality.entity.gate.GatePalette;
import zcylas.totality.entity.gate.SoloGateEntity;

/**
 * Draws the Solo Leveling Normal Gate (Creative Test K) from separated, palette-tinted layers — one renderer and one
 * GRAYSCALE texture set for every gate; the instance's {@link GatePalette} supplies each layer's colour as the vertex
 * colour, so a blue and a red gate differ only by palette. Back to front along the gate's normal:
 * <ol>
 *   <li>mist — a soft haze wider than the gate, turning slowly the other way;</li>
 *   <li>depth — the dark outer vortex (alpha-blended, not additive), so the bright swirl has something to sit in;</li>
 *   <li>swirl — the main arms (8-frame loop) plus a smaller, faster inner copy: parallax that reads as depth;</li>
 *   <li>veins — bright crackle turning with the arms;</li>
 *   <li>rim — the irregular energy boundary (8-frame flicker); arcs — lightning along it;</li>
 *   <li>core — the white-hot centre; edge — a glowing band so the gate is a sliver of light edge-on;</li>
 *   <li>fragments — small crystal shards orbiting in and out of the gate's plane (camera-facing).</li>
 * </ol>
 * Everything but the depth layer is additive and full-bright. The layers turn to the camera's side (seen from behind the
 * swirl is mirrored, like a thin sheet). Lifecycle: opening (a point of energy that expands, forms its rim, spins up),
 * stable (continuous flow, a gentle breath), pulse (the entry/distortion reaction: a shock ring, a flash, a spin surge
 * and a wobble), closing (it contracts, flickers, flares and dissipates). Textures: textures/entity/solo_gate/, from
 * Totality-Research/sl-gate/tools/gate_textures.py.
 */
public class SoloGateRenderer extends EntityRenderer<SoloGateEntity, SoloGateRenderer.State> {

    /** Vanilla's eyes pipeline (full-bright entity texture, no depth write) with additive blending. */
    private static final RenderPipeline ADDITIVE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Totality.MOD_ID, "pipeline/solo_gate_additive"))
            .withVertexShader("core/entity")
            .withFragmentShader("core/entity")
            .withShaderDefine("EMISSIVE")
            .withShaderDefine("NO_OVERLAY")
            .withShaderDefine("NO_CARDINAL_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withVertexBinding(0, DefaultVertexFormat.ENTITY)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build());

    private static final RenderType MIST = additive("mist");
    private static final RenderType DEPTH = RenderTypes.entityTranslucentEmissive(texture("depth"));
    private static final RenderType SWIRL = additive("swirl_body");
    private static final RenderType VEINS = additive("swirl_veins");
    private static final RenderType RIM = additive("rim");
    private static final RenderType ARCS = additive("arcs");
    private static final RenderType CORE = additive("core");
    private static final RenderType FRAGMENT = additive("fragment");

    private static final int FRAMES = 8;
    /** The texture's 128-px frame spans the vortex plus its ragged edge; the ragged edge (~0.74-0.9 of the half-frame) sits about RADIUS. */
    private static final float QUAD = SoloGateEntity.RADIUS / 0.74F;
    private static final int FRAGMENTS = 14;
    private static final int EDGE_SEGMENTS = 40;

    public static class State extends EntityRenderState {
        SoloGateEntity.Phase phase = SoloGateEntity.Phase.STABLE;
        float phaseTime;
        float pulseTime = SoloGateEntity.PULSE_TICKS;
        GatePalette palette = GatePalette.BLUE;
        int seed;
        final Quaternionf rotation = new Quaternionf();
        float normalX, normalZ;
    }

    public SoloGateRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath(Totality.MOD_ID, "textures/entity/solo_gate/" + name + ".png");
    }

    private static RenderType additive(String name) {
        return RenderType.create("solo_gate_" + name, RenderSetup.builder(ADDITIVE).withTexture("Sampler0", texture(name)).sortOnUpload().createRenderSetup());
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SoloGateEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.phase = entity.phase();
        state.phaseTime = entity.phaseAge() + partialTicks;
        state.pulseTime = entity.pulseAge() + partialTicks;
        state.palette = entity.palette();
        state.seed = entity.getId();
        state.rotation.rotationY(-entity.getYRot() * Mth.DEG_TO_RAD);
        state.normalX = -Mth.sin(entity.getYRot() * Mth.DEG_TO_RAD);
        state.normalZ = Mth.cos(entity.getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    protected AABB getBoundingBoxForCulling(SoloGateEntity entity) {
        return AABB.ofSize(entity.position(), 7, 7, 7);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        float t = state.ageInTicks / 20.0F;                     // seconds, continuous
        // Lifecycle: overall scale, how formed the rim and swirl are, overall intensity, the core's flare, spin boost.
        float scale = 1, form = 1, intensity = 1, flare = 0, spin = 1, fragmentsOut = 0, jitter = 0;
        switch (state.phase) {
            case OPENING -> {
                float p = Math.min(1.0F, state.phaseTime / SoloGateEntity.OPEN_TICKS);
                scale = 0.08F + 0.92F * easeOutBack(clamp01((p - 0.15F) / 0.65F));   // a point of energy, then it tears open
                form = smoothstep(0.3F, 0.85F, p);
                intensity = clamp01(p * 5.0F);
                flare = 1.4F * (1 - smoothstep(0.0F, 0.45F, p));
                spin = 1 + 3.0F * (1 - p);
                fragmentsOut = 1 - smoothstep(0.4F, 1.0F, p);
            }
            case CLOSING -> {
                float q = Math.min(1.0F, state.phaseTime / SoloGateEntity.CLOSE_TICKS);
                scale = 1 - 0.92F * smoothstep(0.15F, 0.95F, q);
                form = 1 - smoothstep(0.2F, 0.7F, q);
                intensity = 1 - smoothstep(0.75F, 1.0F, q);
                flare = 1.6F * smoothstep(0.55F, 0.8F, q) * (1 - smoothstep(0.85F, 1.0F, q));
                spin = 1 + 5.0F * q * q;
                fragmentsOut = -2.2F * q;                                              // they scatter outward
                jitter = 0.06F * smoothstep(0.1F, 0.4F, q);
            }
            case STABLE -> scale = 1 + 0.015F * Mth.sin(t * 2.1F);
        }
        float pulse = state.pulseTime < SoloGateEntity.PULSE_TICKS ? 1 - state.pulseTime / SoloGateEntity.PULSE_TICKS : 0;
        spin += 2.5F * pulse * pulse;
        flare += 1.2F * pulse * pulse;
        jitter += 0.05F * pulse;
        if (scale <= 0.01F || intensity <= 0.0F) {
            super.submit(state, poseStack, collector, camera);
            return;
        }
        float wobbleX = 1 + jitter * Mth.sin(t * 47.0F), wobbleY = 1 + jitter * Mth.cos(t * 39.0F);
        GatePalette pal = state.palette;
        int frame = Math.floorMod((int) (state.ageInTicks / 3 * spin), FRAMES);
        int arcFrame = Math.floorMod((int) (state.ageInTicks / 2) * 5 + state.seed, FRAMES);
        float flicker = state.phase == SoloGateEntity.Phase.CLOSING ? 0.55F + 0.45F * hash(state.seed + (int) state.ageInTicks) : 1;

        poseStack.pushPose();
        poseStack.mulPose(state.rotation);
        Vec3 toCamera = camera.pos.subtract(state.x, state.y, state.z);
        if (toCamera.x * state.normalX + toCamera.z * state.normalZ < 0) poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(scale * wobbleX, scale * wobbleY, scale);

        float swirlAngle = t * 0.9F * spin, a = intensity;
        // the dark depth layer (alpha-blended) first, every additive layer after it: a fixed, reliable order
        OrderedSubmitNodeCollector glow = collector.order(1);
        disc(poseStack, glow, MIST, QUAD * 1.3F, -0.14F, -t * 0.25F, 0, 1, pal.mist(), 0.55F * a * (0.4F + 0.6F * form));
        disc(poseStack, collector, DEPTH, QUAD, -0.10F, t * 0.35F * spin, 0, 1, pal.depth(), a * form);
        disc(poseStack, glow, SWIRL, QUAD, -0.06F, swirlAngle, frame, FRAMES, pal.body(), 0.85F * a);
        disc(poseStack, glow, SWIRL, QUAD * 0.62F, -0.03F, -swirlAngle * 1.8F + 1.3F, (frame + 3) % FRAMES, FRAMES, pal.body(), 0.55F * a);
        disc(poseStack, glow, VEINS, QUAD, -0.01F, swirlAngle, frame, FRAMES, pal.veins(), 0.8F * a);
        disc(poseStack, glow, RIM, QUAD, 0.02F, -t * 0.12F, frame, FRAMES, pal.rim(), a * form * flicker);
        if (pulse > 0) {                                                            // the distortion's shock ring
            disc(poseStack, glow, RIM, QUAD * (1 + 0.35F * (1 - pulse)), 0.03F, t * 0.3F, frame, FRAMES, pal.arcs(), pulse * 0.9F * a);
        }
        disc(poseStack, glow, ARCS, QUAD, 0.04F, 0, arcFrame, FRAMES, pal.arcs(), 0.9F * a * Math.max(form, flare * 0.5F));
        float coreSize = QUAD * (0.55F + 0.35F * flare) / Math.max(scale, 0.3F);
        disc(poseStack, glow, CORE, coreSize, 0.06F, 0, 0, 1, pal.core(), Math.min(1.0F, (0.95F + 0.2F * Mth.sin(t * 3.0F)) * a + flare * 0.5F));
        int edgeRgb = pal.rim();
        float edgeAlpha = 0.9F * a * form;
        float facing = (float) Math.abs((toCamera.x * state.normalX + toCamera.z * state.normalZ) / Math.max(1.0E-4, toCamera.length()));
        float edgeShow = edgeAlpha * (1 - smoothstep(0.2F, 0.45F, facing));
        if (edgeShow > 0.01F) {
            glow.submitCustomGeometry(poseStack, CORE, (pose, buffer) -> edgeBand(buffer, pose, SoloGateEntity.RADIUS * 0.95F, edgeRgb, edgeShow));
        }
        poseStack.popPose();

        // an aura card that always faces the camera: the gate keeps a glowing volume seen from the side
        float auraAlpha = 0.6F * a * (0.2F + 0.8F * (1 - facing));
        poseStack.pushPose();
        poseStack.mulPose(camera.orientation);
        poseStack.scale(scale, scale, scale);
        int mistRgb = pal.mist();
        glow.submitCustomGeometry(poseStack, CORE, (pose, buffer) -> quad(buffer, pose, SoloGateEntity.RADIUS * 1.1F, SoloGateEntity.RADIUS * 1.1F, 0, 0, 1, mistRgb, auraAlpha));
        poseStack.popPose();

        submitFragments(state, poseStack, glow, camera, t, scale, fragmentsOut, a * (0.5F + 0.5F * form));
        super.submit(state, poseStack, collector, camera);
    }

    /** Crystal shards orbiting the gate, in and out of its plane; each faces the camera. */
    private static void submitFragments(State state, PoseStack poseStack, OrderedSubmitNodeCollector collector, CameraRenderState camera,
                                        float t, float scale, float spread, float alpha) {
        if (alpha <= 0.01F) return;
        int rgb = state.palette.fragments();
        for (int i = 0; i < FRAGMENTS; i++) {
            float h1 = hash(state.seed * 31 + i), h2 = hash(state.seed * 17 + i * 7 + 3), h3 = hash(i * 13 + 5);
            float angle = h1 * Mth.TWO_PI + t * (0.15F + 0.25F * h2) * (i % 2 == 0 ? 1 : -1);
            // spread > 0 (opening): drawn in towards the centre; spread < 0 (closing): scattered outward
            float reach = spread > 0 ? 1 - 0.8F * spread : 1 - 0.6F * spread;
            float radius = SoloGateEntity.RADIUS * (1.08F + 0.45F * h2) * Math.max(0.2F, scale) * reach;
            float lx = Mth.cos(angle) * radius, ly = Mth.sin(angle) * radius + 0.12F * Mth.sin(t * 1.7F + i);
            float lz = (h3 - 0.5F) * 0.9F;
            float size = 0.10F + 0.10F * h3;
            float fade = alpha * (0.6F + 0.4F * Mth.sin(t * 3.1F + i * 1.9F));
            // the offset turned into the world by the gate's rotation, then a plain camera-facing billboard (as vanilla's)
            Vector3f offset = state.rotation.transform(new Vector3f(lx, ly, lz));
            poseStack.pushPose();
            poseStack.translate(offset.x, offset.y, offset.z);
            poseStack.mulPose(camera.orientation);
            poseStack.mulPose(Axis.ZP.rotation(t * (0.8F + h1) + i));
            collector.submitCustomGeometry(poseStack, FRAGMENT, (pose, buffer) -> quad(buffer, pose, size * 0.6F, size, 0, 0, 1, rgb, fade));
            poseStack.popPose();
        }
    }

    /** A square layer of half-size {@code half} at depth {@code z}, turned by {@code angle}, showing frame {@code frame} of {@code frames}. */
    private static void disc(PoseStack poseStack, OrderedSubmitNodeCollector collector, RenderType type, float half, float z, float angle,
                             int frame, int frames, int rgb, float alpha) {
        if (alpha <= 0.005F) return;
        float v0 = (float) frame / frames, v1 = (float) (frame + 1) / frames;
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotation(angle));
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> quad(buffer, pose, half, half, z, v0, v1, rgb, alpha));
        poseStack.popPose();
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float hw, float hh, float z, float v0, float v1, int rgb, float alpha) {
        vertex(buffer, pose, -hw, -hh, z, 0, v1, rgb, alpha);
        vertex(buffer, pose, hw, -hh, z, 1, v1, rgb, alpha);
        vertex(buffer, pose, hw, hh, z, 1, v0, rgb, alpha);
        vertex(buffer, pose, -hw, hh, z, 0, v0, rgb, alpha);
    }

    /** A thin glowing band round the vortex (both windings): the gate's silhouette seen edge-on. Samples the core's hot middle. */
    private static void edgeBand(VertexConsumer buffer, PoseStack.Pose pose, float r, int rgb, float alpha) {
        float d = 0.2F;
        for (int i = 0; i < EDGE_SEGMENTS; i++) {
            float t0 = Mth.TWO_PI * i / EDGE_SEGMENTS, t1 = Mth.TWO_PI * (i + 1) / EDGE_SEGMENTS;
            float x0 = r * Mth.cos(t0), y0 = r * Mth.sin(t0), x1 = r * Mth.cos(t1), y1 = r * Mth.sin(t1);
            vertex(buffer, pose, x0, y0, -d, 0.5F, 0.3F, rgb, alpha);
            vertex(buffer, pose, x1, y1, -d, 0.5F, 0.3F, rgb, alpha);
            vertex(buffer, pose, x1, y1, d, 0.5F, 0.7F, rgb, alpha);
            vertex(buffer, pose, x0, y0, d, 0.5F, 0.7F, rgb, alpha);
            vertex(buffer, pose, x0, y0, d, 0.5F, 0.7F, rgb, alpha);
            vertex(buffer, pose, x1, y1, d, 0.5F, 0.7F, rgb, alpha);
            vertex(buffer, pose, x1, y1, -d, 0.5F, 0.3F, rgb, alpha);
            vertex(buffer, pose, x0, y0, -d, 0.5F, 0.3F, rgb, alpha);
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int rgb, float alpha) {
        buffer.addVertex(pose, x, y, z)
                .setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.round(clamp01(alpha) * 255))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    private static float hash(int n) {
        n = (n << 13) ^ n;
        return ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / (float) 0x7fffffff;
    }

    private static float clamp01(float v) {
        return Math.clamp(v, 0.0F, 1.0F);
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3 - 2 * t);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158F, c3 = c1 + 1;
        return 1 + c3 * (t - 1) * (t - 1) * (t - 1) + c1 * (t - 1) * (t - 1);
    }
}

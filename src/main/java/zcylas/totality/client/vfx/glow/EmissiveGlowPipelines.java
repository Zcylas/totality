package zcylas.totality.client.vfx.glow;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Render pipelines of the emissive glow layer. All of them go through Blaze3D's backend-agnostic {@link RenderPipeline}
 * API (no direct graphics-API calls), so they follow whichever backend the game runs on.
 *
 * <ul>
 *   <li>{@link #EMISSIVE}: emissive geometry added into the float glow buffer, depth-tested against the world
 *       (reversed-Z: {@code GREATER_THAN_OR_EQUAL}) without writing depth.</li>
 *   <li>{@link #DOWNSAMPLE} / {@link #UPSAMPLE}: the blur chain (fullscreen triangle from {@code core/screenquad}).</li>
 *   <li>{@link #COMPOSITE}: adds the blurred glow to the main image (colour only, alpha untouched).</li>
 * </ul>
 */
public final class EmissiveGlowPipelines {

    /** Format of the emissive buffer and the blur chain: half floats keep bright, overlapping glows from clipping. */
    public static final GpuFormat CHAIN_FORMAT = GpuFormat.RGBA16_FLOAT;
    public static final String SAMPLER = "InSampler";
    public static final String UNIFORM_BLOCK = "GlowPass";

    private static final BindGroupLayout PASS_LAYOUT = BindGroupLayout.builder()
            .withSampler(SAMPLER)
            .withUniform(UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER)
            .build();

    public static final RenderPipeline EMISSIVE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(id("pipeline/vfx_glow_emissive"))
                    .withColorTargetState(new ColorTargetState(Optional.of(BlendFunction.ADDITIVE), CHAIN_FORMAT,
                            ColorTargetState.WRITE_ALL))
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .withCull(false)
                    .build());

    public static final RenderPipeline DOWNSAMPLE = RenderPipelines.register(
            fullscreen("vfx_glow_downsample", Optional.empty(), CHAIN_FORMAT, ColorTargetState.WRITE_ALL));

    public static final RenderPipeline UPSAMPLE = RenderPipelines.register(
            fullscreen("vfx_glow_upsample", Optional.of(BlendFunction.ADDITIVE), CHAIN_FORMAT, ColorTargetState.WRITE_ALL));

    public static final RenderPipeline COMPOSITE = RenderPipelines.register(
            fullscreen("vfx_glow_composite", Optional.of(BlendFunction.ADDITIVE), GpuFormat.RGBA8_UNORM,
                    ColorTargetState.WRITE_COLOR));

    private EmissiveGlowPipelines() {}

    /** Forces class initialisation, which registers the pipelines with the game's pipeline list. */
    static void bootstrap() {
    }

    private static RenderPipeline fullscreen(String name, Optional<BlendFunction> blend, GpuFormat format, int writeMask) {
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(id("pipeline/" + name))
                .withVertexShader("core/screenquad")
                .withFragmentShader(id("post/" + name))
                .withBindGroupLayout(PASS_LAYOUT)
                .withColorTargetState(new ColorTargetState(blend, format, writeMask))
                .build();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("totality", path);
    }
}

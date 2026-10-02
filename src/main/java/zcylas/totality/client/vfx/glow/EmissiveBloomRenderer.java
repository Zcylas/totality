package zcylas.totality.client.vfx.glow;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.TimerQuery;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import zcylas.totality.Totality;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * GPU side of the emissive glow layer. Once per frame, after the world and before the first-person hand:
 *
 * <ol>
 *   <li>every {@link EmissiveSource} adds emissive quads, drawn additively into a full-resolution float buffer that
 *       uses the world's depth buffer for occlusion (read only);</li>
 *   <li>the buffer is halved {@code levels} times (downsample), then each level is blurred back up onto the next
 *       larger one (upsample, additive), which sums glows of several radii;</li>
 *   <li>the half-resolution result is added to the main image.</li>
 * </ol>
 *
 * Nothing is recorded when the layer is off or no source emits, and the buffers are released after
 * {@link #RELEASE_AFTER_IDLE_NANOS} without anything to draw, so an unused layer costs no GPU time and no memory.
 */
final class EmissiveBloomRenderer {

    /** Idle time before the buffers are freed (time-based: a frame count would be a fraction of a second at high fps). */
    static final long RELEASE_AFTER_IDLE_NANOS = 10_000_000_000L;
    /** Weight of each smaller level added during the upsample. */
    private static final float UPSAMPLE_WEIGHT = 1.0f;
    /** Scales the user intensity at the composite (the chain sums several levels). */
    private static final float COMPOSITE_GAIN = 0.6f;

    private final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private final List<TextureTarget> chain = new ArrayList<>();
    private final List<MappableRingBuffer> passUniforms = new ArrayList<>();
    private TextureTarget emissive;
    private MappableRingBuffer vertexBuffer;
    private long idleSince = -1;

    private TimerQuery timer;
    private boolean timing;
    private boolean awaitingTimer;
    private long lastGpuNanos = -1;
    private int lastQuads;

    /** Runs the layer for this frame (render thread). */
    void render(RenderTarget main, CameraRenderState camera, float partialTick, List<EmissiveSource> sources,
                EmissiveGlowSettings settings, EmissiveBudget budget) {
        pollTimer();
        if (!settings.active() || sources.isEmpty() || main.getDepthTextureView() == null) {
            idle();
            return;
        }
        BufferBuilder builder = new BufferBuilder(allocator, EmissiveGlowPipelines.EMISSIVE.getPrimitiveTopology(),
                EmissiveGlowPipelines.EMISSIVE.getVertexFormatBinding(0));
        EmissiveBuffer buffer = new EmissiveBuffer(builder);
        budget.resolve(sources, settings.globalLimit());
        for (EmissiveSource source : sources) {
            buffer.setScale(budget.scale(source));
            source.emit(buffer, camera.pos, partialTick);
        }
        lastQuads = buffer.quadCount();
        MeshData mesh = builder.build();
        if (mesh == null) {
            idle();
            return;
        }
        idleSince = -1;
        int levels = settings.levels();
        ensureTargets(main.width, main.height, levels);

        boolean timed = timing && timer != null && timer.getStatus() == TimerQuery.Status.NOT_RECORDING;
        if (timed) timer.beginProfile();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        drawEmissive(encoder, mesh, camera, main);
        GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        int pass = 0;
        RenderTarget input = emissive;
        for (int i = 0; i < levels; i++) {
            TextureTarget output = chain.get(i);
            fullscreen(encoder, pass++, EmissiveGlowPipelines.DOWNSAMPLE, "down " + i, input, output.getColorTextureView(),
                    linear, 1.0f);
            input = output;
        }
        for (int i = levels - 1; i > 0; i--) {
            fullscreen(encoder, pass++, EmissiveGlowPipelines.UPSAMPLE, "up " + i, chain.get(i),
                    chain.get(i - 1).getColorTextureView(), linear, UPSAMPLE_WEIGHT);
        }
        fullscreen(encoder, pass, EmissiveGlowPipelines.COMPOSITE, "composite", chain.get(0), main.getColorTextureView(),
                linear, settings.intensity() * COMPOSITE_GAIN);

        if (timed) {
            timer.endProfile();
            awaitingTimer = true;
        }
    }

    private void drawEmissive(CommandEncoder encoder, MeshData mesh, CameraRenderState camera, RenderTarget main) {
        MeshData.DrawState state = mesh.drawState();
        int size = state.vertexCount() * state.format().getVertexSize();
        if (vertexBuffer == null || vertexBuffer.size() < size) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> "Totality glow emissive vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, Math.max(size, 4096));
        }
        try (GpuBufferSlice.MappedView view = vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining())
                .map(false, true)) {
            MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
        }
        RenderPipeline pipeline = EmissiveGlowPipelines.EMISSIVE;
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(pipeline.getPrimitiveTopology());
        GpuBuffer indexBuffer = indices.getBuffer(state.indexCount());
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(camera.viewRotationMatrix);
        try (RenderPass pass = encoder.createRenderPass(() -> "Totality glow emissive", emissive.getColorTextureView(),
                Optional.of(new Vector4f(0.0f, 0.0f, 0.0f, 0.0f)), main.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertexBuffer.currentBuffer().slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(state.indexCount(), 1, 0, 0, 0);
        }
        mesh.close();
        vertexBuffer.rotate();
    }

    /** One fullscreen-triangle pass reading {@code input} and writing {@code output}. */
    private void fullscreen(CommandEncoder encoder, int index, RenderPipeline pipeline, String label, RenderTarget input,
                            GpuTextureView output, GpuSampler sampler, float intensity) {
        MappableRingBuffer uniforms = passUniforms(index);
        try (GpuBufferSlice.MappedView view = uniforms.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data())
                    .putVec2(1.0f / input.width, 1.0f / input.height)
                    .putFloat(intensity)
                    .putFloat(1.0f);
        }
        try (RenderPass pass = encoder.createRenderPass(() -> "Totality glow " + label, output, Optional.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(EmissiveGlowPipelines.UNIFORM_BLOCK, uniforms.currentBuffer());
            pass.bindTexture(EmissiveGlowPipelines.SAMPLER, input.getColorTextureView(), sampler);
            pass.draw(3, 1, 0, 0);
        }
        uniforms.rotate();
    }

    private MappableRingBuffer passUniforms(int index) {
        while (passUniforms.size() <= index) {
            int n = passUniforms.size();
            passUniforms.add(new MappableRingBuffer(() -> "Totality glow pass " + n,
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 16));
        }
        return passUniforms.get(index);
    }

    private void ensureTargets(int width, int height, int levels) {
        if (emissive != null && emissive.width == width && emissive.height == height && chain.size() == levels) return;
        releaseTargets();
        emissive = new TextureTarget("Totality glow emissive", width, height, false, EmissiveGlowPipelines.CHAIN_FORMAT);
        for (int i = 0; i < levels; i++) {
            chain.add(new TextureTarget("Totality glow level " + i, GlowChainLayout.levelWidth(width, i),
                    GlowChainLayout.levelHeight(height, i), false, EmissiveGlowPipelines.CHAIN_FORMAT));
        }
        Totality.LOGGER.info("[Totality VFX] glow buffers {}x{}, {} levels, {} KiB", width, height, levels,
                GlowChainLayout.bytes(width, height, levels, 8) / 1024);
    }

    private void idle() {
        lastQuads = 0;
        if (timing && !awaitingTimer) lastGpuNanos = 0;
        if (emissive == null) return;
        long now = System.nanoTime();
        if (idleSince < 0) idleSince = now;
        if (now - idleSince >= RELEASE_AFTER_IDLE_NANOS) {
            releaseTargets();
            idleSince = -1;
            Totality.LOGGER.info("[Totality VFX] glow buffers released after {} s idle", RELEASE_AFTER_IDLE_NANOS / 1_000_000_000L);
        }
    }

    private void releaseTargets() {
        if (emissive != null) emissive.destroyBuffers();
        emissive = null;
        chain.forEach(TextureTarget::destroyBuffers);
        chain.clear();
    }

    // ── Development measurement ───────────────────────────────────────────────

    void setTiming(boolean on) {
        timing = on;
        if (on && timer == null) timer = new TimerQuery();
        lastGpuNanos = -1;
    }

    boolean timing() {
        return timing;
    }

    /** Average GPU time of the layer's passes over the last three measured frames; 0 while idle, -1 when unknown. */
    long lastGpuNanos() {
        return lastGpuNanos;
    }

    int lastQuads() {
        return lastQuads;
    }

    boolean hasTargets() {
        return emissive != null;
    }

    private void pollTimer() {
        if (awaitingTimer && timer != null && timer.getStatus() == TimerQuery.Status.NOT_RECORDING) {
            lastGpuNanos = timer.get();
            awaitingTimer = false;
        }
    }

    void close() {
        releaseTargets();
        passUniforms.forEach(MappableRingBuffer::close);
        passUniforms.clear();
        if (vertexBuffer != null) vertexBuffer.close();
        vertexBuffer = null;
        if (timer != null) timer.close();
        timer = null;
        allocator.close();
    }
}

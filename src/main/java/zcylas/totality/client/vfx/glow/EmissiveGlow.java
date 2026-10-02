package zcylas.totality.client.vfx.glow;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import zcylas.totality.Totality;
import zcylas.totality.client.vfx.glow.dev.EmissiveGlowDevCommand;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Experimental shared emissive glow layer (VFX Experiment 1). Effects register an {@link EmissiveSource}; once per
 * frame all sources draw their emissive part into one Totality-only buffer, which is blurred and added to the image.
 * Only registered sources glow: vanilla surfaces (snow, lava, light sources) never enter the buffer.
 *
 * <p>Client only. Settings: {@code config/totality-vfx.properties} ({@link EmissiveGlowSettings}). Heat Vision V2 is
 * the first production source; in a development environment {@code /totalityvfx} adds test sources.
 */
public final class EmissiveGlow {

    private static final List<EmissiveSource> SOURCES = new ArrayList<>();
    private static EmissiveGlowSettings settings = new EmissiveGlowSettings(
            FabricLoader.getInstance().getConfigDir().resolve("totality-vfx.properties"));
    private static EmissiveBloomRenderer renderer;

    private EmissiveGlow() {}

    public static void register() {
        settings = EmissiveGlowSettings.load(FabricLoader.getInstance().getConfigDir().resolve("totality-vfx.properties"));
        EmissiveGlowPipelines.bootstrap();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (renderer != null) renderer.close();
            renderer = null;
        });
        EmissiveGlowDevCommand.registerIfDevelopmentEnvironment();
    }

    /** Adds a source (render thread). Adding the same source twice has no effect. */
    public static void addSource(EmissiveSource source) {
        if (!SOURCES.contains(source)) SOURCES.add(source);
    }

    public static void removeSource(EmissiveSource source) {
        SOURCES.remove(source);
    }

    public static int sourceCount() {
        return SOURCES.size();
    }

    public static EmissiveGlowSettings settings() {
        return settings;
    }

    public static void saveSettings() {
        try {
            settings.save();
        } catch (IOException e) {
            Totality.LOGGER.warn("[Totality VFX] could not save glow settings: {}", e.toString());
        }
    }

    /** Called by the {@code GameRenderer} mixin right after the level is rendered and before the first-person hand. */
    public static void afterLevel(GameRenderer gameRenderer) {
        if (renderer == null) {
            if (SOURCES.isEmpty() || !settings.active()) return;
            renderer = new EmissiveBloomRenderer();
        }
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        renderer.render(gameRenderer.mainRenderTarget(), gameRenderer.gameRenderState().levelRenderState.cameraRenderState,
                partialTick, SOURCES, settings);
    }

    // ── Development measurement (used by the dev command and capture scene) ─

    public static void setTiming(boolean on) {
        if (renderer == null) renderer = new EmissiveBloomRenderer();
        renderer.setTiming(on);
    }

    /** GPU time of the glow passes in nanoseconds (average of the last measured frames), or -1 when not measured. */
    public static long lastGpuNanos() {
        return renderer == null ? -1 : renderer.lastGpuNanos();
    }

    public static int lastQuadCount() {
        return renderer == null ? 0 : renderer.lastQuads();
    }

    /** True while the layer holds GPU buffers (released after a period with nothing to draw). */
    public static boolean holdsBuffers() {
        return renderer != null && renderer.hasTargets();
    }
}

package zcylas.totality.client.vfx.glow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-level guarantees of VFX Experiment 1: supported rendering API only (no direct OpenGL), client-only and
 * network-free, wired into the frame at one place, test tooling gated to the development environment.
 */
class EmissiveGlowWiringTest {

    private static final Path MAIN = Path.of("src/main/java/zcylas/totality");
    private static final Path GLOW = MAIN.resolve("client/vfx/glow");
    private static final Path SHADERS = Path.of("src/main/resources/assets/totality/shaders/post");

    private static List<Path> glowSources() throws IOException {
        try (Stream<Path> files = Files.walk(GLOW)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void usesBlaze3dOnlyNeverDirectOpenGl() throws IOException {
        List<Path> files = new ArrayList<>(glowSources());
        files.add(MAIN.resolve("mixin/client/vfx/GameRendererEmissiveGlowMixin.java"));
        for (Path f : files) {
            String src = Files.readString(f);
            for (String forbidden : new String[] {"org.lwjgl.opengl", "GL11", "GL30", "glId()", "GlTexture", "GlStateManager"}) {
                assertFalse(src.contains(forbidden), f.getFileName() + " must not use " + forbidden);
            }
        }
    }

    @Test
    void neverTouchesTheNetworkOrTheServer() throws IOException {
        for (Path f : glowSources()) {
            String src = Files.readString(f);
            for (String forbidden : new String[] {"ClientPlayNetworking", "Payload", "sendPacket"}) {
                assertFalse(src.contains(forbidden), f.getFileName() + " must not use " + forbidden);
            }
        }
    }

    @Test
    void mixinIsAClientMixinAndTheLayerIsRegistered() throws IOException {
        String json = Files.readString(Path.of("src/main/resources/totality.mixins.json"));
        assertTrue(json.indexOf("\"client.vfx.GameRendererEmissiveGlowMixin\"") > json.indexOf("\"client\""));
        assertTrue(Files.readString(MAIN.resolve("TotalityClient.java")).contains("EmissiveGlow.register();"));
        String mixin = Files.readString(MAIN.resolve("mixin/client/vfx/GameRendererEmissiveGlowMixin.java"));
        assertTrue(mixin.contains("LevelRenderer;render(") && mixin.contains("At.Shift.AFTER"),
                "runs right after the level, before the hand");
    }

    @Test
    void testToolingIsDevelopmentOnly() throws IOException {
        String command = Files.readString(GLOW.resolve("dev/EmissiveGlowDevCommand.java"));
        assertTrue(command.contains("if (!VerificationReporter.isDevEnvironment()) return;"));
        assertTrue(command.contains("if (HologramCapture.requested())"));
    }

    @Test
    void everyPipelineShaderExists() throws IOException {
        String pipelines = Files.readString(GLOW.resolve("EmissiveGlowPipelines.java"));
        for (String name : new String[] {"vfx_glow_downsample", "vfx_glow_upsample", "vfx_glow_composite"}) {
            assertTrue(pipelines.contains("\"" + name + "\""), name + " pipeline");
            String shader = Files.readString(SHADERS.resolve(name + ".fsh"));
            assertTrue(shader.contains("uniform sampler2D " + EmissiveGlowPipelines.SAMPLER + ";"));
            assertTrue(shader.contains("uniform " + EmissiveGlowPipelines.UNIFORM_BLOCK + " {"));
        }
    }
}

package zcylas.totality.client.renderer.ability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-level guarantees of Heat Vision V2 (VFX Experiment 2): reversed-Z depth, supported rendering API only,
 * emissive contribution that cleans itself up, the pre-V2 renderer reachable only from development tooling, and the
 * server-side ability untouched in behaviour (no client presentation leaking into it).
 */
class HeatVisionV2WiringTest {

    private static final Path MAIN = Path.of("src/main/java/zcylas/totality");
    private static final Path DIR = MAIN.resolve("client/renderer/ability");
    private static final Path SHADERS = Path.of("src/main/resources/assets/totality/shaders/core");

    private static String read(Path p) throws IOException {
        return Files.readString(p);
    }

    @Test
    void v2UsesReversedZAndNoDepthWrites() throws IOException {
        String renderer = read(DIR.resolve("HeatVisionBeamRenderer.java"));
        assertTrue(renderer.contains("new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)"));
        assertFalse(renderer.contains("LESS_THAN_OR_EQUAL"), "only the classic comparison renderer keeps the old test");
        assertTrue(renderer.contains("BlendFunction.ADDITIVE"));
    }

    @Test
    void noDirectOpenGl() throws IOException {
        for (String f : new String[] {"HeatVisionBeamRenderer.java", "HeatVisionGeometry.java", "HeatVisionEmissive.java",
                "HeatVisionClassicRenderer.java", "HeatVisionBeam.java"}) {
            String src = read(DIR.resolve(f));
            for (String forbidden : new String[] {"org.lwjgl.opengl", "GL11", "GlTexture", "glId()"}) {
                assertFalse(src.contains(forbidden), f + " must not use " + forbidden);
            }
        }
    }

    @Test
    void emissiveContributionCleansItselfUp() throws IOException {
        String emissive = read(DIR.resolve("HeatVisionEmissive.java"));
        assertTrue(emissive.contains("implements EmissiveSource"));
        assertTrue(emissive.contains("EmissiveGlow.removeSource(this)"), "unregisters when no beam is drawn");
        assertTrue(emissive.contains("frameBeams.clear();"), "beams are consumed once per frame");
        String renderer = read(DIR.resolve("HeatVisionBeamRenderer.java"));
        assertTrue(renderer.contains("HeatVisionEmissive.INSTANCE.submit(List.of()"), "an empty frame unregisters");
    }

    @Test
    void classicRendererIsDevelopmentOnly() throws IOException {
        String renderer = read(DIR.resolve("HeatVisionBeamRenderer.java"));
        assertTrue(renderer.contains("private static boolean classic;"), "off by default");
        assertTrue(renderer.contains("classic = on && VerificationReporter.isDevEnvironment();"),
                "the switch is ignored outside a development environment");
        try (var files = Files.walk(MAIN)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = read(f);
                if (src.contains("setClassic(")) {
                    String path = f.toString().replace('\\', '/');
                    assertTrue(path.contains("/dev/") || path.endsWith("HeatVisionBeamRenderer.java"),
                            f + " must not switch to the classic renderer outside development tooling");
                }
            }
        }
    }

    @Test
    void shadersExistAndUseTheVanillaUniformBlocks() throws IOException {
        String vsh = read(SHADERS.resolve("heat_vision.vsh"));
        assertTrue(vsh.contains("#moj_import <minecraft:dynamictransforms.glsl>") && vsh.contains("#moj_import <minecraft:projection.glsl>"));
        for (String f : new String[] {"heat_vision_beam.fsh", "heat_vision_hotspot.fsh"}) {
            assertTrue(read(SHADERS.resolve(f)).contains("#moj_import <minecraft:globals.glsl>"), f + " animates from GameTime");
        }
    }

    @Test
    void serverAbilityIsUnchangedByThePresentation() throws IOException {
        String ability = read(MAIN.resolve("api/ability/kryptonian/HeatVisionAbility.java"));
        assertFalse(ability.contains("import zcylas.totality.client"), "the server ability does not import client presentation");
        assertTrue(ability.contains("private static final float RANGE           = 20.0f;"));
    }
}

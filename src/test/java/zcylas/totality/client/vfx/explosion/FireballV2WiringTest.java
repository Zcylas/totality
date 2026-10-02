package zcylas.totality.client.vfx.explosion;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-level guarantees of VFX Experiment 3 B1-B2: supported Blaze3D API only, reversed-Z depth, one draw, the V1
 * detonation reachable only from development tooling, Screen FX cosmetic and suppressed by the Camera, the shake
 * mixin hooked at every exit, and Fireball's gameplay classes untouched by the presentation.
 */
class FireballV2WiringTest {

    private static final Path MAIN = Path.of("src/main/java/zcylas/totality");

    private static String read(String p) throws IOException {
        return Files.readString(MAIN.resolve(p)).replace("\r\n", "\n");
    }

    private static List<Path> sources(String dir) throws IOException {
        try (Stream<Path> s = Files.walk(MAIN.resolve(dir))) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void noDirectOpenGlAndNoNetworking() throws IOException {
        for (String dir : new String[]{"client/vfx/explosion", "client/vfx/screen", "client/vfx/projectile", "client/vfx/ribbon"}) {
            for (Path f : sources(dir)) {
                String src = Files.readString(f);
                for (String forbidden : new String[]{"org.lwjgl.opengl", "GL11", "GL30", "GlTexture", "glId()", "ClientPlayNetworking", "Payload"}) {
                    assertFalse(src.contains(forbidden), f.getFileName() + " must not use " + forbidden);
                }
            }
        }
    }

    @Test
    void reversedZPremultipliedOneDraw() throws IOException {
        String r = read("client/vfx/explosion/FireExplosionRenderer.java");
        assertTrue(r.contains("new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)"));
        assertFalse(r.contains("LESS_THAN_OR_EQUAL"));
        assertTrue(r.contains("BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA"));
        assertEquals(1, r.split("drawIndexed\\(").length - 1, "one draw call for every explosion");
        assertTrue(Files.exists(Path.of("src/main/resources/assets/totality/shaders/core/vfx_fire.fsh")));
        assertTrue(Files.exists(Path.of("src/main/resources/assets/totality/shaders/core/vfx_fire.vsh")));
    }

    @Test
    void legacyExplosionAndEmissiveToggleAreDevelopmentOnly() throws IOException {
        String p = read("client/particle/fireball/FireballDetonationParticle.java");
        assertTrue(p.contains("legacy = on && VerificationReporter.isDevEnvironment();"));
        assertTrue(p.contains("private static boolean legacy;"), "V2 by default");
        assertTrue(read("client/vfx/explosion/FireExplosionRenderer.java").contains("emissiveEnabled = on || !VerificationReporter.isDevEnvironment();"));
        // B2.1: the slow-motion clock is development-only; the radius marker lives in the development-only dev package.
        assertTrue(read("client/vfx/explosion/FireExplosionRenderer.java").contains("timeScale = VerificationReporter.isDevEnvironment() ? scale : 1.0;"));
        assertTrue(read("client/vfx/explosion/dev/FireballV2Dev.java").contains("if (!VerificationReporter.isDevEnvironment()) return;"));
        for (Path f : sources("")) {
            String src = Files.readString(f);
            String path = f.toString().replace('\\', '/');
            if (src.contains("setLegacyExplosion(") && !path.endsWith("FireballDetonationParticle.java")) {
                assertTrue(path.contains("/dev/"), f + " must not switch to the V1 detonation outside development tooling");
            }
        }
        String dev = read("client/vfx/explosion/dev/FireballV2Dev.java");
        assertTrue(dev.contains("if (!VerificationReporter.isDevEnvironment()) return;"));
        assertTrue(read("client/vfx/screen/dev/ScreenFxDev.java").contains("if (!VerificationReporter.isDevEnvironment()) return;"));
    }

    @Test
    void screenFxIsCosmeticScopedAndAccessible() throws IOException {
        String fx = read("client/vfx/screen/ScreenFx.java");
        assertTrue(fx.contains("CameraSession.isActive()"), "never while Totality's Camera is open");
        assertTrue(fx.contains("mc.isPaused()"));
        assertTrue(fx.contains("screenEffectScale()") && fx.contains("hideLightningFlash()"), "Minecraft's accessibility options");
        for (String forbidden : new String[]{"setDayTime", "setWeather", "setRainLevel", "setThunderLevel", "gameRules"}) {
            assertFalse(fx.contains(forbidden), "Screen FX never changes world time, weather or rules: " + forbidden);
        }
        String mixin = read("mixin/client/vfx/GameRendererScreenFxMixin.java");
        assertTrue(mixin.contains("@Inject(method = \"bobHurt\", at = @At(\"RETURN\"))"), "every exit of bobHurt (it returns early)");
        String json = Files.readString(Path.of("src/main/resources/totality.mixins.json"));
        assertTrue(json.indexOf("\"client.vfx.GameRendererScreenFxMixin\"") > json.indexOf("\"client\""));
        String client = read("TotalityClient.java");
        assertTrue(client.contains("ScreenFx.register();") && client.contains("FireballV2.register();"));
        String v2 = read("client/particle/fireball/FireballV2.java");
        assertTrue(v2.contains("FireExplosionRenderer.register();") && v2.contains("FireballProjectileVfx.register();"));
    }

    @Test
    void fireballPresentationUsesTheServerCentreAndLeavesGameplayAlone() throws IOException {
        String v2 = read("client/particle/fireball/FireballV2.java");
        assertTrue(v2.contains("FireExplosionRenderer.spawn(level, at,"), "the explosion is centred on the explode packet position");
        assertTrue(v2.contains("FireballProjectileEntity.BLAST_RADIUS"), "the gameplay radius");
        String entity = read("entity/magic/FireballProjectileEntity.java");
        assertFalse(entity.contains("FireExplosion") || entity.contains("ScreenFx"), "the projectile entity is untouched");
        String vfx = read("entity/magic/FireballVfx.java");
        assertFalse(vfx.contains("net.minecraft.client"), "common code only adds particles");
    }

    @Test
    void projectileV2IsOneReversedZDrawAndTheV1LookIsDevelopmentOnly() throws IOException {
        String r = read("client/vfx/projectile/FireballProjectileVfx.java");
        assertTrue(r.contains("new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)"));
        assertFalse(r.contains("LESS_THAN_OR_EQUAL"));
        assertTrue(r.contains("BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA"));
        assertEquals(1, r.split("drawIndexed\\(").length - 1, "one draw call for every projectile");
        assertTrue(r.contains("RibbonGeometry.ribbon("), "the streak is the shared ribbon");
        assertTrue(Files.exists(Path.of("src/main/resources/assets/totality/shaders/core/vfx_fireball_projectile.fsh")));
        String vfx = read("entity/magic/FireballVfx.java");
        assertTrue(vfx.contains("legacyProjectile = on && VerificationReporter.isDevEnvironment();"));
        assertTrue(vfx.contains("private static boolean legacyProjectile;"), "V2 by default");
        assertTrue(read("client/renderer/entity/magic/FireballProjectileRenderer.java").contains("if (FireballVfx.legacyProjectile() && state.traveled >= SHOW_AFTER) {"),
                "the V1 renderer draws only in the development comparison");
    }

    @Test
    void onlyAClientThatSawTheCastPlaysTheCastBurst() throws IOException {
        String e = read("entity/magic/FireballProjectileEntity.java");
        assertTrue(e.contains("castSeen = FireballVfx.sawCast(this, start);"));
        assertTrue(e.contains("if (castSeen) FireballVfx.castBurst(level(), start, travelDirection());"));
        assertTrue(read("entity/magic/FireballVfx.java").contains("return owner != null && owner.getEyePosition().distanceTo(at) <= CAST_SEEN_DISTANCE;"));
    }

    @Test
    void everyFireballGlowsInOneBudgetGroup() throws IOException {
        String v2 = read("client/particle/fireball/FireballV2.java");
        assertTrue(v2.contains("EmissiveGlow.setGroupLimit(GLOW_GROUP, GLOW_GROUP_LIMIT);"));
        assertEquals(2, (read("client/vfx/explosion/FireExplosionRenderer.java") + read("client/vfx/projectile/FireballProjectileVfx.java"))
                .split("return \"fireball\";").length - 1, "the explosion and the projectile report to the fireball group");
    }
}

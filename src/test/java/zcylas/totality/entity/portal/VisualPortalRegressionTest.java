package zcylas.totality.entity.portal;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Creative Capability Test C (visual portal): it stays visual only, its textures match the renderer's layout, the
 * dissolve mask lets the whole surface show when fully open, and rendering code stays on the client.
 */
class VisualPortalRegressionTest {

    private static final String J = "src/main/java/zcylas/totality/";
    private static final Path ASSETS = Path.of("src/main/resources/assets/totality");
    private static final Path TEX = ASSETS.resolve("textures/entity/visual_portal");

    private static String read(String p) throws Exception {
        return Files.readString(Path.of(p));
    }

    @Test
    void thePortalIsVisualOnly() throws Exception {
        for (String f : new String[]{"VisualPortalEntity", "VisualPortalVfx", "VisualPortalCommands"}) {
            String src = read(J + "entity/portal/" + f + ".java");
            for (String forbidden : new String[]{"changeDimension", "TeleportTransition", "setPortalCooldown", "handleInsidePortal",
                    "setAsInsidePortal", "Gate", "ResourceKey<Level>"}) {
                assertFalse(src.contains(forbidden), f + " must not " + forbidden);
            }
        }
        String entity = read(J + "entity/portal/VisualPortalEntity.java");
        assertTrue(entity.contains("public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {\n        return false;"));
        assertFalse(entity.contains("teleportTo"), "nothing is teleported");
        assertTrue(read(J + "entity/portal/VisualPortalCommands.java").contains("isOp("), "the dev command is operator-only");
    }

    @Test
    void frameStripsMatchTheRenderer() throws Exception {
        for (String name : new String[]{"core", "core_mask", "glow"}) {
            BufferedImage im = ImageIO.read(TEX.resolve(name + ".png").toFile());
            assertEquals(40, im.getWidth(), name);
            assertEquals(56 * 16, im.getHeight(), name + ": 16 frames of 40 x 56");
        }
        // The opaque oval in every core frame is 26 x 38 texels: 1.6 x 2.4 blocks at 16 texels a block.
        BufferedImage core = ImageIO.read(TEX.resolve("core.png").toFile());
        for (int f = 0; f < 16; f++) {
            int minX = 99, maxX = -1, minY = 99, maxY = -1;
            for (int y = 0; y < 56; y++) {
                for (int x = 0; x < 40; x++) {
                    if ((core.getRGB(x, f * 56 + y) >>> 24) > 0) {
                        minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                    }
                }
            }
            assertEquals(26, maxX - minX + 1, "frame " + f + " width");
            assertEquals(38, maxY - minY + 1, "frame " + f + " height");
        }
    }

    @Test
    void theFullyOpenSurfaceShowsEveryPixel() throws Exception {
        // Vanilla's dissolve discards a pixel while the vertex alpha is below the mask's alpha: fully open (255) must
        // show all of the surface, and fully dissolved must hide it.
        BufferedImage core = ImageIO.read(TEX.resolve("core.png").toFile());
        BufferedImage mask = ImageIO.read(TEX.resolve("core_mask.png").toFile());
        for (int y = 0; y < core.getHeight(); y++) {
            for (int x = 0; x < 40; x++) {
                if ((core.getRGB(x, y) >>> 24) == 0) continue;
                int a = mask.getRGB(x, y) >>> 24;
                assertTrue(a > 0 && a < 255, "mask alpha at " + x + "," + y);
            }
        }
    }

    @Test
    void everyParticleDefinitionPointsAtRealSprites() throws Exception {
        for (String name : new String[]{"mote", "converge", "scatter"}) {
            Path def = ASSETS.resolve("particles/visual_portal_" + name + ".json");
            var textures = JsonParser.parseString(Files.readString(def)).getAsJsonObject().getAsJsonArray("textures");
            assertFalse(textures.isEmpty(), name);
            for (var t : textures) {
                String path = t.getAsString().replace("totality:", "");
                assertNotNull(ImageIO.read(ASSETS.resolve("textures/particle/" + path + ".png").toFile()), path);
            }
        }
    }

    @Test
    void renderingStaysOnTheClient() throws Exception {
        for (String f : new String[]{"VisualPortalEntity", "VisualPortalVfx", "VisualPortalCommands"}) {
            String src = read(J + "entity/portal/" + f + ".java");
            assertFalse(src.contains("net.minecraft.client."), f + " must not load client classes");
            assertFalse(src.contains("zcylas.totality.client."), f + " must not reference Totality client classes");
        }
        assertTrue(read(J + "TotalityClient.java").contains("VisualPortalRenderer::new"));
    }
}

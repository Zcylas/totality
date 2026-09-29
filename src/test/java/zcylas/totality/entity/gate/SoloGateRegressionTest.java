package zcylas.totality.entity.gate;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creative Test K, the Solo Leveling Normal Gate: one grayscale texture set and one renderer for every gate, the palette
 * as the only per-instance difference, and a visual-only entity (the live lifecycle and cleanup are GateCapture and
 * SoloGateVerification).
 */
class SoloGateRegressionTest {

    private static final Path TEX = Path.of("src/main/resources/assets/totality/textures/entity/solo_gate");
    private static final String J = "src/main/java/zcylas/totality/";

    private static String read(String p) throws Exception {
        return Files.readString(Path.of(p)).replace("\r\n", "\n");
    }

    @Test
    void oneGrayscaleTextureSetServesEveryPalette() throws Exception {
        List<String> layers = List.of("swirl_body", "swirl_veins", "rim", "arcs", "depth", "core", "mist", "fragment");
        try (var files = Files.list(TEX)) {
            assertEquals(layers.size(), files.count(), "no per-colour copies of any layer");
        }
        for (String layer : layers) {
            BufferedImage im = ImageIO.read(TEX.resolve(layer + ".png").toFile());
            for (int y = 0; y < im.getHeight(); y++) {
                for (int x = 0; x < im.getWidth(); x++) {
                    int p = im.getRGB(x, y), r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                    assertTrue(r == g && g == b, layer + " is grayscale (the palette supplies the hue) at " + x + "," + y);
                }
            }
        }
        for (String animated : List.of("swirl_body", "swirl_veins", "rim", "arcs")) {
            BufferedImage im = ImageIO.read(TEX.resolve(animated + ".png").toFile());
            assertEquals(128, im.getWidth());
            assertEquals(128 * 8, im.getHeight(), animated + ": an 8-frame loop");
        }
    }

    @Test
    void palettesAreIndependentAndKeepContrastBetweenLayers() {
        assertSame(GatePalette.BLUE, GatePalette.byId("blue"));
        assertSame(GatePalette.RED, GatePalette.byId("red"));
        assertSame(GatePalette.BLUE, GatePalette.byId("unknown"), "blue is the default");
        assertNotEquals(GatePalette.BLUE, GatePalette.RED);
        for (GatePalette p : GatePalette.ALL) {
            assertTrue(lum(p.core()) > 0.9, p.id() + ": a near-white core");
            assertTrue(lum(p.depth()) < 0.12, p.id() + ": a dark outer vortex for depth");
            assertTrue(lum(p.arcs()) > lum(p.body()) && lum(p.veins()) > lum(p.body()), p.id() + ": highlights brighter than the body");
            assertEquals(8, List.of(p.core(), p.body(), p.veins(), p.depth(), p.rim(), p.arcs(), p.mist(), p.fragments()).stream().distinct().count(),
                    p.id() + ": every layer has its own colour (not one uniform tint)");
        }
        assertTrue(hue(GatePalette.BLUE.body()) > 200 && hue(GatePalette.BLUE.body()) < 235, "blue body hue");
        assertTrue(hue(GatePalette.RED.body()) < 10 || hue(GatePalette.RED.body()) > 350, "red body hue");
    }

    /** The blue default was tuned to the reference: its swirl pixels (saturated, lit) peak at hue 215°, mean 210°. */
    @Test
    void theBlueDefaultMatchesTheReferenceHue() {
        float referenceSwirlHue = 215;
        assertTrue(Math.abs(hue(GatePalette.BLUE.body()) - referenceSwirlHue) < 8, "body hue " + hue(GatePalette.BLUE.body()));
        assertTrue(Math.abs(hue(GatePalette.BLUE.rim()) - 200) < 15, "cyan rim, as the reference's glowing boundary");
    }

    @Test
    void oneRendererTintsEveryLayerFromTheInstancePalette() throws Exception {
        String r = read(J + "client/renderer/entity/gate/SoloGateRenderer.java");
        assertTrue(r.contains("state.palette = entity.palette();"), "per instance, read each frame");
        for (String layer : List.of("pal.mist()", "pal.depth()", "pal.body()", "pal.veins()", "pal.rim()", "pal.arcs()", "pal.core()")) {
            assertTrue(r.contains(layer), layer);
        }
        assertTrue(r.contains("int rgb = state.palette.fragments();"));
        assertFalse(r.contains("GatePalette.RED"), "no colour-specific branch in the renderer");
        assertFalse(r.contains("static GatePalette") || r.contains("static int frame"), "no shared mutable palette or animation state");
        String palette = read(J + "entity/gate/GatePalette.java");
        assertTrue(palette.contains("public record GatePalette("), "immutable");
    }

    @Test
    void theGateIsVisualOnly() throws Exception {
        String e = read(J + "entity/gate/SoloGateEntity.java");
        assertTrue(e.contains("public boolean isPickable() {\n        return false;"));
        assertTrue(e.contains("public boolean isPushable() {\n        return false;"));
        assertFalse(e.contains("changeDimension") || e.contains("teleportTo("));
        assertEquals(3.0F, SoloGateEntity.RADIUS * 2, 1e-6, "about three blocks tall");
        assertTrue(SoloGateEntity.CENTRE_HEIGHT > SoloGateEntity.RADIUS);
    }

    private static double lum(int rgb) {
        return (0.2126 * ((rgb >> 16) & 255) + 0.7152 * ((rgb >> 8) & 255) + 0.0722 * (rgb & 255)) / 255;
    }

    private static float hue(int rgb) {
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        return hsb[0] * 360;
    }
}

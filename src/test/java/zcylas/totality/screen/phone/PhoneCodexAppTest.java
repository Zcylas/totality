package zcylas.totality.screen.phone;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Codex replaces the Bestiary placeholder in the launcher (same slot), with one universal default icon, and does
 * nothing yet beyond the normal press feedback.
 */
class PhoneCodexAppTest {

    private static final Path GRID = Path.of("src/main/java/zcylas/totality/screen/phone/PhoneAppGridScreen.java");
    private static final Path ICON = Path.of("src/main/resources/assets/totality/textures/gui/sprites/phone/apps/codex.png");

    @Test
    void codexTakesTheBestiarySlotAndOpensNothing() throws Exception {
        String src = Files.readString(GRID);
        assertFalse(src.contains("new App(\"Bestiary\""), "no separate Bestiary app: it becomes a Codex category");
        assertTrue(src.contains("all.add(new App(\"Codex\",     true, null, null, CODEX_ICON));"), "unlocked, no action, the icon");
        int character = src.indexOf("new App(\"Character\""), quests = src.indexOf("new App(\"Quests\""),
                codex = src.indexOf("new App(\"Codex\""), spells = src.indexOf("new App(\"Spells\"");
        assertTrue(character < quests && quests < codex && codex < spells, "the fourth app, as the Bestiary was");
    }

    @Test
    void oneUniversalIconNotTiedToAPhoneTier() throws Exception {
        String src = Files.readString(GRID);
        assertTrue(src.contains("Identifier.fromNamespaceAndPath(\"totality\", \"phone/apps/codex\")"));
        assertFalse(src.contains("crude/codex"), "not a Crude-specific icon");
        assertFalse(Files.readString(Path.of("src/main/java/zcylas/totality/screen/phone/PhoneDeviceStyle.java")).contains("codex"),
                "the device style does not choose app artwork");
    }

    @Test
    void theIconIsAClean32PixelSprite() throws Exception {
        BufferedImage im = ImageIO.read(ICON.toFile());
        assertEquals(32, im.getWidth());
        assertEquals(32, im.getHeight());
        int opaque = 0;
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int a = im.getRGB(x, y) >>> 24;
                assertTrue(a == 0 || a == 255, "no soft (semi-transparent) pixels");
                if (a == 255) opaque++;
            }
        }
        for (int[] c : new int[][]{{0, 0}, {31, 0}, {0, 31}, {31, 31}}) {
            assertEquals(0, im.getRGB(c[0], c[1]) >>> 24, "transparent corners: no tile or background baked in");
        }
        assertTrue(opaque > 500 && opaque < 1024, "a book silhouette, not a filled square: " + opaque);
    }
}

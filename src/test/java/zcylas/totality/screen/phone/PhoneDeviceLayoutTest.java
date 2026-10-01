package zcylas.totality.screen.phone;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The opened phone keeps its smartphone proportions, stays on screen, and its sprites match its style. */
class PhoneDeviceLayoutTest {

    private static final Path SPRITES = Path.of("src/main/resources/assets/totality/textures/gui/sprites/phone/crude");

    @Test
    void keepsItsProportionsAndStaysOnScreen() {
        PhoneDeviceStyle style = PhoneDeviceStyle.CRUDE;
        for (int[] gui : new int[][] {{480, 253}, {480, 270}, {640, 360}, {854, 480}, {320, 240}}) {
            PhoneFrameRenderer.Layout l = PhoneFrameRenderer.layout(gui[0], gui[1], style);
            assertEquals(style.aspect(), l.w() / (float) l.h(), 0.01f, "device aspect at " + gui[0] + "x" + gui[1]);
            assertTrue(l.h() > l.w() * 1.9f, "a tall, narrow smartphone (not a tablet) at " + gui[0] + "x" + gui[1]);
            assertTrue(l.x() >= 0 && l.y() >= 0 && l.x() + l.w() + 2 <= gui[0] && l.y() + l.h() <= gui[1],
                    "the body (and its side buttons) stay on screen at " + gui[0] + "x" + gui[1]);
            assertEquals(l.x() + style.insetLeft(), l.dx());
            assertEquals(l.y() + style.insetTop(), l.dy());
            assertEquals(l.w() - style.insetLeft() - style.insetRight(), l.dw());
            assertEquals(l.h() - style.insetTop() - style.insetBottom(), l.dh());
        }
    }

    @Test
    void theOpenTransitionEndsFullyShownAndIsSkippedBetweenPhoneScreens() {
        assertEquals(PhoneFrameRenderer.Transition.NONE, PhoneFrameRenderer.transition(-1));
        PhoneFrameRenderer.Transition done = PhoneFrameRenderer.transition(System.nanoTime() - 2_000_000_000L);
        assertEquals(0, done.slide());
        assertEquals(1f, done.power());
    }

    @Test
    void crudeSpritesMatchTheStyle() throws Exception {
        PhoneDeviceStyle s = PhoneDeviceStyle.CRUDE;
        JsonObject scaling = JsonParser.parseString(Files.readString(SPRITES.resolve("frame.png.mcmeta")))
                .getAsJsonObject().getAsJsonObject("gui").getAsJsonObject("scaling");
        assertEquals("nine_slice", scaling.get("type").getAsString());
        assertFalse(scaling.get("stretch_inner").getAsBoolean(), "edges tile, never stretch the patina");
        JsonObject border = scaling.getAsJsonObject("border");
        assertEquals(s.insetLeft(), border.get("left").getAsInt());
        assertEquals(s.insetTop(), border.get("top").getAsInt());
        assertEquals(s.insetRight(), border.get("right").getAsInt());
        assertEquals(s.insetBottom(), border.get("bottom").getAsInt());
        assertSize("speaker.png", s.speakerWidth(), s.speakerHeight());
        assertSize("camera.png", s.cameraSize(), s.cameraSize());
        assertSize("grille.png", s.grilleWidth(), s.grilleHeight());
        assertTrue(Files.exists(SPRITES.resolve("../os/lock.png")), "the padlock is an OS glyph, not casing art");
    }

    private static void assertSize(String sprite, int w, int h) throws Exception {
        BufferedImage im = ImageIO.read(SPRITES.resolve(sprite).toFile());
        assertEquals(w, im.getWidth(), sprite);
        assertEquals(h, im.getHeight(), sprite);
    }
}

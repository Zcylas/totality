package zcylas.totality.client.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The shutter feedback timeline: flash, shrink, fly into the Gallery thumbnail, done; and the reduced variant. */
class ShutterAnimationTest {

    private static final ShutterAnimation.Rect SCREEN = new ShutterAnimation.Rect(0, 0, 480, 270);
    private static final ShutterAnimation.Rect THUMB = new ShutterAnimation.Rect(16, 222, 24, 24);

    @Test
    void flashesThenShrinksThenFliesIntoTheThumbnail() {
        ShutterAnimation.Frame start = ShutterAnimation.frame(0, SCREEN, THUMB, false);
        assertTrue(start.flash() > 0.8f, "an immediate flash confirms the shot");
        assertEquals(SCREEN, start.photo(), "the photo starts exactly over the view");
        assertFalse(start.done());

        ShutterAnimation.Frame held = ShutterAnimation.frame(ShutterAnimation.SHRINK_NANOS + 10_000_000L, SCREEN, THUMB, false);
        assertEquals(0f, held.flash(), "the flash is brief");
        assertEquals(480 * ShutterAnimation.SHRUNK, held.photo().w(), 0.01f, "the photo appears inset over the viewfinder");
        assertEquals(0f, held.crop(), "uncropped while shown");

        ShutterAnimation.Frame mid = ShutterAnimation.frame((ShutterAnimation.HOLD_NANOS + ShutterAnimation.FLY_NANOS) / 2, SCREEN, THUMB, false);
        assertTrue(mid.photo().w() < held.photo().w() && mid.photo().w() > THUMB.w(), "shrinking on the way");
        assertTrue(mid.photo().x() < held.photo().x(), "moving left, towards the shortcut");
        assertTrue(mid.photo().y() > held.photo().y(), "and down");

        ShutterAnimation.Frame landing = ShutterAnimation.frame(ShutterAnimation.FLY_NANOS - 1, SCREEN, THUMB, false);
        assertEquals(THUMB.x(), landing.photo().x(), 0.5f);
        assertEquals(THUMB.w(), landing.photo().w(), 0.5f);
        assertEquals(1f, landing.crop(), 0.01f, "it becomes the thumbnail's square crop");

        ShutterAnimation.Frame done = ShutterAnimation.frame(ShutterAnimation.FLY_NANOS, SCREEN, THUMB, false);
        assertTrue(done.done());
        assertNull(done.photo());
        assertTrue(ShutterAnimation.duration(false) < 700_000_000L, "responsive: well under a second");
    }

    @Test
    void reducedMotionSkipsTheFlight() {
        ShutterAnimation.Frame f = ShutterAnimation.frame(0, SCREEN, THUMB, true);
        assertNull(f.photo(), "no moving photo");
        assertTrue(f.flash() > 0, "still an immediate confirmation");
        assertTrue(ShutterAnimation.frame(ShutterAnimation.REDUCED_NANOS, SCREEN, THUMB, true).done());
        assertTrue(ShutterAnimation.duration(true) < ShutterAnimation.duration(false));
    }

    @Test
    void cropUvGoesFromTheWholeImageToItsCentredSquare() {
        float[] whole = ShutterAnimation.cropUv(1920, 1080, 0f);
        assertArrayEquals(new float[] {0, 0, 1, 1}, whole, 1e-6f);
        float[] square = ShutterAnimation.cropUv(1920, 1080, 1f);
        assertEquals(1080f / 1920f, square[2] - square[0], 1e-6f, "square: the visible width equals the height");
        assertEquals(0.5f, (square[0] + square[2]) / 2, 1e-6f, "centred");
        float[] tall = ShutterAnimation.cropUv(1080, 1920, 1f);
        assertEquals(1080f / 1920f, tall[3] - tall[1], 1e-6f, "portrait images crop vertically");
    }
}

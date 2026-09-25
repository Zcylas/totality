package zcylas.totality.client.tooltip.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static zcylas.totality.client.tooltip.preview.TooltipPreviewLayout.*;

class TooltipPreviewLayoutTest {

    @Test
    void viewportHeightIsTheFixedBaselineOnNormalScreens() {
        assertEquals(VIEWPORT_HEIGHT, viewportHeight(228));   // 240-high GUI (max scale) minus margins
        assertEquals(VIEWPORT_HEIGHT, viewportHeight(1000));
    }

    @Test
    void viewportHeightOnlyShrinksOnVeryShortScreensAndNeverBelowTheMinimum() {
        assertEquals(40, viewportHeight(160));
        assertEquals(MIN_VIEWPORT_HEIGHT, viewportHeight(20));
    }

    @Test
    void spriteMagnificationIsAnIntegerThatFitsTheViewport() {
        int w = 180, h = VIEWPORT_HEIGHT;
        int k = spriteScale(w, h);
        assertEquals(3, k, "56px viewport: 48px sprite = 3x, crisp integer magnification");
        assertTrue(16 * k <= h - MARGIN * 2);
        assertEquals(1, spriteScale(10, 10), "never below 1x");
        assertEquals(MAX_SPRITE_SCALE, spriteScale(1000, 1000));
    }

    @Test
    void longModelsScaleDownToFitWithoutCroppingAndKeepProportions() {
        // A long weapon: 5.0 model units wide, 0.5 tall (wide enough that width, not the up-scale cap, binds).
        float scale = fitScale(5.0f, 0.5f, 180, VIEWPORT_HEIGHT);
        assertTrue(5.0f * scale <= 180 - MARGIN * 2 + 1e-3);
        assertTrue(0.5f * scale <= VIEWPORT_HEIGHT - MARGIN * 2 + 1e-3);
        assertEquals((180 - MARGIN * 2) / 5.0f, scale, 1e-3, "width is the binding constraint; one scale for both axes");
    }

    @Test
    void smallModelsScaleUpButAreCapped() {
        float scale = fitScale(0.25f, 0.25f, 180, VIEWPORT_HEIGHT);
        assertEquals(MAX_MODEL_SCALE, scale);
        assertTrue(fitScale(1f, 1f, 180, VIEWPORT_HEIGHT) > STANDARD_ITEM_SCALE, "a normal cube is shown larger than a slot icon");
    }

    @Test
    void degenerateBoundsFallBackSafely() {
        float scale = fitScale(0f, 0f, 180, VIEWPORT_HEIGHT);
        assertTrue(scale > 0 && Float.isFinite(scale));
        assertTrue(Float.isFinite(fitScale(Float.NaN, 1f, 180, VIEWPORT_HEIGHT)));
    }

    @Test
    void rotatingFitUsesTheBoundingSphereSoNoAngleCanOverflow() {
        float radius = 0.9f;
        float scale = rotatingFitScale(radius, 180, VIEWPORT_HEIGHT);
        assertTrue(2 * radius * scale <= VIEWPORT_HEIGHT - MARGIN * 2 + 1e-3);
    }

    @Test
    void blockViewExtentsMatchTheStandardGuiBlockView() {
        // A full cube seen at [30, 225, 0]: width is the face diagonal, height is cos30 + sin30·√2.
        float[] cube = blockViewExtents(1f, 1f, 1f);
        assertEquals((float) Math.sqrt(2), cube[0], 1e-4);
        assertEquals((float) (Math.cos(Math.toRadians(30)) + Math.sin(Math.toRadians(30)) * Math.sqrt(2)), cube[1], 1e-4);
        // A fence post (4/16 wide, full height) is tall and narrow; a two-block door is twice as tall as it is shown wide.
        float[] post = blockViewExtents(0.25f, 1f, 0.25f);
        assertTrue(post[1] > 2.5f * post[0]);
        assertTrue(blockViewExtents(1f, 2f, 0.1875f)[1] > cube[1]);
    }

    @Test
    void turntableIsSlowPeriodicAndStartsAtZero() {
        assertEquals(0f, turntableDegrees(0));
        assertEquals(0f, turntableDegrees(TURNTABLE_PERIOD_MS));
        assertEquals(180f, turntableDegrees(TURNTABLE_PERIOD_MS / 2), 1e-3);
        assertTrue(TURNTABLE_PERIOD_MS >= 10_000, "a calm display turntable, not a fast item spin");
        for (long t = -50_000; t < 50_000; t += 777) {
            float deg = turntableDegrees(t);
            assertTrue(deg >= 0f && deg < 360f);
        }
    }
}

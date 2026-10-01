package zcylas.totality.client.camera;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Camera zoom: presets, wheel, smoothing, and the real projection behind each magnification. */
class CameraZoomTest {

    @Test
    void basicPhoneHasTheThreeSmartphonePresets() {
        assertEquals(List.of(0.5f, 1f, 2f), CameraZoom.BASIC.presets());
        assertEquals(0.5f, CameraZoom.BASIC.min());
        assertEquals(2f, CameraZoom.BASIC.max());
    }

    @Test
    void presetStepsWalkThePresetsAndStopAtTheEnds() {
        CameraZoom z = new CameraZoom(CameraZoom.BASIC);
        assertEquals(1f, z.target());
        z.stepPreset(1);
        assertEquals(2f, z.target());
        z.stepPreset(1);
        assertEquals(2f, z.target(), "no preset beyond 2x on the Basic phone");
        z.stepPreset(-1);
        z.stepPreset(-1);
        assertEquals(0.5f, z.target());
        z.stepPreset(-1);
        assertEquals(0.5f, z.target());
        // From an in-between wheel value, presets go to the next one in that direction.
        z.snapTo(1.4f);
        z.stepPreset(-1);
        assertEquals(1f, z.target());
    }

    @Test
    void wheelIsMultiplicativeAndClamped() {
        CameraZoom z = new CameraZoom(CameraZoom.BASIC);
        z.wheel(6);
        assertEquals(2f, z.target(), 1e-4, "six notches double the zoom");
        z.wheel(-6);
        assertEquals(1f, z.target(), 1e-4);
        z.wheel(-6);
        assertEquals(0.5f, z.target(), 1e-4, "and halve it");
        z.wheel(-20);
        assertEquals(0.5f, z.target(), "clamped at the widest");
        z.wheel(100);
        assertEquals(2f, z.target(), "clamped at the closest");
    }

    @Test
    void currentFollowsTheTargetSmoothlyAndArrives() {
        CameraZoom z = new CameraZoom(CameraZoom.BASIC);
        long t = 1_000_000_000L;
        z.update(t);
        z.setTarget(2f);
        float previous = z.current();
        int frames = 0;
        while (z.current() != 2f && frames < 200) {
            t += 16_666_667L;
            float now = z.update(t);
            assertTrue(now >= previous && now <= 2f, "moves monotonically towards the target");
            previous = now;
            frames++;
        }
        assertEquals(2f, z.current(), "arrives exactly");
        assertTrue(frames > 3, "it is a transition, not a jump (" + frames + " frames)");
        assertTrue(frames < 40, "and a quick one (" + frames + " frames at 60 fps)");
    }

    @Test
    void magnificationIsARealProjectionChange() {
        assertEquals(70f, CameraZoom.fov(70f, 1f), 1e-4, "1x is the player's own FOV setting");
        // tan(fov/2) scales by 1/zoom.
        assertEquals(2 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(35)) * 2)), CameraZoom.fov(70f, 0.5f), 1e-3);
        assertEquals(2 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(35)) / 2)), CameraZoom.fov(70f, 2f), 1e-3);
        assertTrue(CameraZoom.fov(70f, 0.5f) > 100f, "0.5x is genuinely wider (about 109 degrees from 70)");
        assertTrue(CameraZoom.fov(110f, 0.5f) <= CameraZoom.MAX_FOV, "capped at the widest sane projection");
        assertTrue(CameraZoom.fov(110f, 0.5f) > 135f, "still wider than the 110-degree setting");
    }

    @Test
    void lookSensitivitySlowsOnlyWhenMagnified() {
        assertEquals(1.0, CameraZoom.sensitivity(0.5f));
        assertEquals(1.0, CameraZoom.sensitivity(1f));
        assertEquals(0.5, CameraZoom.sensitivity(2f), 1e-9);
    }

    @Test
    void compactIndicatorLabels() {
        assertEquals("0.5×", CameraZoom.label(0.5f));
        assertEquals("1×", CameraZoom.label(1f));
        assertEquals("2×", CameraZoom.label(2f));
        assertEquals("1.4×", CameraZoom.label(1.4142f));
        assertEquals("1×", CameraZoom.label(0.999f));
    }

    @Test
    void nearestPresetIsLogarithmic() {
        CameraZoom z = new CameraZoom(CameraZoom.BASIC);
        z.snapTo(0.69f);
        assertEquals(0, z.nearestPreset(), "0.69 is closer to 0.5 than to 1 on a log scale");
        z.snapTo(0.72f);
        assertEquals(1, z.nearestPreset());
        z.snapTo(1.5f);
        assertEquals(2, z.nearestPreset());
    }

    @Test
    void futureTiersCanWidenTheRange() {
        CameraZoom.Range flagship = new CameraZoom.Range(0.5f, 10f, List.of(0.5f, 1f, 2f, 5f));
        CameraZoom z = new CameraZoom(flagship);
        z.stepPreset(1);
        z.stepPreset(1);
        z.stepPreset(1);
        assertEquals(5f, z.target());
        assertThrows(IllegalArgumentException.class, () -> new CameraZoom.Range(2f, 1f, List.of(1f)));
    }
}

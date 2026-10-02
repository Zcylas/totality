package zcylas.totality.client.vfx.glow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EmissiveGlowSettingsTest {

    @TempDir
    Path dir;

    @Test
    void defaultsWhenTheFileIsMissing() {
        EmissiveGlowSettings s = EmissiveGlowSettings.load(dir.resolve("missing.properties"));
        assertTrue(s.enabled());
        assertEquals(EmissiveGlowSettings.DEFAULT_INTENSITY, s.intensity());
        assertEquals(EmissiveGlowSettings.DEFAULT_LEVELS, s.levels());
        assertTrue(s.active());
    }

    @Test
    void savedValuesRoundTrip() throws IOException {
        Path file = dir.resolve("config").resolve("totality-vfx.properties");
        EmissiveGlowSettings s = new EmissiveGlowSettings(file);
        s.setEnabled(false);
        s.setIntensity(2.5f);
        s.setLevels(3);
        s.save();
        EmissiveGlowSettings loaded = EmissiveGlowSettings.load(file);
        assertFalse(loaded.enabled());
        assertEquals(2.5f, loaded.intensity());
        assertEquals(3, loaded.levels());
        assertFalse(loaded.active(), "disabled layer never runs");
    }

    @Test
    void outOfRangeAndInvalidValuesAreClampedOrIgnored() throws IOException {
        Path file = dir.resolve("vfx.properties");
        Files.writeString(file, "glow.enabled=yes\nglow.intensity=99\nglow.levels=-4\n");
        EmissiveGlowSettings s = EmissiveGlowSettings.load(file);
        assertTrue(s.enabled(), "anything but false keeps the layer on");
        assertEquals(EmissiveGlowSettings.MAX_INTENSITY, s.intensity());
        assertEquals(EmissiveGlowSettings.MIN_LEVELS, s.levels());

        Files.writeString(file, "glow.intensity=bright\nglow.levels=lots\n");
        s = EmissiveGlowSettings.load(file);
        assertEquals(EmissiveGlowSettings.DEFAULT_INTENSITY, s.intensity());
        assertEquals(EmissiveGlowSettings.DEFAULT_LEVELS, s.levels());

        s.setIntensity(Float.NaN);
        assertEquals(EmissiveGlowSettings.DEFAULT_INTENSITY, s.intensity());
        s.setLevels(50);
        assertEquals(EmissiveGlowSettings.MAX_LEVELS, s.levels());
    }

    @Test
    void zeroIntensityMeansNoPasses() {
        EmissiveGlowSettings s = new EmissiveGlowSettings(dir.resolve("x.properties"));
        s.setIntensity(0.0f);
        assertTrue(s.enabled());
        assertFalse(s.active());
    }
}

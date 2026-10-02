package zcylas.totality.client.vfx.screen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ScreenFxSettingsTest {

    @Test
    void defaultsSaveLoadAndClamp(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("totality-screenfx.properties");
        ScreenFxSettings d = ScreenFxSettings.load(f);
        assertEquals(1.0f, d.shake());
        assertEquals(1.0f, d.flash());
        assertFalse(d.impactFrames());
        d.setShake(0.25f);
        d.setFlash(7f);
        d.save();
        ScreenFxSettings r = ScreenFxSettings.load(f);
        assertEquals(0.25f, r.shake());
        assertEquals(1.0f, r.flash(), "clamped to [0, 1]");
        Files.writeString(f, "shake=abc\n");
        assertEquals(1.0f, ScreenFxSettings.load(f).shake(), "unreadable values fall back to defaults");
    }
}

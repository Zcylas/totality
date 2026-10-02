package zcylas.totality.client.vfx.glow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlowChainLayoutTest {

    @Test
    void levelsHalveFromHalfResolution() {
        assertEquals(960, GlowChainLayout.levelWidth(1920, 0));
        assertEquals(540, GlowChainLayout.levelHeight(1080, 0));
        assertEquals(60, GlowChainLayout.levelWidth(1920, 4));
        assertEquals(33, GlowChainLayout.levelHeight(1080, 4));
    }

    @Test
    void levelsNeverReachZero() {
        assertEquals(1, GlowChainLayout.levelWidth(3, 5));
        assertEquals(1, GlowChainLayout.levelHeight(1, 0));
    }

    @Test
    void memoryAt1080pWithFiveLevelsIsUnderTwentyTwoMebibytes() {
        // Emissive 1920x1080 + 960x540 + 480x270 + 240x135 + 120x67 + 60x33, 8 bytes per pixel (RGBA16F).
        long expected = (1920L * 1080 + 960 * 540 + 480 * 270 + 240 * 135 + 120 * 67 + 60 * 33) * 8;
        assertEquals(expected, GlowChainLayout.bytes(1920, 1080, 5, 8));
        assertTrue(expected < 22L * 1024 * 1024);
    }
}

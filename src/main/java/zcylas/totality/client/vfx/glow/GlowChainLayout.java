package zcylas.totality.client.vfx.glow;

/**
 * Sizes of the glow blur chain: level {@code i} (0-based) is the screen size halved {@code i + 1} times, never smaller
 * than one pixel. Level 0 is half resolution, so the full-resolution emissive buffer is the only full-size target.
 */
public final class GlowChainLayout {

    private GlowChainLayout() {}

    public static int levelWidth(int screenWidth, int level) {
        return Math.max(1, screenWidth >> (level + 1));
    }

    public static int levelHeight(int screenHeight, int level) {
        return Math.max(1, screenHeight >> (level + 1));
    }

    /** Bytes of GPU memory for the emissive buffer plus {@code levels} chain targets, at {@code bytesPerPixel}. */
    public static long bytes(int screenWidth, int screenHeight, int levels, int bytesPerPixel) {
        long total = (long) screenWidth * screenHeight;
        for (int i = 0; i < levels; i++) {
            total += (long) levelWidth(screenWidth, i) * levelHeight(screenHeight, i);
        }
        return total * bytesPerPixel;
    }
}

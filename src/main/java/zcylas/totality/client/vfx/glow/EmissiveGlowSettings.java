package zcylas.totality.client.vfx.glow;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Client-only settings of the experimental emissive glow layer, stored in {@code config/totality-vfx.properties}.
 *
 * <ul>
 *   <li>{@code glow.enabled} (default true): when false no glow pass runs at all; emissive sources still render their
 *       ordinary geometry, so the frame is exactly the normal frame.</li>
 *   <li>{@code glow.intensity} (default 1.0, clamped to [0, 4]): strength of the glow added to the image.</li>
 *   <li>{@code glow.levels} (default 5, clamped to [2, 6]): length of the blur chain; more levels give a wider glow
 *       at a small extra cost.</li>
 * </ul>
 */
public final class EmissiveGlowSettings {

    public static final float MAX_INTENSITY = 4.0f;
    public static final int MIN_LEVELS = 2;
    public static final int MAX_LEVELS = 6;
    static final float DEFAULT_INTENSITY = 1.0f;
    static final int DEFAULT_LEVELS = 5;

    private final Path file;
    private volatile boolean enabled = true;
    private volatile float intensity = DEFAULT_INTENSITY;
    private volatile int levels = DEFAULT_LEVELS;

    public EmissiveGlowSettings(Path file) {
        this.file = file;
    }

    public static EmissiveGlowSettings load(Path file) {
        EmissiveGlowSettings s = new EmissiveGlowSettings(file);
        if (!Files.isRegularFile(file)) return s;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | IllegalArgumentException e) {
            return s; // unreadable settings fall back to defaults
        }
        s.enabled = !"false".equalsIgnoreCase(p.getProperty("glow.enabled", "true").trim());
        s.setIntensity(parseFloat(p.getProperty("glow.intensity"), DEFAULT_INTENSITY));
        s.setLevels(parseInt(p.getProperty("glow.levels"), DEFAULT_LEVELS));
        return s;
    }

    public synchronized void save() throws IOException {
        Properties p = new Properties();
        p.setProperty("glow.enabled", Boolean.toString(enabled));
        p.setProperty("glow.intensity", Float.toString(intensity));
        p.setProperty("glow.levels", Integer.toString(levels));
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            p.store(w, "Totality VFX (client only). Experimental emissive glow layer.");
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public float intensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = Float.isFinite(intensity) ? Math.clamp(intensity, 0.0f, MAX_INTENSITY) : DEFAULT_INTENSITY;
    }

    public int levels() {
        return levels;
    }

    public void setLevels(int levels) {
        this.levels = Math.clamp(levels, MIN_LEVELS, MAX_LEVELS);
    }

    /** True when the glow passes should run: enabled and a visible intensity. */
    public boolean active() {
        return enabled && intensity > 0.0f;
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null) return fallback;
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

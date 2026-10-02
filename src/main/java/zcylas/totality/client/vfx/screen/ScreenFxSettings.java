package zcylas.totality.client.vfx.screen;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * Totality's own Shared Screen FX controls, stored in {@code config/totality-screenfx.properties} (a separate file:
 * the Emissive Rendering Layer's settings file is rewritten whole when it is saved). They multiply Minecraft's own
 * accessibility options, which are always respected (Distortion Effects scales shake; Hide Lightning Flashes turns
 * flashes off).
 *
 * <ul>
 *   <li>{@code shake} (default 1.0, [0, 1]): camera shake scale; 0 disables it;</li>
 *   <li>{@code flash} (default 1.0, [0, 1]): screen flash scale; 0 disables it;</li>
 *   <li>{@code impactFrames} (default false): allows future impact-frame requests (none are rendered yet).</li>
 * </ul>
 */
public final class ScreenFxSettings {

    private final Path file;
    private volatile float shake = 1.0f;
    private volatile float flash = 1.0f;
    private volatile boolean impactFrames;

    public ScreenFxSettings(Path file) {
        this.file = file;
    }

    public static ScreenFxSettings load(Path file) {
        ScreenFxSettings s = new ScreenFxSettings(file);
        if (!Files.isRegularFile(file)) return s;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | IllegalArgumentException e) {
            return s; // unreadable settings fall back to defaults
        }
        s.setShake(parse(p.getProperty("shake"), 1.0f));
        s.setFlash(parse(p.getProperty("flash"), 1.0f));
        s.impactFrames = "true".equalsIgnoreCase(p.getProperty("impactFrames", "false").trim());
        return s;
    }

    public synchronized void save() throws IOException {
        Properties p = new Properties();
        p.setProperty("shake", Float.toString(shake));
        p.setProperty("flash", Float.toString(flash));
        p.setProperty("impactFrames", Boolean.toString(impactFrames));
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            p.store(w, "Totality Shared Screen FX (client only)");
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static float parse(String v, float def) {
        if (v == null) return def;
        try {
            return Float.parseFloat(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public float shake() {
        return shake;
    }

    public float flash() {
        return flash;
    }

    public boolean impactFrames() {
        return impactFrames;
    }

    public void setShake(float v) {
        shake = Float.isNaN(v) ? 1.0f : Math.clamp(v, 0.0f, 1.0f);
    }

    public void setFlash(float v) {
        flash = Float.isNaN(v) ? 1.0f : Math.clamp(v, 0.0f, 1.0f);
    }

    public void setImpactFrames(boolean on) {
        impactFrames = on;
    }

    /** Effective shake multiplier: Totality's setting × Minecraft's Distortion Effects (0..1). */
    public double shakeScale(double distortionEffects) {
        return shake * Math.clamp(distortionEffects, 0.0, 1.0);
    }

    /** Effective flash multiplier: Totality's setting, or 0 when Minecraft's Hide Lightning Flashes is on. */
    public double flashScale(boolean hideLightningFlashes) {
        return hideLightningFlashes ? 0.0 : flash;
    }
}

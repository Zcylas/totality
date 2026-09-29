package zcylas.totality.client.voice;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;

/**
 * Client-only Voice Input settings, stored in {@code config/totality-voice.properties}.
 *
 * <ul>
 *   <li>{@code enabled} (default true): Voice Input is available; nothing loads until push-to-talk is
 *       first pressed. When false the microphone is never opened and the model is unloaded.</li>
 *   <li>{@code device} (default empty = system default microphone).</li>
 *   <li>{@code debug} (default false; also {@code -Dtotality.voice.debug=true}): extra diagnostics,
 *       including transcripts, in the log and in chat.</li>
 *   <li>{@code debug.saveWav} (default false): writes each utterance to {@code totality/voice/debug/}.
 *       Only takes effect together with {@code debug}.</li>
 * </ul>
 */
public final class VoiceSettings {

    private final Path file;
    private volatile boolean enabled = true;
    private volatile String device = "";
    private volatile boolean debug;
    private volatile boolean saveDebugWav;

    public VoiceSettings(Path file) {
        this.file = file;
    }

    public static VoiceSettings load(Path file) {
        VoiceSettings s = new VoiceSettings(file);
        if (!Files.isRegularFile(file)) return s;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException | IllegalArgumentException e) {
            return s; // unreadable settings fall back to defaults
        }
        s.enabled = !"false".equalsIgnoreCase(p.getProperty("enabled", "true").trim());
        s.device = p.getProperty("device", "").trim();
        s.debug = "true".equalsIgnoreCase(p.getProperty("debug", "false").trim());
        s.saveDebugWav = "true".equalsIgnoreCase(p.getProperty("debug.saveWav", "false").trim());
        return s;
    }

    public synchronized void save() throws IOException {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        p.setProperty("device", device);
        p.setProperty("debug", Boolean.toString(debug));
        p.setProperty("debug.saveWav", Boolean.toString(saveDebugWav));
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            p.store(w, "Totality Voice Input (client only). Push-to-talk; audio never leaves this computer.");
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** The chosen microphone, or null for the system default. */
    @Nullable
    public String device() {
        return device.isEmpty() ? null : device;
    }

    public void setDevice(@Nullable String device) {
        this.device = device == null ? "" : device;
    }

    public boolean debug() {
        return debug || Boolean.getBoolean("totality.voice.debug");
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean saveDebugWav() {
        return saveDebugWav && debug();
    }

    public void setSaveDebugWav(boolean saveDebugWav) {
        this.saveDebugWav = saveDebugWav;
    }

    /**
     * Which device to open: the chosen one if it is currently present, otherwise the system default
     * (null). {@code fellBack} tells the caller to explain the substitution.
     */
    public record DeviceChoice(@Nullable String device, boolean fellBack) {}

    public static DeviceChoice resolveDevice(@Nullable String chosen, List<String> available) {
        if (chosen == null || chosen.isEmpty()) return new DeviceChoice(null, false);
        if (available.contains(chosen)) return new DeviceChoice(chosen, false);
        return new DeviceChoice(null, true);
    }
}

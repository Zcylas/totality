package zcylas.totality.api.voice.recognition;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Detects a native call that took the whole JVM down on a previous launch.
 *
 * <p>A native recognition engine that hits a fatal error inside C++ aborts the process; no Java
 * {@code catch} ever runs. So before a risky native operation (loading the library, opening the
 * model) a marker file is written, and it is deleted once the operation returns. Finding the marker
 * on a later launch means the earlier attempt never returned — most likely it crashed. The owning
 * backend then refuses to retry automatically, so a bad model or incompatible native library cannot
 * crash every launch in a row. An explicit player action ({@link #clear()}) allows a retry.
 *
 * <p>An orderly client shutdown that happens while a guarded call is still running (the player
 * quit during loading) annotates the marker with {@link #INTERRUPTED_LINE} via
 * {@link #markInterruptedByCleanShutdown()}; the next launch reports that as
 * {@link PreviousAttempt#INTERRUPTED_BY_SHUTDOWN}, not as a crash. A genuine native abort never
 * reaches that code, so its marker stays unannotated.
 */
public final class NativeCrashGuard {

    public static final String INTERRUPTED_LINE = "interrupted-by-clean-shutdown=true";

    public enum PreviousAttempt {
        /** No marker: the last guarded call returned (or none was made). */
        NONE,
        /** The last guarded call never returned and the process did not shut down cleanly. */
        SUSPECTED_CRASH,
        /** The last guarded call was still running when the client shut down normally. */
        INTERRUPTED_BY_SHUTDOWN
    }

    private final Path marker;
    private boolean active;

    public NativeCrashGuard(Path marker) {
        this.marker = marker;
    }

    public Path marker() {
        return marker;
    }

    public PreviousAttempt previousAttempt() {
        if (!Files.exists(marker)) return PreviousAttempt.NONE;
        return previousAttemptDescription().contains(INTERRUPTED_LINE)
                ? PreviousAttempt.INTERRUPTED_BY_SHUTDOWN
                : PreviousAttempt.SUSPECTED_CRASH;
    }

    /** True only for a marker left by an attempt that did not end in a clean shutdown. */
    public boolean previousAttemptCrashed() {
        return previousAttempt() == PreviousAttempt.SUSPECTED_CRASH;
    }

    /** What the unfinished attempt was doing, or empty if unreadable. */
    public String previousAttemptDescription() {
        try {
            return Files.readString(marker, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    public synchronized void begin(String operation) throws IOException {
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, operation + "\nstarted=" + Instant.now() + "\n", StandardCharsets.UTF_8);
        active = true;
    }

    public synchronized void end() throws IOException {
        active = false;
        Files.deleteIfExists(marker);
    }

    /**
     * Called during an orderly client shutdown. If a guarded call is running right now, annotates
     * its marker so the next launch does not mistake the interruption for a crash.
     *
     * @return true if a running call was annotated
     */
    public synchronized boolean markInterruptedByCleanShutdown() {
        if (!active) return false;
        try {
            Files.writeString(marker, INTERRUPTED_LINE + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Explicit reset (player-requested retry). Only ever deletes the marker file itself. */
    public synchronized void clear() throws IOException {
        active = false;
        Files.deleteIfExists(marker);
    }
}

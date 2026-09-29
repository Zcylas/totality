package zcylas.totality.client.voice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zcylas.totality.api.voice.model.ModelIntegrityException;
import zcylas.totality.api.voice.recognition.BackendStatus;
import zcylas.totality.api.voice.recognition.NativeCrashGuard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Failure branches of the Vosk backend that are decided before any native code is touched, so they
 * run in the ordinary suite. (Runs on Linux/Windows x86-64 dev machines; the platform gate itself is
 * covered by {@link VoskResultParserTest#onlyLinuxAndWindowsX64AreSupported()}.)
 */
class VoskRecognitionBackendFailureTest {

    private static final Executor DIRECT_THREAD = r -> new Thread(r, "vosk-failure-test").start();

    @Test
    void leftoverCrashMarkerBlocksLoadingAndInvalidatesTheModel(@TempDir Path dir) throws Exception {
        NativeCrashGuard guard = new NativeCrashGuard(dir.resolve("native-load.marker"));
        guard.begin("vosk native load + model open: /somewhere"); // as if the JVM died here last launch
        AtomicInteger located = new AtomicInteger();
        AtomicInteger invalidated = new AtomicInteger();
        VoskRecognitionBackend backend = new VoskRecognitionBackend(() -> {
            located.incrementAndGet();
            return dir;
        }, guard, invalidated::incrementAndGet, DIRECT_THREAD);

        BackendStatus s = backend.load().get(10, TimeUnit.SECONDS);
        assertEquals(BackendStatus.FailureReason.CRASH_SUSPECTED, s.reason());
        assertTrue(s.detail().contains("native-load.marker"), "the status must tell the player how to retry");
        assertEquals(0, located.get(), "no model work after a suspected crash");
        assertEquals(1, invalidated.get(), "the model install is invalidated so a retry re-extracts it");
        assertTrue(Files.exists(guard.marker()), "the marker stays until the player removes it");
        backend.close();
    }

    @Test
    void integrityFailureIsModelInvalidAndLeavesNoCrashMarker(@TempDir Path dir) throws Exception {
        NativeCrashGuard guard = new NativeCrashGuard(dir.resolve("native-load.marker"));
        VoskRecognitionBackend backend = new VoskRecognitionBackend(() -> {
            throw new ModelIntegrityException("sha mismatch");
        }, guard, () -> {}, DIRECT_THREAD);
        BackendStatus s = backend.load().get(10, TimeUnit.SECONDS);
        assertEquals(BackendStatus.FailureReason.MODEL_INVALID, s.reason());
        assertTrue(s.detail().contains("sha mismatch"));
        assertFalse(Files.exists(guard.marker()));
    }

    @Test
    void unavailableModelIsModelUnavailable(@TempDir Path dir) throws Exception {
        NativeCrashGuard guard = new NativeCrashGuard(dir.resolve("native-load.marker"));
        VoskRecognitionBackend backend = new VoskRecognitionBackend(() -> {
            throw new IOException("Bundled model manifest missing from the jar");
        }, guard, () -> {}, DIRECT_THREAD);
        BackendStatus s = backend.load().get(10, TimeUnit.SECONDS);
        assertEquals(BackendStatus.FailureReason.MODEL_UNAVAILABLE, s.reason());
        assertFalse(Files.exists(guard.marker()));
    }
}

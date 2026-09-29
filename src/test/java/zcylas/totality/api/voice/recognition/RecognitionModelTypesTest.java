package zcylas.totality.api.voice.recognition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Requests, transcripts and the native crash guard. */
class RecognitionModelTypesTest {

    @Test
    void grammarPhrasesAreNormalized() {
        RecognitionRequest r = RecognitionRequest.grammar(Set.of("  Confirm ", "SELECT   second", "", "confirm"));
        assertEquals(RecognitionMode.GRAMMAR, r.mode());
        assertEquals(Set.of("confirm", "select second"), r.phrases());
    }

    @Test
    void invalidRequestsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> RecognitionRequest.grammar(Set.of()));
        assertThrows(IllegalArgumentException.class, () -> RecognitionRequest.grammar(Set.of("  ")));
        assertThrows(IllegalArgumentException.class,
                () -> new RecognitionRequest(RecognitionMode.FREEFORM, Set.of("confirm")));
        assertTrue(RecognitionRequest.freeform().phrases().isEmpty());
    }

    @Test
    void averageConfidenceIgnoresMissingValues() {
        Transcript t = new Transcript(" hello  ", RecognitionMode.FREEFORM, List.of(
                new Transcript.Word("hello", 0, 0.5, 0.8),
                new Transcript.Word("there", 0.5, 1, Double.NaN),
                new Transcript.Word("friend", 1, 1.5, 0.6)), false);
        assertEquals("hello", t.text());
        assertEquals(0.7, t.averageConfidence().orElseThrow(), 1e-9);
        assertTrue(new Transcript("", RecognitionMode.FREEFORM, null, true).averageConfidence().isEmpty());
    }

    @Test
    void crashGuardMarkerSurvivesOnlyAnUnfinishedAttempt(@TempDir Path dir) throws Exception {
        Path marker = dir.resolve("voice").resolve("native-load.marker");
        NativeCrashGuard guard = new NativeCrashGuard(marker);
        assertFalse(guard.previousAttemptCrashed());

        guard.begin("open model X");
        guard.end();
        assertFalse(guard.previousAttemptCrashed(), "a completed attempt leaves no marker");

        guard.begin("open model X");
        // Simulate the process dying here: a fresh guard (next launch) finds the marker.
        NativeCrashGuard nextLaunch = new NativeCrashGuard(marker);
        assertTrue(nextLaunch.previousAttemptCrashed());
        assertTrue(nextLaunch.previousAttemptDescription().startsWith("open model X"));

        Files.delete(marker); // what a player does to retry
        assertFalse(new NativeCrashGuard(marker).previousAttemptCrashed());
    }

    @Test
    void cleanShutdownDuringAGuardedCallIsNotACrash(@TempDir Path dir) throws Exception {
        Path marker = dir.resolve("native-load.marker");
        NativeCrashGuard guard = new NativeCrashGuard(marker);
        assertFalse(guard.markInterruptedByCleanShutdown(), "nothing to annotate when no call is running");
        assertFalse(Files.exists(marker));

        guard.begin("open model X");
        assertTrue(guard.markInterruptedByCleanShutdown());
        // The process exits here (clean shutdown while loading). Next launch:
        NativeCrashGuard next = new NativeCrashGuard(marker);
        assertEquals(NativeCrashGuard.PreviousAttempt.INTERRUPTED_BY_SHUTDOWN, next.previousAttempt());
        assertFalse(next.previousAttemptCrashed());

        guard.begin("open model X"); // a new attempt writes a fresh, unannotated marker
        assertEquals(NativeCrashGuard.PreviousAttempt.SUSPECTED_CRASH, new NativeCrashGuard(marker).previousAttempt());
        guard.end();
        assertEquals(NativeCrashGuard.PreviousAttempt.NONE, new NativeCrashGuard(marker).previousAttempt());
    }

    @Test
    void annotationAfterTheCallReturnedDoesNotRecreateTheMarker(@TempDir Path dir) throws Exception {
        NativeCrashGuard guard = new NativeCrashGuard(dir.resolve("native-load.marker"));
        guard.begin("open model X");
        guard.end();
        assertFalse(guard.markInterruptedByCleanShutdown());
        assertFalse(Files.exists(guard.marker()));
    }

    @Test
    void clearOnlyRemovesTheMarker(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("unrelated.txt"), "keep");
        NativeCrashGuard guard = new NativeCrashGuard(dir.resolve("native-load.marker"));
        guard.begin("x");
        guard.clear();
        assertEquals(NativeCrashGuard.PreviousAttempt.NONE, guard.previousAttempt());
        assertEquals("keep", Files.readString(dir.resolve("unrelated.txt")));
    }
}

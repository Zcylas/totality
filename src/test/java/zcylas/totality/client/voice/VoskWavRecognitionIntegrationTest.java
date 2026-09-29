package zcylas.totality.client.voice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Native integration test: real Vosk natives, the real bundled model, a real spoken recording.
 * Isolated from the ordinary suite — it only runs when {@code TOTALITY_VOICE_TEST_WAV} points to a
 * 16 kHz 16-bit PCM WAV of spoken English. Optionally {@code TOTALITY_VOICE_TEST_EXPECT} lists words
 * (space-separated) the transcript must contain. The report is written to
 * {@code build/voice-test/wav-integration-report.txt}.
 */
@EnabledIfEnvironmentVariable(named = "TOTALITY_VOICE_TEST_WAV", matches = ".+")
class VoskWavRecognitionIntegrationTest {

    @Test
    void decodesRealSpeechFromTheBundledModel(@TempDir Path voiceRoot) throws Exception {
        Path wav = Path.of(System.getenv("TOTALITY_VOICE_TEST_WAV"));
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Totality-Voice-Loader-Test"));
        try {
            VoiceWavTranscriptionProbe.Result first = VoiceWavTranscriptionProbe.run(voiceRoot, wav, executor, () -> 0);
            VoiceWavTranscriptionProbe.Result second = VoiceWavTranscriptionProbe.run(voiceRoot, wav, executor, () -> 0);

            Path out = Path.of("build", "voice-test");
            Files.createDirectories(out);
            Files.writeString(out.resolve("wav-integration-report.txt"),
                    "wav=" + wav.toAbsolutePath() + "\n\n# first run (fresh extraction)\n" + first.toReport()
                            + "\n# second run (same voice root)\n" + second.toReport());

            assertTrue(first.status() != null && first.status().isReady(), "backend not ready: " + first.toReport());
            assertTrue(first.success(), "no speech decoded: " + first.toReport());
            assertFalse(first.installReused(), "first run must extract the model");
            assertTrue(second.installReused(), "second run must reuse the verified extraction");
            assertEquals(first.transcript(), second.transcript(), "same audio, same model, same result");
            assertTrue(first.grammarUnrecognized(), "speech outside a [confirm, cancel] grammar must be rejected");

            String expect = System.getenv("TOTALITY_VOICE_TEST_EXPECT");
            if (expect != null && !expect.isBlank()) {
                for (String word : expect.toLowerCase(Locale.ROOT).split("\\s+")) {
                    assertTrue((" " + first.transcript() + " ").contains(" " + word + " "),
                            "expected '" + word + "' in transcript: " + first.transcript());
                }
            }
            assertTrue(first.negativeChecks().stream().anyMatch(c -> c.startsWith("missing model path -> BackendStatus[state=FAILED")),
                    String.valueOf(first.negativeChecks()));
            assertTrue(first.negativeChecks().stream().filter(c -> c.contains("WAV ->")).allMatch(c -> c.contains("rejected")),
                    String.valueOf(first.negativeChecks()));
            assertTrue(first.negativeChecks().contains("sessions still open after use: 0"));
        } finally {
            executor.shutdownNow();
        }
    }
}

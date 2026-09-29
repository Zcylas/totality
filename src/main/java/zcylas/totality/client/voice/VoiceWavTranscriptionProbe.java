package zcylas.totality.client.voice;

import zcylas.totality.api.voice.audio.PcmAudio;
import zcylas.totality.api.voice.audio.UnsupportedAudioException;
import zcylas.totality.api.voice.audio.WavPcmReader;
import zcylas.totality.api.voice.recognition.BackendStatus;
import zcylas.totality.api.voice.recognition.NativeCrashGuard;
import zcylas.totality.api.voice.recognition.RecognitionRequest;
import zcylas.totality.api.voice.recognition.RecognitionSession;
import zcylas.totality.api.voice.recognition.Transcript;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Development verification: decodes a real WAV recording through the bundled model and the real
 * Vosk backend, and measures it. Shared by the opt-in in-game hook ({@link VoiceWavVerification})
 * and the environment-gated JUnit integration test, so both exercise the same code path.
 *
 * <p>Blocking; run on a worker thread. It never fabricates a result: the transcript reported is
 * exactly what the engine returned, and "success" requires non-empty decoded text.
 */
public final class VoiceWavTranscriptionProbe {

    /** Samples fed per call, i.e. 100 ms at 16 kHz — the chunk size live capture will use. */
    private static final int CHUNK = RecognitionSession.SAMPLE_RATE / 10;
    private static final Set<String> NEGATIVE_GRAMMAR = Set.of("confirm", "cancel");

    private VoiceWavTranscriptionProbe() {}

    public record Result(boolean success, BackendStatus status, String transcript, boolean transcriptUnrecognized,
                         String grammarTranscript, boolean grammarUnrecognized, double audioSeconds,
                         long installMillis, boolean installReused, long loadMillis, long feedMillis,
                         long finishMillis, long rssBeforeKb, long rssAfterLoadKb, long rssAfterDecodeKb,
                         long ticksDuringLoad, List<String> negativeChecks, String error) {

        public String toReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("success=").append(success).append('\n');
            sb.append("backend.status=").append(status).append('\n');
            sb.append("transcript.freeform=\"").append(transcript).append("\" unrecognized=").append(transcriptUnrecognized).append('\n');
            sb.append("transcript.grammar[confirm,cancel]=\"").append(grammarTranscript).append("\" unrecognized=").append(grammarUnrecognized).append('\n');
            sb.append(String.format("audio.seconds=%.3f%n", audioSeconds));
            sb.append("model.install.millis=").append(installMillis).append(" reused=").append(installReused).append('\n');
            sb.append("backend.load.millis=").append(loadMillis).append(" (includes model install)\n");
            sb.append("decode.feed.millis=").append(feedMillis).append('\n');
            sb.append("decode.finish.millis=").append(finishMillis).append('\n');
            if (audioSeconds > 0) {
                sb.append(String.format("decode.realtime.factor=%.3f%n", (feedMillis + finishMillis) / 1000.0 / audioSeconds));
            }
            sb.append("rss.kb before=").append(rssBeforeKb).append(" afterLoad=").append(rssAfterLoadKb)
                    .append(" afterDecode=").append(rssAfterDecodeKb).append('\n');
            sb.append("client.ticks.during.load=").append(ticksDuringLoad).append('\n');
            negativeChecks.forEach(c -> sb.append("negative: ").append(c).append('\n'));
            if (error != null) sb.append("error=").append(error).append('\n');
            return sb.toString();
        }
    }

    /**
     * @param voiceRoot  where the model is extracted (reused if already verified)
     * @param wav        spoken-English recording, 16 kHz 16-bit PCM
     * @param tickCounter current client tick count (to show loading did not stall the client), or () -> 0
     */
    public static Result run(Path voiceRoot, Path wav, Executor loadExecutor, LongSupplier tickCounter) {
        List<String> negatives = new ArrayList<>();
        long[] install = {-1, 0};
        long rssBefore = rssKb();

        PcmAudio audio;
        try {
            audio = WavPcmReader.read(wav);
        } catch (IOException e) {
            return failure(null, "Could not read WAV " + wav + ": " + e.getMessage(), negatives, rssBefore);
        }

        VoskRecognitionBackend backend = VoskRecognitionBackend.forBundledModel(voiceRoot, loadExecutor,
                (installed, millis) -> {
                    install[0] = millis;
                    install[1] = installed.reused() ? 1 : 0;
                });
        try {
            long ticksBefore = tickCounter.getAsLong();
            long t0 = System.nanoTime();
            BackendStatus status = backend.load().get(5, TimeUnit.MINUTES);
            long loadMillis = (System.nanoTime() - t0) / 1_000_000L;
            long ticksDuringLoad = tickCounter.getAsLong() - ticksBefore;
            long rssAfterLoad = rssKb();
            if (!status.isReady()) {
                return failure(status, "Backend not ready", negatives, rssBefore);
            }

            String text;
            boolean unrecognized;
            long feedMillis, finishMillis;
            try (RecognitionSession session = backend.openSession(RecognitionRequest.freeform())) {
                long f0 = System.nanoTime();
                feed(session, audio.samples());
                long f1 = System.nanoTime();
                Transcript t = session.finish();
                long f2 = System.nanoTime();
                feedMillis = (f1 - f0) / 1_000_000L;
                finishMillis = (f2 - f1) / 1_000_000L;
                text = t.text();
                unrecognized = t.unrecognized();
            }
            long rssAfterDecode = rssKb();

            String grammarText;
            boolean grammarUnrecognized;
            try (RecognitionSession session = backend.openSession(RecognitionRequest.grammar(NEGATIVE_GRAMMAR))) {
                feed(session, audio.samples());
                Transcript t = session.finish();
                grammarText = t.text();
                grammarUnrecognized = t.unrecognized();
            }

            runNegativeChecks(voiceRoot, loadExecutor, negatives);
            negatives.add("sessions still open after use: " + backend.openSessionCount());

            boolean success = !text.isBlank() && !unrecognized;
            return new Result(success, status, text, unrecognized, grammarText, grammarUnrecognized,
                    audio.durationSeconds(), install[0], install[1] == 1, loadMillis, feedMillis, finishMillis,
                    rssBefore, rssAfterLoad, rssAfterDecode, ticksDuringLoad, negatives, null);
        } catch (Exception e) {
            return failure(backend.status(), e.toString(), negatives, rssBefore);
        } finally {
            backend.close();
        }
    }

    private static void feed(RecognitionSession session, short[] samples) {
        for (int off = 0; off < samples.length; off += CHUNK) {
            short[] chunk = Arrays.copyOfRange(samples, off, Math.min(samples.length, off + CHUNK));
            session.acceptAudio(chunk, chunk.length);
        }
    }

    /** Failure paths that must degrade gracefully (never crash the game). */
    private static void runNegativeChecks(Path voiceRoot, Executor executor, List<String> out) {
        Path scratch = voiceRoot.resolve("verification").resolve("negative");
        Path missing = scratch.resolve("no-such-model");
        VoskRecognitionBackend missingModel = new VoskRecognitionBackend(() -> missing,
                new NativeCrashGuard(scratch.resolve("negative-native-load.marker")), () -> {}, executor);
        try {
            BackendStatus s = missingModel.load().get(1, TimeUnit.MINUTES);
            out.add("missing model path -> " + s);
        } catch (Exception e) {
            out.add("missing model path -> threw " + e);
        } finally {
            missingModel.close();
        }

        byte[] malformed = "RIFF\0\0\0\0WAVEjunk".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.add("malformed WAV -> " + rejectionOf(malformed));
        out.add("8 kHz WAV -> " + rejectionOf(pcmWav(8_000, 1, 16)));
        out.add("8-bit WAV -> " + rejectionOf(pcmWav(16_000, 1, 8)));
        try {
            Files.deleteIfExists(scratch.resolve("negative-native-load.marker"));
        } catch (IOException ignored) {
        }
    }

    private static String rejectionOf(byte[] wav) {
        try {
            WavPcmReader.read(wav);
            return "ACCEPTED (unexpected)";
        } catch (UnsupportedAudioException e) {
            return "rejected: " + e.getMessage();
        }
    }

    /** A 0.1 s silent PCM WAV with the given format, for rejection checks. */
    static byte[] pcmWav(int rate, int channels, int bits) {
        int blockAlign = channels * bits / 8;
        int dataSize = rate / 10 * blockAlign;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44 + dataSize).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes()).putInt(36 + dataSize).put("WAVE".getBytes());
        b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) channels).putInt(rate)
                .putInt(rate * blockAlign).putShort((short) blockAlign).putShort((short) bits);
        b.put("data".getBytes()).putInt(dataSize);
        return b.array();
    }

    private static Result failure(BackendStatus status, String error, List<String> negatives, long rssBefore) {
        return new Result(false, status, "", true, "", true, 0, -1, false, -1, -1, -1,
                rssBefore, -1, -1, -1, negatives, error);
    }

    /** Resident set size of this process in KiB (Linux /proc), or -1 elsewhere. */
    static long rssKb() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                if (line.startsWith("VmRSS:")) {
                    return Long.parseLong(line.replaceAll("[^0-9]", ""));
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }
}

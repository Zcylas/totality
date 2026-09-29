package zcylas.totality.client.voice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import zcylas.totality.api.voice.audio.PcmAudio;
import zcylas.totality.api.voice.audio.WavPcmReader;
import zcylas.totality.api.voice.command.OperatorVoice;
import zcylas.totality.api.voice.command.VoiceCommandMatcher;
import zcylas.totality.api.voice.recognition.BackendStatus;
import zcylas.totality.api.voice.recognition.RecognitionRequest;
import zcylas.totality.api.voice.recognition.RecognitionSession;
import zcylas.totality.api.voice.recognition.Transcript;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in offline probe of contextual voice commands on the REAL bundled model: every WAV (16 kHz mono
 * 16-bit) in {@code TOTALITY_VOICE_COMMAND_WAVS} (a file or a directory) is decoded exactly as a
 * command utterance is in game — a GRAMMAR session restricted to the showcase's spoken phrases plus a
 * FREEFORM session with ranked alternatives on the same audio, then {@link VoiceCommandMatcher} — and,
 * for comparison, as ordinary dictation. Nothing is asserted about acceptance: this is a measuring
 * instrument. Report: {@code build/voice-test/command-probe-report.txt}.
 */
@EnabledIfEnvironmentVariable(named = "TOTALITY_VOICE_COMMAND_WAVS", matches = ".+")
class VoiceCommandWavProbeTest {

    /** Mirrors the showcase's intents (HologramVoice.SPOKEN): Confirm, Cancel and Dismiss phrases. */
    private static final Set<String> PHRASES = Set.of("confirmed", "confirm", "yes", "okay", "ok", "sure", "accept",
            "cancel", "no", "decline", "reject", "dismiss", "close", "hide", "minimize", "minimise");

    private static String commandOf(String phrase) {
        return switch (phrase) {
            case "cancel", "no", "decline", "reject" -> "cancel";
            case "dismiss", "close", "hide", "minimize", "minimise" -> "dismiss";
            default -> "confirm";
        };
    }

    @Test
    void probeCommandRecognition(@TempDir Path voiceRoot) throws Exception {
        Path input = Path.of(System.getenv("TOTALITY_VOICE_COMMAND_WAVS"));
        List<Path> wavs;
        if (Files.isDirectory(input)) {
            try (Stream<Path> s = Files.list(input)) {
                wavs = s.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".wav")).sorted().toList();
            }
        } else {
            wavs = List.of(input);
        }
        ExecutorService executor = Executors.newSingleThreadExecutor();
        VoskRecognitionBackend backend = VoskRecognitionBackend.forBundledModel(voiceRoot, executor, (i, ms) -> {});
        List<String> report = new ArrayList<>();
        int accepted = 0, strict = 0;
        java.util.Set<String> operatorPhrases = new java.util.LinkedHashSet<>(PHRASES);
        List<String> operatorReport = new ArrayList<>();
        int operatorRequests = 0;
        try {
            BackendStatus status = backend.load().get(5, TimeUnit.MINUTES);
            assertTrue(status.isReady(), "backend: " + status);
            operatorPhrases.addAll(OperatorVoice.GRAMMAR_PHRASES);
            for (Path wav : wavs) {
                PcmAudio audio = WavPcmReader.read(wav);
                // As an OPERATOR with a Confirm/Cancel/Dismiss hologram open: the real grammar, the real
                // precedence (Operator Mode vocabulary -> Operator Mode alone) and the interim activation.
                double[] activatedAt = {-1};
                Transcript og = decodeWatching(backend, RecognitionRequest.grammar(operatorPhrases),
                        RecognitionRequest.freeformWithAlternatives(VoiceCommandMatcher.CORROBORATION_HYPOTHESES), audio, activatedAt);
                Transcript of = lastFreeform;
                String outcome;
                OperatorVoice.Decision od = OperatorVoice.decide(og, of);
                if (od.involved()) {
                    outcome = od.accepted() ? "OPERATOR REQUEST " + od.action() : "operator rejected " + od.rejection();
                    if (od.accepted()) operatorRequests++;
                } else {
                    VoiceCommandMatcher.Decision hd = VoiceCommandMatcher.decide(operatorPhrases.stream()
                            .filter(PHRASES::contains).collect(java.util.stream.Collectors.toSet()), VoiceCommandWavProbeTest::commandOf, og, of);
                    outcome = hd.accepted() ? "hologram command '" + hd.command() + "'" : "hologram rejected " + hd.rejection();
                }
                operatorReport.add(String.format(Locale.ROOT, "%-32s grammar \"%s\" | free-form best \"%s\" | interim activation %s | %s",
                        wav.getFileName(), og.text(), of.hypotheses().isEmpty() ? "" : of.hypotheses().getFirst(),
                        activatedAt[0] < 0 ? "no" : String.format(Locale.ROOT, "at %.1f s", activatedAt[0]), outcome));
                Transcript grammar = decode(backend, RecognitionRequest.grammar(PHRASES), audio);
                Transcript freeform = decode(backend,
                        RecognitionRequest.freeformWithAlternatives(VoiceCommandMatcher.CORROBORATION_HYPOTHESES), audio);
                Transcript dictation = decode(backend, RecognitionRequest.freeform(), audio);
                VoiceCommandMatcher.Decision d = VoiceCommandMatcher.decide(PHRASES, VoiceCommandWavProbeTest::commandOf, grammar, freeform);
                if (d.accepted()) accepted++;
                if (d.acceptedByBestHypothesisAlone()) strict++;
                report.add("=== " + wav.getFileName());
                report.add("dictation would show: \"" + dictation.text() + "\"");
                for (String line : VoiceCommandDiagnostics.format(new VoiceInputController.CommandAttempt(0, true, null,
                        PHRASES, VoiceInputController.Outcome.SUCCESS, audio.samples().length / 16_000.0, 0, grammar,
                        freeform, d, null, d.accepted() ? "WOULD EXECUTE '" + d.command() + "'" : "rejected " + d.rejection()))) {
                    if (!line.startsWith("===") && !line.startsWith("capture:") && !line.startsWith("hologram current")) report.add(line);
                }
                report.add("");
            }
        } finally {
            backend.close();
            executor.shutdownNow();
        }
        report.add(0, String.format(Locale.ROOT, "%d file(s): %d would execute with the current rule, %d with the strict best-hypothesis rule",
                wavs.size(), accepted, strict));
        Path outDir = Path.of("build", "voice-test");
        Files.createDirectories(outDir);
        operatorReport.add(0, String.format(Locale.ROOT, "Operator Mode probe (operator, Confirm/Cancel/Dismiss hologram open): %d file(s), %d Operator Mode request(s)",
                wavs.size(), operatorRequests));
        Files.write(outDir.resolve("operator-probe-report.txt"), operatorReport);
        Path out = Path.of("build", "voice-test");
        Files.createDirectories(out);
        Files.write(out.resolve("command-probe-report.txt"), report);
    }

    private static Transcript lastFreeform;

    /** Grammar + free-form sessions fed together chunk by chunk, as in game; notes the interim activation. */
    private static Transcript decodeWatching(VoskRecognitionBackend backend, RecognitionRequest grammarRequest,
                                             RecognitionRequest freeformRequest, PcmAudio audio, double[] activatedAt) {
        try (RecognitionSession g = backend.openSession(grammarRequest); RecognitionSession f = backend.openSession(freeformRequest)) {
            short[] samples = audio.samples();
            short[] chunk = new short[1_600];
            for (int i = 0; i < samples.length; i += chunk.length) {
                int n = Math.min(chunk.length, samples.length - i);
                System.arraycopy(samples, i, chunk, 0, n);
                g.acceptAudio(chunk, n);
                f.acceptAudio(chunk, n);
                if (activatedAt[0] < 0 && OperatorVoice.activationHeard(f.partialText(), g.partialText())) {
                    activatedAt[0] = (i + n) / 16_000.0;
                }
            }
            lastFreeform = f.finish();
            return g.finish();
        }
    }

    private static Transcript decode(VoskRecognitionBackend backend, RecognitionRequest request, PcmAudio audio) {
        try (RecognitionSession session = backend.openSession(request)) {
            short[] samples = audio.samples();
            short[] chunk = new short[1_600];
            for (int i = 0; i < samples.length; i += chunk.length) {
                int n = Math.min(chunk.length, samples.length - i);
                System.arraycopy(samples, i, chunk, 0, n);
                session.acceptAudio(chunk, n);
            }
            return session.finish();
        }
    }
}

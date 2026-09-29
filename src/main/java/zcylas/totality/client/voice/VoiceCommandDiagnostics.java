package zcylas.totality.client.voice;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.voice.recognition.Transcript;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Development-only diagnostics of contextual voice attempts (push-to-talk while a hologram offers voice
 * commands). Off by default; enabled with {@code -Dtotality.voice.commandDiagnostics=true} or
 * {@code /totalityvoice commanddiag on}, and only in a Fabric development environment.
 *
 * <p>For every push-to-talk it records whether a command context was offered (and if not, why), the
 * offered grammar phrases, the GRAMMAR result, the FREEFORM result with its ranked alternatives, the
 * rule-by-rule decision, and whether the hologram was still current at execution. Written to
 * {@code <gameDir>/totality/voice/diagnostics/command-attempts.log} and the development log only —
 * never chat, never the server. It contains recognized TEXT only; no audio is ever saved by it.
 */
final class VoiceCommandDiagnostics {

    static final String PROPERTY = "totality.voice.commandDiagnostics";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private VoiceCommandDiagnostics() {}

    static boolean available() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    static Path logFile() {
        return VoiceRuntime.voiceRoot().resolve("diagnostics").resolve("command-attempts.log");
    }

    static void registerIfRequested(VoiceInputController controller) {
        if (available() && Boolean.getBoolean(PROPERTY)) setEnabled(controller, true);
    }

    static boolean setEnabled(VoiceInputController controller, boolean enabled) {
        if (!available()) return false;
        controller.setCommandDiagnostics(enabled ? VoiceCommandDiagnostics::write : null);
        Totality.LOGGER.info("[Totality Voice] command diagnostics {} ({})", enabled ? "ON" : "OFF", logFile());
        return true;
    }

    static void write(VoiceInputController.CommandAttempt a) {
        List<String> lines = format(a);
        for (String line : lines) Totality.LOGGER.info("[Totality Voice][command-diag] {}", line);
        try {
            Files.createDirectories(logFile().getParent());
            lines.add("");
            Files.write(logFile(), lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            Totality.LOGGER.warn("[Totality Voice] could not write command diagnostics", e);
        }
    }

    static List<String> format(VoiceInputController.CommandAttempt a) {
        List<String> out = new ArrayList<>();
        out.add("=== " + LocalDateTime.now().format(TIME) + " · utterance #" + a.utterance());
        if (!a.offered()) {
            out.add("context offered: NO — " + a.notOfferedBecause());
            out.add("RESULT: " + a.result());
            return out;
        }
        out.add("context offered: yes · grammar phrases " + a.phrases());
        out.add(String.format(Locale.ROOT, "capture: %.2f s, peak %.1f dBFS, outcome %s", a.audioSeconds(), a.peakDbfs(), a.outcome()));
        out.add("GRAMMAR:  " + describe(a.grammar()));
        out.add("FREEFORM: " + describe(a.freeform()));
        if (a.decision() != null) {
            out.add("decision trace:");
            for (String line : a.decision().trace()) out.add("  " + line);
            out.add("strict rule (free-form best hypothesis only) would accept: "
                    + (a.decision().acceptedByBestHypothesisAlone() ? "yes" : "no"));
        }
        out.add("hologram current at execution: " + (a.currentAtExecution() == null ? "n/a (not executed)"
                : a.currentAtExecution() ? "yes" : "NO (stale)"));
        out.add("RESULT: " + a.result());
        return out;
    }

    private static String describe(@Nullable Transcript t) {
        if (t == null) return "(no result)";
        StringBuilder b = new StringBuilder("\"").append(t.text()).append('"');
        if (t.unrecognized()) b.append(" [unrecognized/out-of-grammar]");
        if (!t.words().isEmpty()) {
            b.append(" words [");
            for (int i = 0; i < t.words().size(); i++) {
                Transcript.Word w = t.words().get(i);
                b.append(i == 0 ? "" : ", ").append(w.text());
                if (!Double.isNaN(w.confidence())) b.append(String.format(Locale.ROOT, " %.2f", w.confidence()));
            }
            b.append(']');
        }
        if (!t.alternatives().isEmpty()) {
            b.append(" alternatives [");
            for (int i = 0; i < t.alternatives().size(); i++) {
                b.append(i == 0 ? "" : ", ").append('#').append(i).append(" \"").append(t.alternatives().get(i)).append('"');
            }
            b.append(']');
        }
        return b.toString();
    }
}

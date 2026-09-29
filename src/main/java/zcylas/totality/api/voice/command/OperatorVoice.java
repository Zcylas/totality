package zcylas.totality.api.voice.command;

import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.operator.OperatorAction;
import zcylas.totality.api.voice.recognition.RecognitionRequest;
import zcylas.totality.api.voice.recognition.Transcript;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Operator Mode's recognition rules (pure logic). Operator Mode is a deliberate, complete phrase:
 * "operator mode" followed by exactly one whitelisted command ({@link OperatorAction}).
 *
 * <p><b>Final decision</b> ({@link #decide}), stricter than hologram commands:
 * <ol>
 *   <li>the GRAMMAR result must be exactly "operator mode &lt;command&gt;" — no other words, no
 *       out-of-grammar segment, confidence at least {@link VoiceCommandMatcher#MIN_GRAMMAR_CONFIDENCE};</li>
 *   <li>the FREEFORM BEST hypothesis (rank 0 only — lower ranks never authorize) must be exactly the same
 *       words.</li>
 * </ol>
 * Anything else — no prefix, prefix alone, two commands, extra words, a sound-alike — performs nothing.
 * The result only NAMES an action; the server decides whether it happens.
 *
 * <p><b>Precedence</b> ({@link #involvesOperator}): an utterance that mentions Operator Mode's vocabulary
 * in either recognition belongs to Operator Mode ALONE — it can never also confirm, cancel or dismiss a
 * hologram — and an ordinary hologram phrase can never reach Operator Mode without the spoken prefix.
 *
 * <p><b>Interim activation</b> ({@link #activationHeard}): mid-hold, when BOTH partial results begin with
 * the activation phrase, the HUD may switch to Operator Mode and ask the server for authorization.
 * Presentation only; partial results never execute anything.
 */
public final class OperatorVoice {

    public static final String ACTIVATION = "operator mode";

    /**
     * Extra GRAMMAR phrases offered to operators: the complete phrases, the activation phrase alone (so an
     * unfinished utterance is not forced into a command) and the bare commands (so a command WITHOUT the
     * prefix is recognized as such — and then refused — instead of being pulled into a full phrase).
     */
    public static final Set<String> GRAMMAR_PHRASES;

    static {
        Set<String> p = new LinkedHashSet<>();
        p.add(ACTIVATION);
        for (OperatorAction a : OperatorAction.values()) {
            p.add(ACTIVATION + " " + a.spoken);
            p.add(a.spoken);
        }
        GRAMMAR_PHRASES = Set.copyOf(p);
    }

    public enum Rejection {
        /** The grammar result was not exactly one complete Operator Mode phrase. */
        NOT_RECOGNIZED,
        /** The activation phrase without a command. */
        INCOMPLETE,
        /** A command without the activation phrase. */
        NO_PREFIX,
        /** Grammar confidence below the floor. */
        LOW_CONFIDENCE,
        /** The free-form best hypothesis did not say exactly the same words. */
        NOT_CORROBORATED
    }

    /**
     * @param action    the whitelisted action named, or null when rejected
     * @param involved  whether the utterance belonged to Operator Mode at all (see {@link #involvesOperator})
     */
    public record Decision(@Nullable OperatorAction action, boolean involved, @Nullable Rejection rejection, List<String> trace) {
        public boolean accepted() {
            return action != null;
        }
    }

    private OperatorVoice() {}

    /** Both partial results (free-form and grammar) begin with the activation phrase. */
    public static boolean activationHeard(@Nullable String freeformPartial, @Nullable String grammarPartial) {
        return startsWithActivation(freeformPartial) && startsWithActivation(grammarPartial);
    }

    static boolean startsWithActivation(@Nullable String text) {
        String n = RecognitionRequest.normalize(text);
        return n.equals(ACTIVATION) || n.startsWith(ACTIVATION + " ");
    }

    /** True when either result uses Operator Mode vocabulary ("operator", or one of the commands). */
    public static boolean involvesOperator(@Nullable Transcript grammar, @Nullable Transcript freeform) {
        String g = grammar == null ? "" : RecognitionRequest.normalize(grammar.text());
        String f = freeform == null || freeform.hypotheses().isEmpty() ? "" : RecognitionRequest.normalize(freeform.hypotheses().getFirst());
        return mentions(g) || mentions(f);
    }

    private static boolean mentions(String text) {
        String[] words = text.split(" ");
        for (String w : words) if (w.equals("operator")) return true;
        for (OperatorAction a : OperatorAction.values()) {
            if (VoiceCommandMatcher.contains(words, a.spoken.split(" "))) return true;
        }
        return false;
    }

    public static Decision decide(@Nullable Transcript grammar, @Nullable Transcript freeform) {
        List<String> trace = new ArrayList<>();
        boolean involved = involvesOperator(grammar, freeform);
        if (!involved) return new Decision(null, false, null, List.of("operator: vocabulary not used"));
        String g = grammar == null ? "" : RecognitionRequest.normalize(grammar.text());
        trace.add("operator grammar: \"" + g + "\"" + (grammar != null && grammar.unrecognized() ? " (out-of-grammar segment)" : ""));
        if (grammar == null || grammar.unrecognized() || !GRAMMAR_PHRASES.contains(g)) {
            return reject(Rejection.NOT_RECOGNIZED, trace, "operator rule 1 FAIL: not exactly one complete phrase");
        }
        if (g.equals(ACTIVATION)) return reject(Rejection.INCOMPLETE, trace, "operator rule 1 FAIL: activation phrase without a command");
        if (!g.startsWith(ACTIVATION + " ")) return reject(Rejection.NO_PREFIX, trace, "operator rule 1 FAIL: command without \"operator mode\"");
        OperatorAction action = OperatorAction.bySpoken(g.substring(ACTIVATION.length() + 1));
        if (action == null) return reject(Rejection.NOT_RECOGNIZED, trace, "operator rule 1 FAIL: unknown command");
        OptionalDouble confidence = grammar.averageConfidence();
        if (confidence.isPresent() && confidence.getAsDouble() < VoiceCommandMatcher.MIN_GRAMMAR_CONFIDENCE) {
            return reject(Rejection.LOW_CONFIDENCE, trace, String.format(Locale.ROOT, "operator rule 2 FAIL: grammar confidence %.2f",
                    confidence.getAsDouble()));
        }
        List<String> hypotheses = freeform == null ? List.of() : freeform.hypotheses();
        String best = hypotheses.isEmpty() ? "" : RecognitionRequest.normalize(hypotheses.getFirst());
        trace.add("operator free-form best: \"" + best + "\"");
        if (!best.equals(g)) {
            String lower = "";
            for (int i = 1; i < hypotheses.size(); i++) {
                if (RecognitionRequest.normalize(hypotheses.get(i)).equals(g)) {
                    lower = " (lower-ranked #" + i + " matches — never accepted)";
                    break;
                }
            }
            return reject(Rejection.NOT_CORROBORATED, trace, "operator rule 3 FAIL: the free-form best hypothesis is not exactly \"" + g + "\"" + lower);
        }
        trace.add("operator rules pass: " + action + " (the server decides whether it is allowed)");
        return new Decision(action, true, null, List.copyOf(trace));
    }

    private static Decision reject(Rejection why, List<String> trace, String line) {
        trace.add(line);
        return new Decision(null, true, why, List.copyOf(trace));
    }
}

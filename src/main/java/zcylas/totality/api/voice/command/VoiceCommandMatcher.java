package zcylas.totality.api.voice.command;

import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.voice.recognition.RecognitionRequest;
import zcylas.totality.api.voice.recognition.Transcript;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Decides whether one push-to-talk utterance is a command. Two recognitions of the SAME audio must
 * agree on the same INTENT (several spoken phrases may name one intent — "confirmed", "yes", "okay"
 * all mean Confirm — and never count as different commands):
 * <ol>
 *   <li>a GRAMMAR session restricted to the offered phrases (plus an "unknown" catch-all) must return
 *       exactly one of them, with no out-of-grammar segment;</li>
 *   <li>the unrestricted FREEFORM session's BEST hypothesis must contain a phrase of that same intent,
 *       word for word, and no phrase of another intent.</li>
 * </ol>
 * Lower-ranked free-form alternatives never authorize an action: in real-microphone testing
 * (2026-09-26) they turned "conform" and "castle" into Confirm/Cancel. They are still reported in the
 * trace so development diagnostics can show what they would have done. Vosk's confidence is only an
 * extra floor, never sufficient on its own. Anything else is rejected. Pure logic.
 */
public final class VoiceCommandMatcher {

    /** Grammar word confidence below this rejects (an extra gate; it never accepts by itself). */
    public static final double MIN_GRAMMAR_CONFIDENCE = 0.5;
    /** A command is a short utterance; longer speech is dictation, not a command. */
    public static final int MAX_FREEFORM_WORDS = 3;
    /**
     * Ranked FREEFORM hypotheses the command sessions request — for diagnostics only; only the best one
     * (rank 0) can corroborate.
     */
    public static final int CORROBORATION_HYPOTHESES = 5;

    public enum Rejection {
        /** The grammar pass did not return exactly one offered phrase. */
        NOT_RECOGNIZED,
        /** The free-form best hypothesis of the same audio did not contain the command. */
        NOT_CORROBORATED,
        /** More than one command was heard. */
        AMBIGUOUS,
        /** The grammar pass matched with low confidence. */
        LOW_CONFIDENCE,
        /** Too many words for a command. */
        TOO_LONG,
        /** Nothing usable was captured (too short, silent, device problem, recognition error). */
        NO_SPEECH,
        /** The target closed, changed or was interrupted while the utterance was recognized. */
        STALE,
        /** The utterance was cancelled (screen opened, focus lost, disconnect). */
        CANCELLED
    }

    /**
     * @param command            the canonical command to execute, or null when rejected
     * @param corroborationRank  0 when accepted (the free-form best hypothesis corroborated), -1 otherwise
     * @param trace              human-readable rule evaluations (development diagnostics)
     */
    public record Decision(@Nullable String command, @Nullable Rejection rejection, int corroborationRank,
                           List<String> trace) {
        public boolean accepted() {
            return command != null;
        }

        /** Always equal to {@link #accepted()} now that only the best hypothesis corroborates. */
        public boolean acceptedByBestHypothesisAlone() {
            return accepted() && corroborationRank == 0;
        }
    }

    private VoiceCommandMatcher() {}

    /** Each phrase is its own command. */
    public static Decision decide(Set<String> phrases, @Nullable Transcript grammar, @Nullable Transcript freeform) {
        return decide(phrases, UnaryOperator.identity(), grammar, freeform);
    }

    /**
     * @param phrases   the spoken phrases the grammar was restricted to
     * @param commandOf maps a spoken phrase to its canonical command ("confirmed" → "confirm")
     */
    public static Decision decide(Set<String> phrases, UnaryOperator<String> commandOf,
                                  @Nullable Transcript grammar, @Nullable Transcript freeform) {
        List<String> trace = new ArrayList<>();
        if (grammar == null) return reject(Rejection.NOT_RECOGNIZED, trace, "grammar: no result");
        String spoken = RecognitionRequest.normalize(grammar.text());
        trace.add("grammar: \"" + spoken + "\"" + (grammar.unrecognized() ? " (contains/was an out-of-grammar segment)" : ""));
        if (grammar.unrecognized() || !phrases.contains(spoken)) {
            return reject(Rejection.NOT_RECOGNIZED, trace, "rule 1 FAIL: grammar did not return exactly one offered phrase");
        }
        String command = commandOf.apply(spoken);
        trace.add("rule 1 pass: grammar heard '" + spoken + "' -> command '" + command + "'");
        OptionalDouble confidence = grammar.averageConfidence();
        if (confidence.isPresent() && confidence.getAsDouble() < MIN_GRAMMAR_CONFIDENCE) {
            return reject(Rejection.LOW_CONFIDENCE, trace, String.format(Locale.ROOT,
                    "rule 2 FAIL: grammar confidence %.2f < %.2f", confidence.getAsDouble(), MIN_GRAMMAR_CONFIDENCE));
        }
        trace.add(confidence.isPresent()
                ? String.format(Locale.ROOT, "rule 2 pass: grammar confidence %.2f", confidence.getAsDouble())
                : "rule 2 n/a: no grammar confidence reported");

        List<String> hypotheses = freeform == null ? List.of() : freeform.hypotheses();
        trace.add("freeform hypotheses: " + (hypotheses.isEmpty() ? "(none)" : quoteAll(hypotheses)));
        if (hypotheses.isEmpty()) return reject(Rejection.NOT_CORROBORATED, trace, "rule 3 FAIL: free-form heard nothing");
        String[] best = RecognitionRequest.normalize(hypotheses.getFirst()).split(" ");
        if (best.length > MAX_FREEFORM_WORDS) {
            return reject(Rejection.TOO_LONG, trace, "rule 3 FAIL: free-form best hypothesis has " + best.length
                    + " words (max " + MAX_FREEFORM_WORDS + ")");
        }
        Set<String> heard = commandsIn(hypotheses.getFirst(), phrases, commandOf);
        if (heard.size() > 1) {
            return reject(Rejection.AMBIGUOUS, trace, "rule 4 FAIL: the best hypothesis names several commands " + heard);
        }
        if (!heard.contains(command)) {
            String lower = "";
            for (int rank = 1; rank < Math.min(hypotheses.size(), CORROBORATION_HYPOTHESES); rank++) {
                if (commandsIn(hypotheses.get(rank), phrases, commandOf).contains(command)) {
                    lower = " (lower-ranked #" + rank + " \"" + hypotheses.get(rank)
                            + "\" names it — deliberately NOT accepted: such matches caused false activations)";
                    break;
                }
            }
            return reject(Rejection.NOT_CORROBORATED, trace, "rule 3 FAIL: the free-form best hypothesis \""
                    + hypotheses.getFirst() + "\" " + (heard.isEmpty() ? "names no command" : "names a different command " + heard) + lower);
        }
        trace.add("rule 3 pass: the free-form best hypothesis \"" + hypotheses.getFirst() + "\" names '" + command + "'");
        trace.add("rule 4 pass: no other command in it");
        return new Decision(command, null, 0, List.copyOf(trace));
    }

    /** The commands whose phrases occur, word for word, in {@code hypothesis}. */
    static Set<String> commandsIn(String hypothesis, Set<String> phrases, UnaryOperator<String> commandOf) {
        String[] words = RecognitionRequest.normalize(hypothesis).split(" ");
        Set<String> heard = new LinkedHashSet<>();
        for (String phrase : phrases) if (contains(words, phrase.split(" "))) heard.add(commandOf.apply(phrase));
        return heard;
    }

    private static Decision reject(Rejection why, List<String> trace, String line) {
        trace.add(line);
        return new Decision(null, why, -1, List.copyOf(trace));
    }

    private static String quoteAll(List<String> items) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < items.size(); i++) b.append(i == 0 ? "" : ", ").append('#').append(i).append(" \"").append(items.get(i)).append('"');
        return b.toString();
    }

    /** Whether {@code words} contains {@code phrase} as consecutive, identical words. */
    static boolean contains(String[] words, String[] phrase) {
        for (int start = 0; start + phrase.length <= words.length; start++) {
            boolean match = true;
            for (int i = 0; i < phrase.length && match; i++) match = words[start + i].equals(phrase[i]);
            if (match) return true;
        }
        return false;
    }
}

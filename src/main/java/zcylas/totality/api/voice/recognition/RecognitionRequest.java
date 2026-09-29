package zcylas.totality.api.voice.recognition;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable description of one recognition session: its mode and, for GRAMMAR, the phrases it may
 * return. Phrases are normalized (trimmed, lower-case, single spaces) so backends never see two
 * spellings of the same phrase. A FREEFORM request may ask for up to {@code alternatives} ranked
 * hypotheses (0 = best hypothesis only, the default and what dictation uses).
 */
public record RecognitionRequest(RecognitionMode mode, Set<String> phrases, int alternatives) {

    public RecognitionRequest(RecognitionMode mode, Set<String> phrases) {
        this(mode, phrases, 0);
    }

    public RecognitionRequest {
        if (alternatives < 0) throw new IllegalArgumentException("alternatives < 0");
        if (alternatives > 0 && mode != RecognitionMode.FREEFORM) {
            throw new IllegalArgumentException("Only a FREEFORM request can ask for alternatives");
        }
        if (mode == null) throw new IllegalArgumentException("mode");
        TreeSet<String> normalized = new TreeSet<>();
        if (phrases != null) {
            for (String phrase : phrases) {
                String n = normalize(phrase);
                if (!n.isEmpty()) normalized.add(n);
            }
        }
        if (mode == RecognitionMode.GRAMMAR && normalized.isEmpty()) {
            throw new IllegalArgumentException("A GRAMMAR request needs at least one non-blank phrase");
        }
        if (mode == RecognitionMode.FREEFORM && !normalized.isEmpty()) {
            throw new IllegalArgumentException("A FREEFORM request takes no phrases");
        }
        phrases = Set.copyOf(normalized);
    }

    public static RecognitionRequest freeform() {
        return new RecognitionRequest(RecognitionMode.FREEFORM, Set.of());
    }

    /** Free-form transcription that also reports up to {@code count} ranked hypotheses. */
    public static RecognitionRequest freeformWithAlternatives(int count) {
        return new RecognitionRequest(RecognitionMode.FREEFORM, Set.of(), count);
    }

    public static RecognitionRequest grammar(Set<String> phrases) {
        return new RecognitionRequest(RecognitionMode.GRAMMAR, phrases);
    }

    public static String normalize(String phrase) {
        if (phrase == null) return "";
        return phrase.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}

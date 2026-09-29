package zcylas.totality.api.voice.recognition;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Final result of one {@link RecognitionSession}, independent of the engine that produced it.
 *
 * @param text         recognized text, normalized to single spaces; empty when nothing was heard.
 *                     Never contains the backend's own "unknown" token.
 * @param mode         the session's mode
 * @param words        per-word detail, when the backend provides it (may be empty)
 * @param unrecognized true when the utterance was empty or (GRAMMAR) did not match a phrase —
 *                     consumers must treat this as "nothing understood", never as a command
 * @param alternatives ranked hypotheses when the request asked for them (index 0 is {@code text});
 *                     empty otherwise
 */
public record Transcript(String text, RecognitionMode mode, List<Word> words, boolean unrecognized,
                         List<String> alternatives) {

    public Transcript(String text, RecognitionMode mode, List<Word> words, boolean unrecognized) {
        this(text, mode, words, unrecognized, List.of());
    }

    public Transcript {
        text = text == null ? "" : text.trim();
        words = words == null ? List.of() : List.copyOf(words);
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
    }

    /** {@link #text} followed by any further ranked alternatives, without duplicates or blanks. */
    public List<String> hypotheses() {
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>();
        if (!text.isBlank()) all.add(text);
        for (String alternative : alternatives) if (!alternative.isBlank()) all.add(alternative);
        return List.copyOf(all);
    }

    /** One recognized word with its timing (seconds from session start) and confidence (0..1). */
    public record Word(String text, double startSeconds, double endSeconds, double confidence) {}

    /** Mean word confidence, or empty when the backend reported no per-word confidence. */
    public OptionalDouble averageConfidence() {
        return words.stream().mapToDouble(Word::confidence).filter(c -> !Double.isNaN(c)).average();
    }
}

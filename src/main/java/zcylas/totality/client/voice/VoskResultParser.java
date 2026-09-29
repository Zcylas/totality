package zcylas.totality.client.voice;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import zcylas.totality.api.voice.recognition.RecognitionMode;
import zcylas.totality.api.voice.recognition.RecognitionRequest;
import zcylas.totality.api.voice.recognition.Transcript;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Converts Vosk's JSON results into engine-neutral {@link Transcript}s. Pure Java; no natives. */
final class VoskResultParser {

    /** Vosk's out-of-grammar token. */
    static final String UNKNOWN = "[unk]";

    private VoskResultParser() {}

    static String grammarJson(Collection<String> phrases) {
        JsonArray array = new JsonArray();
        phrases.forEach(array::add);
        array.add(UNKNOWN);
        return array.toString();
    }

    static String partialText(String json) {
        JsonObject obj = parse(json);
        return obj == null || !obj.has("partial") ? "" : clean(obj.get("partial").getAsString());
    }

    static Transcript parseFinal(String json, RecognitionRequest request) {
        JsonObject obj = parse(json);
        // With setMaxAlternatives, Vosk reports {"alternatives": [{"text", "confidence", "result"}...]}
        // ranked best first; the best one is read exactly like an ordinary result.
        List<String> alternatives = new ArrayList<>();
        if (obj != null && obj.has("alternatives") && obj.get("alternatives").isJsonArray()) {
            JsonObject best = null;
            for (JsonElement e : obj.getAsJsonArray("alternatives")) {
                if (!e.isJsonObject()) continue;
                JsonObject alternative = e.getAsJsonObject();
                if (best == null) best = alternative;
                alternatives.add(clean(alternative.has("text") ? alternative.get("text").getAsString() : ""));
            }
            obj = best;
        }
        String raw = obj != null && obj.has("text") ? obj.get("text").getAsString() : "";
        List<Transcript.Word> words = new ArrayList<>();
        if (obj != null && obj.has("result") && obj.get("result").isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray("result")) {
                JsonObject w = e.getAsJsonObject();
                String word = w.has("word") ? w.get("word").getAsString() : "";
                if (word.isEmpty() || word.equals(UNKNOWN)) continue;
                words.add(new Transcript.Word(word,
                        number(w, "start"), number(w, "end"), number(w, "conf")));
            }
        }
        String text = clean(raw);
        boolean unrecognized = text.isEmpty() || raw.contains(UNKNOWN)
                || (request.mode() == RecognitionMode.GRAMMAR && !request.phrases().contains(text));
        return new Transcript(text, request.mode(), words, unrecognized, alternatives);
    }

    private static String clean(String text) {
        return text.replace(UNKNOWN, " ").trim().replaceAll("\\s+", " ");
    }

    private static double number(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsDouble() : Double.NaN;
    }

    private static JsonObject parse(String json) {
        if (json == null || json.isBlank()) return null;
        JsonElement e = JsonParser.parseString(normalizeDecimals(json));
        return e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    /**
     * Vosk formats numbers with {@code std::to_string}, i.e. C {@code "%f"}, which follows the
     * process's C {@code LC_NUMERIC}. On Linux the JVM adopts the user's locale, so with e.g.
     * {@code it_IT}/{@code de_DE} Vosk emits {@code "conf" : 1,000000} — invalid JSON (observed on
     * the development machine). Numbers only ever appear as {@code key : value} with exactly six
     * decimals, so a comma in that exact position is rewritten to a dot; quoted words never match.
     * Done here rather than by changing the process-wide C locale, which Minecraft shares.
     */
    static String normalizeDecimals(String json) {
        return LOCALIZED_DECIMAL.matcher(json).replaceAll("$1.$2");
    }

    private static final java.util.regex.Pattern LOCALIZED_DECIMAL =
            java.util.regex.Pattern.compile("(:\\s*-?\\d+),(\\d{6})(?=\\s*[,}\\]])");
}

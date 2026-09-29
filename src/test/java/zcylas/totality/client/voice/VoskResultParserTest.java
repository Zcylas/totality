package zcylas.totality.client.voice;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.voice.recognition.RecognitionRequest;
import zcylas.totality.api.voice.recognition.Transcript;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Vosk JSON → Transcript conversion and platform gating (no natives loaded). */
class VoskResultParserTest {

    @Test
    void readsRankedAlternativesWhenRequested() {
        // Shape of Vosk 0.3.45 output with setMaxAlternatives (words carry no "conf" in this mode).
        String json = "{\"alternatives\" : [{\"confidence\" : 183.1, \"result\" : [{\"end\" : 0.6, \"start\" : 0.3, \"word\" : \"can\"},"
                + " {\"end\" : 0.9, \"start\" : 0.6, \"word\" : \"seal\"}], \"text\" : \"can seal\"},"
                + " {\"confidence\" : 180.2, \"result\" : [], \"text\" : \"can sell\"},"
                + " {\"confidence\" : 179.9, \"result\" : [], \"text\" : \"cancel\"}]}";
        Transcript t = VoskResultParser.parseFinal(json, RecognitionRequest.freeformWithAlternatives(5));
        assertEquals("can seal", t.text());
        assertEquals(List.of("can seal", "can sell", "cancel"), t.alternatives());
        assertEquals(List.of("can seal", "can sell", "cancel"), t.hypotheses());
        assertEquals(2, t.words().size());
        assertFalse(t.unrecognized());
    }

    @Test
    void parsesFreeformFinalWithWordDetail() {
        String json = """
                {"result":[{"conf":1.0,"end":0.6,"start":0.2,"word":"hello"},
                           {"conf":0.5,"end":1.1,"start":0.7,"word":"world"}],
                 "text":"hello world"}""";
        Transcript t = VoskResultParser.parseFinal(json, RecognitionRequest.freeform());
        assertEquals("hello world", t.text());
        assertFalse(t.unrecognized());
        assertEquals(2, t.words().size());
        assertEquals(new Transcript.Word("hello", 0.2, 0.6, 1.0), t.words().get(0));
        assertEquals(0.75, t.averageConfidence().orElseThrow(), 1e-9);
    }

    @Test
    void parsesCommaDecimalOutputFromNonEnglishLocales() {
        // Verbatim shape of Vosk 0.3.45 output observed with LC_NUMERIC=it_IT.UTF-8.
        String json = """
                {
                  "result" : [{
                      "conf" : 1,000000,
                      "end" : 1,110000,
                      "start" : 0,840000,
                      "word" : "one"
                    }, {
                      "conf" : 0,512345,
                      "end" : 1,530000,
                      "start" : 1,110000,
                      "word" : "zero"
                    }],
                  "text" : "one zero"
                }""";
        Transcript t = VoskResultParser.parseFinal(json, RecognitionRequest.freeform());
        assertEquals("one zero", t.text());
        assertEquals(new Transcript.Word("one", 0.84, 1.11, 1.0), t.words().get(0));
        assertEquals(0.512345, t.words().get(1).confidence(), 1e-9);
        // Dot-decimal output (C locale) is left untouched.
        String c = "{\"result\" : [{\"conf\" : 1.000000, \"end\" : 1.110000, \"start\" : 0.840000, \"word\" : \"one\"}], \"text\" : \"one\"}";
        assertEquals(c, VoskResultParser.normalizeDecimals(c));
        // A quoted word containing a comma-number is never rewritten.
        assertEquals("{\"text\" : \"1,000000\"}", VoskResultParser.normalizeDecimals("{\"text\" : \"1,000000\"}"));
    }

    @Test
    void emptyResultIsUnrecognized() {
        Transcript t = VoskResultParser.parseFinal("{\"text\":\"\"}", RecognitionRequest.freeform());
        assertEquals("", t.text());
        assertTrue(t.unrecognized());
    }

    @Test
    void grammarResultMustBeExactlyOnePhrase() {
        RecognitionRequest grammar = RecognitionRequest.grammar(Set.of("confirm", "cancel"));
        assertFalse(VoskResultParser.parseFinal("{\"text\":\"confirm\"}", grammar).unrecognized());

        Transcript unk = VoskResultParser.parseFinal("{\"text\":\"[unk]\"}", grammar);
        assertTrue(unk.unrecognized());
        assertEquals("", unk.text(), "the engine's unknown token never leaks into text");

        Transcript mixed = VoskResultParser.parseFinal("{\"text\":\"confirm [unk]\"}", grammar);
        assertTrue(mixed.unrecognized());
        assertEquals("confirm", mixed.text());

        assertTrue(VoskResultParser.parseFinal("{\"text\":\"confirm cancel\"}", grammar).unrecognized());
    }

    @Test
    void partialText() {
        assertEquals("hello th", VoskResultParser.partialText("{\"partial\":\"hello  th\"}"));
        assertEquals("", VoskResultParser.partialText("{\"partial\":\"\"}"));
        assertEquals("", VoskResultParser.partialText(""));
    }

    @Test
    void grammarJsonAlwaysIncludesUnknownToken() {
        assertEquals("[\"cancel\",\"confirm\",\"[unk]\"]", VoskResultParser.grammarJson(List.of("cancel", "confirm")));
        assertEquals("[\"say \\\"hi\\\"\",\"[unk]\"]", VoskResultParser.grammarJson(List.of("say \"hi\"")));
    }

    @Test
    void onlyLinuxAndWindowsX64AreSupported() {
        assertTrue(VoskPlatform.isSupported("Linux", "amd64"));
        assertTrue(VoskPlatform.isSupported("Windows 11", "amd64"));
        assertTrue(VoskPlatform.isSupported("Linux", "x86_64"));
        assertFalse(VoskPlatform.isSupported("Linux", "aarch64"));
        assertFalse(VoskPlatform.isSupported("Windows 11", "aarch64"));
        assertFalse(VoskPlatform.isSupported("Mac OS X", "x86_64"));
        assertFalse(VoskPlatform.isSupported("Mac OS X", "aarch64"));
        assertFalse(VoskPlatform.isSupported("FreeBSD", "amd64"));
    }
}

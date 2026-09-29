package zcylas.totality.api.voice.command;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.operator.OperatorAction;
import zcylas.totality.api.voice.recognition.RecognitionMode;
import zcylas.totality.api.voice.recognition.Transcript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OperatorVoiceTest {

    private static Transcript grammar(String text) {
        boolean unrecognized = text.isBlank() || !OperatorVoice.GRAMMAR_PHRASES.contains(text);
        return new Transcript(text, RecognitionMode.GRAMMAR, List.of(), unrecognized);
    }

    private static Transcript freeform(String best, String... lower) {
        List<String> alternatives = new java.util.ArrayList<>();
        alternatives.add(best);
        alternatives.addAll(List.of(lower));
        return new Transcript(best, RecognitionMode.FREEFORM, List.of(), best.isBlank(), alternatives);
    }

    @Test
    void theThreeCompletePhrasesNameTheirActions() {
        for (OperatorAction a : OperatorAction.values()) {
            String phrase = "operator mode " + a.spoken;
            OperatorVoice.Decision d = OperatorVoice.decide(grammar(phrase), freeform(phrase));
            assertEquals(a, d.action(), d.trace().toString());
        }
    }

    @Test
    void theWhitelistIsExactlyClearRainSurvivalCreative() {
        assertEquals(List.of(OperatorAction.CLEAR_RAIN, OperatorAction.SURVIVAL, OperatorAction.CREATIVE), List.of(OperatorAction.values()));
        assertNull(OperatorAction.byOrdinal(3));
        assertNull(OperatorAction.byOrdinal(-1));
    }

    @Test
    void incompleteExtraneousOrAmbiguousPhrasesNameNothing() {
        String[][] cases = {
                {"operator mode", "operator mode"},                                  // no command
                {"creative", "creative"},                                            // no prefix
                {"clear rain", "clear rain"},
                {"operator mode survival creative", "operator mode survival creative"}, // two commands
                {"operator mode survival", "operator mode survival now"},            // extra words
                {"operator mode confirm", "operator mode confirm"},                  // not a command
                {"operator mode creative", "operation mode creative"},               // sound-alike prefix
                {"operator mode creative", "operator known creative"},
                {"operator mode clear rain", "operator mode coloring"},              // misheard command
        };
        for (String[] c : cases) {
            OperatorVoice.Decision d = OperatorVoice.decide(grammar(c[0]), freeform(c[1]));
            assertNull(d.action(), c[0] + " / " + c[1] + " -> " + d.trace());
            assertTrue(d.involved(), "still Operator Mode's (never a hologram command): " + c[1]);
        }
    }

    @Test
    void lowerRankedAlternativesNeverAuthorize() {
        OperatorVoice.Decision d = OperatorVoice.decide(grammar("operator mode creative"),
                freeform("operator known creative", "operator mode creative"));
        assertNull(d.action());
        assertEquals(OperatorVoice.Rejection.NOT_CORROBORATED, d.rejection());
    }

    @Test
    void ordinaryHologramPhrasesAreNotOperatorMode() {
        for (String said : List.of("confirmed", "yes", "cancel", "dismiss", "okay", "hello there")) {
            Transcript g = new Transcript(said, RecognitionMode.GRAMMAR, List.of(), false);
            assertFalse(OperatorVoice.decide(g, freeform(said)).involved(), said);
        }
    }

    @Test
    void interimActivationNeedsBothPartialsToBeginWithTheFullPhrase() {
        assertTrue(OperatorVoice.activationHeard("operator mode", "operator mode"));
        assertTrue(OperatorVoice.activationHeard("operator mode clear", "operator mode"));
        assertFalse(OperatorVoice.activationHeard("operator", "operator mode"));
        assertFalse(OperatorVoice.activationHeard("operation mode", "operator mode"));
        assertFalse(OperatorVoice.activationHeard("operator mode", ""));
        assertFalse(OperatorVoice.activationHeard("the operator mode", "operator mode"));
    }
}

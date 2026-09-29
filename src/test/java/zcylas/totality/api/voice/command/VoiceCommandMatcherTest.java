package zcylas.totality.api.voice.command;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.voice.command.VoiceCommandMatcher.Rejection;
import zcylas.totality.api.voice.recognition.RecognitionMode;
import zcylas.totality.api.voice.recognition.Transcript;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Command decisions from two recognitions of the same audio. Several cases use the transcripts the
 * real bundled model produced for synthesized speech (Notification V2 interaction polish, 2026-09-26).
 */
class VoiceCommandMatcherTest {

    private static final Set<String> BOTH = Set.of("confirm", "cancel");
    private static final Set<String> CONFIRM_ONLY = Set.of("confirm");

    private static Transcript grammar(String text, boolean unrecognized, double... confidence) {
        List<Transcript.Word> words = confidence.length == 0 ? List.of()
                : List.of(new Transcript.Word(text, 0, 0.5, confidence[0]));
        return new Transcript(text, RecognitionMode.GRAMMAR, words, unrecognized);
    }

    private static Transcript free(String text) {
        return new Transcript(text, RecognitionMode.FREEFORM, List.of(), text.isBlank());
    }

    private static void rejects(Rejection why, VoiceCommandMatcher.Decision d) {
        assertFalse(d.accepted(), "expected rejection " + why + " but accepted " + d.command());
        assertEquals(why, d.rejection());
    }

    @Test
    void acceptsWhenBothRecognitionsAgree() {
        var d = VoiceCommandMatcher.decide(BOTH, grammar("confirm", false), free("confirm"));
        assertTrue(d.accepted());
        assertEquals("confirm", d.command());
    }

    @Test
    void anUnrecognizedGrammarResultIsNeverACommand() {
        // Real model, flite "cancel": grammar text "cancel" but flagged unrecognized (extra [unk] segment).
        rejects(Rejection.NOT_RECOGNIZED, VoiceCommandMatcher.decide(BOTH, grammar("cancel", true), free("cancel")));
        // Real model, flite "banana": grammar empty/unrecognized.
        rejects(Rejection.NOT_RECOGNIZED, VoiceCommandMatcher.decide(BOTH, grammar("", true), free("but man of")));
        rejects(Rejection.NOT_RECOGNIZED, VoiceCommandMatcher.decide(BOTH, null, free("confirm")));
    }

    @Test
    void grammarAloneIsNotEnoughWithoutFreeformCorroboration() {
        // Real model, flite (slt) "cancel": grammar "cancel", free-form heard "can seal".
        rejects(Rejection.NOT_CORROBORATED, VoiceCommandMatcher.decide(BOTH, grammar("cancel", false), free("can seal")));
        rejects(Rejection.NOT_CORROBORATED, VoiceCommandMatcher.decide(BOTH, grammar("confirm", false), free("")));
        rejects(Rejection.NOT_CORROBORATED, VoiceCommandMatcher.decide(BOTH, grammar("confirm", false), null));
        rejects(Rejection.NOT_CORROBORATED, VoiceCommandMatcher.decide(BOTH, grammar("confirm", false), free("information")));
        rejects(Rejection.NOT_CORROBORATED, VoiceCommandMatcher.decide(BOTH, grammar("confirm", false), free("confirmation")));
    }

    @Test
    void highConfidenceNeverReplacesCorroboration() {
        rejects(Rejection.NOT_CORROBORATED, VoiceCommandMatcher.decide(BOTH, grammar("confirm", false, 1.0), free("hello there")));
    }

    @Test
    void lowGrammarConfidenceRejectsEvenWhenCorroborated() {
        rejects(Rejection.LOW_CONFIDENCE, VoiceCommandMatcher.decide(BOTH, grammar("confirm", false, 0.3), free("confirm")));
        assertTrue(VoiceCommandMatcher.decide(BOTH, grammar("confirm", false, 0.9), free("confirm")).accepted());
    }

    @Test
    void twoCommandsHeardIsAmbiguous() {
        // Real model, flite "confirm cancel": free-form "confirmed cancel" (grammar was also unrecognized).
        rejects(Rejection.AMBIGUOUS, VoiceCommandMatcher.decide(BOTH, grammar("confirm", false), free("confirm cancel")));
    }

    @Test
    void longSpeechIsDictationNotACommand() {
        rejects(Rejection.TOO_LONG, VoiceCommandMatcher.decide(BOTH, grammar("confirm", false),
                free("please confirm the order now")));
    }

    @Test
    void aPhraseTheContextDoesNotOfferIsNeverAccepted() {
        rejects(Rejection.NOT_RECOGNIZED, VoiceCommandMatcher.decide(CONFIRM_ONLY, grammar("cancel", false), free("cancel")));
    }

    private static Transcript freeN(String... hypotheses) {
        return new Transcript(hypotheses[0], RecognitionMode.FREEFORM, List.of(), false, List.of(hypotheses));
    }

    private static final Set<String> WITH_ALIAS = Set.of("confirm", "confirmed", "cancel");

    private static String canonical(String phrase) {
        return phrase.equals("confirmed") ? "confirm" : phrase;
    }

    @Test
    void grammarAgreementIsNotEnoughForConfusableSpeech() {
        // Real model, flite "gone from": grammar returned "confirm" at 0.76 — free-form never did.
        rejects(Rejection.NOT_CORROBORATED, VoiceCommandMatcher.decide(WITH_ALIAS, VoiceCommandMatcherTest::canonical,
                grammar("confirm", false, 0.76), freeN("go on front", "go and front", "go in front", "gone from")));
        // Real model, flite "conform": grammar "confirm" at 1.00 — free-form "conform".
        rejects(Rejection.NOT_CORROBORATED, VoiceCommandMatcher.decide(WITH_ALIAS, VoiceCommandMatcherTest::canonical,
                grammar("confirm", false, 1.0), freeN("conform")));
    }

    @Test
    void aDifferentCommandRankedFirstRejects() {
        rejects(Rejection.NOT_CORROBORATED, VoiceCommandMatcher.decide(BOTH, grammar("confirm", false),
                freeN("cancel", "confirm")));
        rejects(Rejection.AMBIGUOUS, VoiceCommandMatcher.decide(BOTH, grammar("confirm", false),
                freeN("confirm cancel", "hello")));
    }

    @Test
    void anAliasNamesTheSameCommandAndIsNeverAmbiguous() {
        var d = VoiceCommandMatcher.decide(WITH_ALIAS, VoiceCommandMatcherTest::canonical,
                grammar("confirmed", false, 1.0), free("confirmed"));
        assertEquals("confirm", d.command());
        assertTrue(d.acceptedByBestHypothesisAlone());
    }

    @Test
    void everyDecisionExplainsItself() {
        var d = VoiceCommandMatcher.decide(BOTH, grammar("confirm", false), free("corn field"));
        assertTrue(d.trace().stream().anyMatch(l -> l.startsWith("rule 1 pass")));
        assertTrue(d.trace().stream().anyMatch(l -> l.startsWith("rule 3 FAIL")));
    }

    // ── Real-microphone evidence (Stefan, 2026-09-26, run/…/command-attempts.log) ─────────────────

    private static final Set<String> INTENTS = Set.of("confirmed", "confirm", "yes", "okay", "ok", "sure", "accept",
            "cancel", "no", "decline", "reject");

    private static String intent(String phrase) {
        return switch (phrase) {
            case "cancel", "no", "decline", "reject" -> "cancel";
            default -> "confirm";
        };
    }

    private static VoiceCommandMatcher.Decision real(String grammarText, String... hypotheses) {
        return VoiceCommandMatcher.decide(INTENTS, VoiceCommandMatcherTest::intent,
                grammar(grammarText, false, 1.0), freeN(hypotheses));
    }

    @Test
    void lowerRankedAlternativesNeverAuthorize() {
        // These three executed under the previous top-5 rule — all were control phrases.
        rejects(Rejection.NOT_CORROBORATED, real("confirm", "conform", "confirm"));
        rejects(Rejection.NOT_CORROBORATED, real("confirm", "conform", "come from", "confirm", "come form"));
        rejects(Rejection.NOT_CORROBORATED, real("cancel", "castle", "cancel", "council"));
        // …and so is a genuine "confirm" heard as "come from": honest rejection, never a guess.
        rejects(Rejection.NOT_CORROBORATED, real("confirm", "come from", "confirm"));
        var d = real("confirm", "configure them", "confirm", "confirmed");
        assertTrue(d.trace().stream().anyMatch(l -> l.contains("deliberately NOT accepted")), String.valueOf(d.trace()));
    }

    @Test
    void whatWorkedWithTheRealMicrophoneStillWorks() {
        assertEquals("confirm", real("confirmed", "confirmed").command());
        assertEquals("cancel", real("cancel", "cancel", "council").command());
    }

    @Test
    void intentAliasesCorroborateEachOtherWordForWord() {
        assertEquals("confirm", real("confirm", "confirmed").command(), "'confirmed' is an alias, not an inflection");
        assertEquals("confirm", real("yes", "yes").command());
        assertEquals("confirm", real("okay", "okay").command());
        assertEquals("cancel", real("no", "no").command());
        rejects(Rejection.AMBIGUOUS, real("yes", "yes no"));
        rejects(Rejection.NOT_CORROBORATED, real("no", "know"));
        rejects(Rejection.NOT_CORROBORATED, real("no", "nod")); // no inflection matching any more
        rejects(Rejection.NOT_CORROBORATED, real("confirm", "confirmation"));
    }

    @Test
    void exactWordsOnly() {
        assertTrue(VoiceCommandMatcher.contains("please confirm".split(" "), new String[] {"confirm"}));
        assertFalse(VoiceCommandMatcher.contains("confirming".split(" "), new String[] {"confirm"}));
        assertFalse(VoiceCommandMatcher.contains("can".split(" "), new String[] {"cancel"}));
    }
}

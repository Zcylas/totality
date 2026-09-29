package zcylas.totality.client.voice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import zcylas.totality.api.voice.capture.FakeCaptureDeviceProvider;
import zcylas.totality.api.voice.command.VoiceCommandContext;
import zcylas.totality.api.voice.operator.OperatorChannel;
import zcylas.totality.api.voice.command.VoiceCommandMatcher;
import zcylas.totality.api.voice.capture.MicrophoneManager;
import zcylas.totality.api.voice.capture.MicrophoneManager.CaptureResult;
import zcylas.totality.api.voice.capture.MicrophoneManager.EndReason;
import zcylas.totality.api.voice.recognition.BackendStatus;
import zcylas.totality.api.voice.recognition.RecognitionBackend;
import zcylas.totality.api.voice.recognition.RecognitionException;
import zcylas.totality.api.voice.recognition.RecognitionMode;
import zcylas.totality.api.voice.recognition.RecognitionRequest;
import zcylas.totality.api.voice.recognition.RecognitionSession;
import zcylas.totality.api.voice.recognition.Transcript;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The push-to-talk state machine with a scripted microphone, a scripted recognition backend and a
 * manually drained "client thread" — deterministic, no audio hardware, no natives.
 */
class VoiceInputControllerTest {

    private static final MicrophoneManager.Config TEST_MIC = new MicrophoneManager.Config(1_600, 16_000 * 30, 10_000, 2, 0);

    @TempDir
    Path tmp;

    final FakeCaptureDeviceProvider provider = new FakeCaptureDeviceProvider();
    final ConcurrentLinkedQueue<Runnable> clientQueue = new ConcurrentLinkedQueue<>();
    final RecordingUi ui = new RecordingUi();
    final List<ScriptedBackend> backends = new ArrayList<>();
    final AtomicInteger crashRecoveries = new AtomicInteger();
    VoiceSettings settings;
    MicrophoneManager mic;
    VoiceInputController controller;
    volatile BackendStatus nextLoadResult = BackendStatus.ready("scripted");
    volatile String scriptedText = "hello this is a test";
    volatile String scriptedGrammarText = "";
    /** What BOTH sessions report as their partial result while audio arrives. */
    volatile String scriptedPartial = "";
    volatile VoiceCommandContext commandContext;

    void create(MicrophoneManager.Config config) {
        settings = new VoiceSettings(tmp.resolve("totality-voice.properties"));
        mic = new MicrophoneManager(provider, r -> new Thread(r, "test-capture"), config);
        controller = new VoiceInputController(() -> {
            ScriptedBackend b = new ScriptedBackend(nextLoadResult);
            backends.add(b);
            return b;
        }, crashRecoveries::incrementAndGet, mic, clientQueue::add, ui, settings, System::nanoTime, tmp.resolve("debug"),
                () -> commandContext);
    }

    @AfterEach
    void tearDown() {
        if (mic != null) mic.shutdown(2_000);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    void drain() {
        Runnable r;
        while ((r = clientQueue.poll()) != null) r.run();
    }

    void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("timed out; notices=" + ui.notices + " state=" + controller.state());
            drain();
            Thread.sleep(2);
        }
        drain();
    }

    void loadReady() throws InterruptedException {
        controller.onPushToTalkPressed();
        await(() -> controller.state() == VoiceState.READY);
    }

    /** Press, let the scripted audio arrive, release, and wait for the result. */
    void speak() throws InterruptedException {
        int before = ui.results();
        controller.onPushToTalkPressed();
        assertEquals(VoiceState.LISTENING, controller.state());
        ScriptedBackend b = backends.getLast();
        await(() -> b.acceptedSamples.get() >= Math.min(provider.samplesPerStream, 16_000));
        controller.onPushToTalkReleased();
        assertEquals(VoiceState.RECOGNIZING, controller.state());
        await(() -> ui.results() > before);
    }

    // ── tests ──────────────────────────────────────────────────────────────────

    @Test
    void firstPressLoadsWithoutListeningAndANewPressIsRequired() throws Exception {
        create(TEST_MIC);
        assertEquals(VoiceState.UNLOADED, controller.state());
        assertTrue(backends.isEmpty(), "nothing is created before push-to-talk");
        controller.onPushToTalkPressed();
        assertEquals(VoiceState.LOADING, controller.state());
        assertEquals(VoiceUi.Notice.LOADING, ui.lastNotice());
        controller.onPushToTalkPressed();
        assertEquals(VoiceUi.Notice.LOADING, ui.lastNotice(), "pressing again while loading only repeats the notice");
        await(() -> controller.state() == VoiceState.READY);
        assertEquals(VoiceUi.Notice.READY, ui.lastNotice());
        assertEquals(0, provider.opens.get(), "the microphone is never opened by the loading press");
        assertEquals(1, backends.size());
        assertTrue(controller.lastLoadMillis() >= 0);
    }

    // ── Notification V2: contextual voice commands ──────────────────────────────

    /** Press with a command context, let audio arrive, release, and wait for the context's verdict. */
    void speakCommand(FakeContext context) throws InterruptedException {
        int before = context.events.size();
        controller.onPushToTalkPressed(VoiceInputController.PushToTalkMode.COMMAND);
        assertEquals(VoiceState.LISTENING, controller.state());
        ScriptedBackend b = backends.getLast();
        await(() -> b.acceptedSamples.get() >= Math.min(provider.samplesPerStream, 16_000));
        controller.onPushToTalkReleased();
        await(() -> controller.state() == VoiceState.READY && context.events.size() > before + 1);
    }

    @Test
    void aCorroboratedCommandExecutesOnceAndNeverBecomesATranscript() throws Exception {
        create(TEST_MIC);
        loadReady();
        FakeContext context = new FakeContext();
        commandContext = context;
        scriptedGrammarText = "confirm";
        scriptedText = "confirmed";
        speakCommand(context);
        assertEquals(List.of("confirm"), context.executed);
        assertEquals(List.of("listening", "accepted confirm"), context.events);
        assertTrue(ui.transcripts.isEmpty(), "a command is never shown as dictation");
        assertNull(controller.latestTranscript(), "Edit & Send never sees a command");
        ScriptedBackend b = backends.getLast();
        assertEquals(2, b.opened.get(), "one FREEFORM and one GRAMMAR session on the same audio");
        assertEquals(2, b.closed.get(), "both sessions closed");
        assertEquals(VoiceState.READY, controller.state());
    }

    @Test
    void commandsAskForRankedAlternativesWhileDictationDoesNot() throws Exception {
        create(TEST_MIC);
        loadReady();
        FakeContext context = new FakeContext();
        commandContext = context;
        scriptedGrammarText = "confirm";
        scriptedText = "confirm";
        speakCommand(context);
        List<RecognitionRequest> command = backends.getLast().requests;
        assertTrue(command.stream().anyMatch(r -> r.mode() == RecognitionMode.FREEFORM
                && r.alternatives() == VoiceCommandMatcher.CORROBORATION_HYPOTHESES));
        assertTrue(command.stream().anyMatch(r -> r.mode() == RecognitionMode.GRAMMAR && r.alternatives() == 0));
        commandContext = null;
        int before = command.size();
        speak();
        assertEquals(RecognitionRequest.freeform(), command.get(before), "dictation keeps its plain free-form session");
    }

    @Test
    void diagnosticsRecordEveryAttemptOnlyWhenEnabled() throws Exception {
        create(TEST_MIC);
        loadReady();
        List<VoiceInputController.CommandAttempt> attempts = new ArrayList<>();
        FakeContext context = new FakeContext();
        commandContext = context;
        scriptedGrammarText = "confirm";
        scriptedText = "corn field";
        speakCommand(context);
        assertTrue(attempts.isEmpty(), "off by default");
        controller.setCommandDiagnostics(attempts::add);
        speakCommand(context);
        VoiceInputController.CommandAttempt a = attempts.getLast();
        assertTrue(a.offered());
        assertEquals("confirm", a.grammar().text());
        assertEquals("corn field", a.freeform().text());
        assertEquals(VoiceCommandMatcher.Rejection.NOT_CORROBORATED, a.decision().rejection());
        assertNull(a.currentAtExecution());
        commandContext = null;
        speak();
        assertFalse(attempts.getLast().offered(), "a press without a context is recorded as not offered");
        assertEquals(1, ui.transcripts.size(), "diagnostics never add chat lines");
    }

    @Test
    void grammarWithoutFreeformCorroborationPerformsNothing() throws Exception {
        create(TEST_MIC);
        loadReady();
        FakeContext context = new FakeContext();
        commandContext = context;
        scriptedGrammarText = "confirm";
        scriptedText = "hello there";
        speakCommand(context);
        assertTrue(context.executed.isEmpty());
        assertEquals("rejected " + VoiceCommandMatcher.Rejection.NOT_CORROBORATED, context.events.getLast());
        assertTrue(ui.transcripts.isEmpty());
    }

    @Test
    void outOfGrammarSpeechPerformsNothing() throws Exception {
        create(TEST_MIC);
        loadReady();
        FakeContext context = new FakeContext();
        commandContext = context;
        scriptedGrammarText = "";
        scriptedText = "but man of";
        speakCommand(context);
        assertTrue(context.executed.isEmpty());
        assertEquals("rejected " + VoiceCommandMatcher.Rejection.NOT_RECOGNIZED, context.events.getLast());
    }

    @Test
    void aTargetThatChangedDuringRecognitionIsNeverActedOn() throws Exception {
        create(TEST_MIC);
        loadReady();
        FakeContext context = new FakeContext();
        commandContext = context;
        scriptedGrammarText = "confirm";
        scriptedText = "confirm";
        int before = context.events.size();
        controller.onPushToTalkPressed(VoiceInputController.PushToTalkMode.COMMAND);
        ScriptedBackend b = backends.getLast();
        await(() -> b.acceptedSamples.get() >= Math.min(provider.samplesPerStream, 16_000));
        context.current = false; // e.g. the hologram was interrupted while the player was speaking
        controller.onPushToTalkReleased();
        await(() -> controller.state() == VoiceState.READY && context.events.size() > before + 1);
        assertTrue(context.executed.isEmpty());
        assertEquals("rejected " + VoiceCommandMatcher.Rejection.STALE, context.events.getLast());
    }

    @Test
    void aCancelledCommandUtterancePerformsNothing() throws Exception {
        create(TEST_MIC);
        loadReady();
        FakeContext context = new FakeContext();
        commandContext = context;
        scriptedGrammarText = "confirm";
        scriptedText = "confirm";
        controller.onPushToTalkPressed(VoiceInputController.PushToTalkMode.COMMAND);
        controller.cancelUtterance("a screen was opened");
        drain();
        Thread.sleep(50);
        drain();
        assertTrue(context.executed.isEmpty());
        assertEquals(List.of("listening", "rejected " + VoiceCommandMatcher.Rejection.CANCELLED), context.events);
    }

    @Test
    void commandModeWithoutAContextDoesNothingAndNeverDictates() throws Exception {
        create(TEST_MIC);
        loadReady();
        commandContext = null;
        int opensBefore = provider.opens.get();
        controller.onPushToTalkPressed(VoiceInputController.PushToTalkMode.COMMAND);
        assertEquals(VoiceState.READY, controller.state(), "no utterance starts");
        assertEquals(opensBefore, provider.opens.get(), "the microphone is never opened");
        assertEquals(VoiceUi.Notice.NO_COMMAND, ui.lastNotice());
        assertTrue(ui.transcripts.isEmpty(), "never falls back to dictation");
    }

    @Test
    void dictationModeDictatesEvenWhileACommandContextExists() throws Exception {
        create(TEST_MIC);
        loadReady();
        FakeContext context = new FakeContext();
        commandContext = context;
        scriptedText = "confirm";
        scriptedGrammarText = "confirm";
        speak(); // dictation press (modifier held)
        assertEquals(List.of("Confirm"), ui.transcripts);
        assertTrue(context.executed.isEmpty(), "dictation never executes a command");
        assertTrue(context.events.isEmpty(), "the context is not even consulted");
        assertEquals(1, backends.getLast().opened.get(), "dictation opens only its FREEFORM session");
        assertEquals(VoiceInputController.PushToTalkMode.DICTATION, controller.utteranceMode());
    }

    /** A command owner that records what it was asked to do. */
    static final class FakeContext implements VoiceCommandContext {
        final List<String> executed = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        volatile boolean current = true;

        @Override
        public Set<String> phrases() {
            return Set.of("confirm", "confirmed", "cancel");
        }

        @Override
        public String commandOf(String phrase) {
            return phrase.equals("confirmed") ? "confirm" : phrase;
        }

        @Override
        public boolean isCurrent() {
            return current;
        }

        @Override
        public boolean execute(String phrase) {
            if (!current) return false;
            executed.add(phrase);
            return true;
        }

        @Override
        public void listening() {
            events.add("listening");
        }

        @Override
        public void accepted(String phrase) {
            events.add("accepted " + phrase);
        }

        @Override
        public void rejected(VoiceCommandMatcher.Rejection reason) {
            events.add("rejected " + reason);
        }
    }

    // ── Operator Mode ──────────────────────────────────────────────────────────

    /** Records what the controller asked the (fake) server. */
    static final class FakeChannel implements OperatorChannel {
        volatile boolean offer = true;
        final List<Integer> authorizationRequests = new java.util.concurrent.CopyOnWriteArrayList<>();
        final List<String> submitted = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile int lastSubmittedId = -1;

        @Override public boolean mayOffer() { return offer; }
        @Override public void requestAuthorization(int requestId) { authorizationRequests.add(requestId); }
        @Override public void submit(int requestId, zcylas.totality.api.operator.OperatorAction action) {
            submitted.add(action.name());
            lastSubmittedId = requestId;
        }
    }

    final FakeChannel channel = new FakeChannel();

    /** Command press, audio, release; waits until the utterance is complete. */
    void speakOperator() throws InterruptedException {
        int before = ui.feedback.size();
        controller.onPushToTalkPressed(VoiceInputController.PushToTalkMode.COMMAND);
        assertEquals(VoiceState.LISTENING, controller.state());
        ScriptedBackend b = backends.getLast();
        await(() -> b.acceptedSamples.get() >= Math.min(provider.samplesPerStream, 16_000));
        controller.onPushToTalkReleased();
        await(() -> controller.state() == VoiceState.READY && ui.feedback.size() > before);
    }

    @Test
    void operatorWithoutAHologramListensForOperatorModeOnlyAndNeverDictates() throws Exception {
        create(TEST_MIC);
        controller.setOperatorChannel(channel);
        loadReady();
        commandContext = null;
        scriptedGrammarText = "operator mode survival";
        scriptedText = "operator mode survival";
        speakOperator();
        assertEquals(List.of("SURVIVAL"), channel.submitted, "exactly one typed request");
        assertEquals(VoiceFeedback.Kind.AWAITING_SERVER, ui.lastFeedback(), "no success before the server answers");
        assertTrue(ui.transcripts.isEmpty(), "never a transcript");
        RecognitionRequest grammar = backends.getLast().requests.stream()
                .filter(r -> r.mode() == RecognitionMode.GRAMMAR).findFirst().orElseThrow();
        assertTrue(grammar.phrases().containsAll(zcylas.totality.api.voice.command.OperatorVoice.GRAMMAR_PHRASES));
    }

    @Test
    void nonOperatorWithoutAHologramStillNeverOpensTheMicrophone() throws Exception {
        create(TEST_MIC);
        channel.offer = false;
        controller.setOperatorChannel(channel);
        loadReady();
        commandContext = null;
        int opensBefore = provider.opens.get();
        controller.onPushToTalkPressed(VoiceInputController.PushToTalkMode.COMMAND);
        assertEquals(VoiceState.READY, controller.state());
        assertEquals(opensBefore, provider.opens.get());
        assertEquals(VoiceFeedback.Kind.NO_COMMAND, ui.lastFeedback());
    }

    @Test
    void operatorPhraseWithAHologramOpenNeverTouchesTheHologram() throws Exception {
        create(TEST_MIC);
        controller.setOperatorChannel(channel);
        loadReady();
        FakeContext context = new FakeContext();
        commandContext = context;
        scriptedGrammarText = "operator mode creative";
        scriptedText = "operator mode creative";
        speakOperator();
        assertEquals(List.of("CREATIVE"), channel.submitted);
        assertTrue(context.executed.isEmpty(), "the hologram was not answered");
        assertFalse(context.events.stream().anyMatch(e -> e.startsWith("accepted")));
    }

    @Test
    void hologramCommandsStillWorkForOperators() throws Exception {
        create(TEST_MIC);
        controller.setOperatorChannel(channel);
        loadReady();
        FakeContext context = new FakeContext();
        commandContext = context;
        scriptedGrammarText = "confirmed";
        scriptedText = "confirmed";
        speakOperator();
        assertEquals(List.of("confirm"), context.executed);
        assertTrue(channel.submitted.isEmpty());
        assertEquals(VoiceFeedback.Kind.RECOGNIZED, ui.lastFeedback(), "recognized only after the owner executed");
    }

    @Test
    void operatorPrefixWithAHologramPhraseDoesNeither() throws Exception {
        create(TEST_MIC);
        controller.setOperatorChannel(channel);
        loadReady();
        FakeContext context = new FakeContext();
        commandContext = context;
        for (String[] said : new String[][] {{"operator mode confirm", "operator mode confirm"},
                {"operator mode survival creative", "operator mode survival creative"},
                {"operator mode", "operator mode"}, {"creative", "creative"},
                {"operator mode creative", "operation mode creative"},
                {"operator mode survival", "operator mode survival now"}}) {
            scriptedGrammarText = said[0];
            scriptedText = said[1];
            speakOperator();
        }
        assertTrue(channel.submitted.isEmpty(), "no request: " + channel.submitted);
        assertTrue(context.executed.isEmpty(), "no hologram action: " + context.executed);
    }

    @Test
    void interimActivationAsksTheServerButExecutesNothingAndKeepsListening() throws Exception {
        create(TEST_MIC);
        controller.setOperatorChannel(channel);
        loadReady();
        commandContext = null;
        scriptedPartial = "operator mode";
        scriptedGrammarText = "operator mode";
        scriptedText = "operator mode";
        controller.onPushToTalkPressed(VoiceInputController.PushToTalkMode.COMMAND);
        await(() -> controller.operatorPhase() == VoiceInputController.OperatorPhase.REQUESTING);
        assertEquals(VoiceState.LISTENING, controller.state(), "the same hold keeps listening");
        assertTrue(mic.isCapturing(), "the microphone was not restarted or stopped");
        assertEquals(1, channel.authorizationRequests.size());
        assertTrue(channel.submitted.isEmpty(), "partial results never execute");
        controller.onOperatorAuthorization(channel.authorizationRequests.getFirst(), true);
        assertEquals(VoiceInputController.OperatorPhase.AUTHORIZED, controller.operatorPhase());
        // Released after the activation phrase alone: the context ends, nothing is requested.
        ScriptedBackend b = backends.getLast();
        await(() -> b.acceptedSamples.get() >= Math.min(provider.samplesPerStream, 16_000));
        controller.onPushToTalkReleased();
        await(() -> controller.state() == VoiceState.READY && !ui.feedback.isEmpty());
        assertTrue(channel.submitted.isEmpty());
        assertEquals(VoiceFeedback.Kind.NOT_UNDERSTOOD, ui.lastFeedback());
    }

    @Test
    void onlyTheServersAnswerForTheCurrentRequestIsShown() throws Exception {
        create(TEST_MIC);
        controller.setOperatorChannel(channel);
        loadReady();
        commandContext = null;
        scriptedGrammarText = "operator mode clear rain";
        scriptedText = "operator mode clear rain";
        speakOperator();
        int id = channel.lastSubmittedId;
        controller.onOperatorResult(id + 7, zcylas.totality.api.operator.OperatorResult.EXECUTED);
        assertEquals(VoiceFeedback.Kind.AWAITING_SERVER, ui.lastFeedback(), "an unknown/stale answer is ignored");
        controller.onOperatorResult(id, zcylas.totality.api.operator.OperatorResult.DENIED);
        assertEquals(VoiceFeedback.Kind.DENIED, ui.lastFeedback());
        controller.onOperatorResult(id, zcylas.totality.api.operator.OperatorResult.EXECUTED);
        assertEquals(VoiceFeedback.Kind.DENIED, ui.lastFeedback(), "a repeated answer is ignored");
        // A new utterance makes any late answer to the old request stale.
        speakOperator();
        int second = channel.lastSubmittedId;
        assertTrue(second > id);
        controller.onOperatorResult(id, zcylas.totality.api.operator.OperatorResult.EXECUTED);
        assertEquals(VoiceFeedback.Kind.AWAITING_SERVER, ui.lastFeedback());
        controller.onOperatorResult(second, zcylas.totality.api.operator.OperatorResult.EXECUTED);
        assertEquals(VoiceFeedback.Kind.EXECUTED, ui.lastFeedback());
        assertEquals("clear rain", ui.feedback.getLast().detail());
    }

    // ── Notification V2: the model-initialization presentation fires once per load ──

    @Test
    void modelInitializationIsReportedOncePerLoadAndNeverOnLaterPresses() throws Exception {
        create(TEST_MIC);
        controller.onPushToTalkPressed();
        controller.onPushToTalkPressed(); // "still loading" is an ordinary notice, not a second event
        await(() -> controller.state() == VoiceState.READY);
        assertEquals(List.of(VoiceUi.Notice.LOADING, VoiceUi.Notice.READY), ui.modelInits);
        speak();
        speak();
        assertEquals(List.of(VoiceUi.Notice.LOADING, VoiceUi.Notice.READY), ui.modelInits,
                "push-to-talk while ready never repeats the ready presentation");
        assertEquals(VoiceState.READY, controller.state());
    }

    @Test
    void failedInitializationIsReportedOnceAndLaterPressesUseOrdinaryNotices() throws Exception {
        nextLoadResult = BackendStatus.failed(BackendStatus.FailureReason.MODEL_INVALID, "bad model");
        create(TEST_MIC);
        controller.onPushToTalkPressed();
        await(() -> controller.state() == VoiceState.FAILED);
        assertEquals(List.of(VoiceUi.Notice.LOADING, VoiceUi.Notice.BACKEND_FAILED), ui.modelInits);
        int notices = ui.notices.size();
        controller.onPushToTalkPressed();
        assertEquals(List.of(VoiceUi.Notice.LOADING, VoiceUi.Notice.BACKEND_FAILED), ui.modelInits);
        assertEquals(notices + 1, ui.notices.size(), "the failure is explained again as a plain notice");
        assertEquals(VoiceUi.Notice.BACKEND_FAILED, ui.lastNotice());

        nextLoadResult = BackendStatus.ready("scripted");
        assertTrue(controller.retry());
        loadReady();
        assertEquals(List.of(VoiceUi.Notice.LOADING, VoiceUi.Notice.BACKEND_FAILED,
                VoiceUi.Notice.LOADING, VoiceUi.Notice.READY), ui.modelInits, "an explicit retry is a new initialization");
    }

    @Test
    void anAbandonedLoadNeverReportsReady() throws Exception {
        CompletableFuture<BackendStatus> gate = new CompletableFuture<>();
        ScriptedBackend.nextLoadGate = gate;
        create(TEST_MIC);
        controller.onPushToTalkPressed();
        controller.setEnabled(false);
        gate.complete(BackendStatus.ready("scripted"));
        Thread.sleep(50);
        drain();
        assertEquals(List.of(VoiceUi.Notice.LOADING), ui.modelInits);
        assertEquals(VoiceState.DISABLED, controller.state());
    }

    @Test
    void oneUtteranceStreamsIntoOneFreshFreeformSessionAndShowsTheTranscriptLocally() throws Exception {
        create(TEST_MIC);
        loadReady();
        speak();
        ScriptedBackend b = backends.getFirst();
        assertEquals(List.of("hello this is a test".replace("hello", "Hello")), ui.transcripts);
        assertEquals("Hello this is a test", controller.latestTranscript());
        assertEquals(VoiceState.READY, controller.state());
        assertEquals(1, b.opened.get());
        assertEquals(1, b.finished.get());
        assertEquals(1, b.closed.get());
        assertEquals(RecognitionMode.FREEFORM, b.lastMode);
        assertEquals(16_000, b.acceptedSamples.get(), "all captured audio reached the recognizer");
        assertEquals(1, provider.closes.get(), "microphone closed after the utterance");
        assertFalse(mic.isCapturing());
        VoiceInputController.UtteranceStats stats = controller.lastStats();
        assertEquals(VoiceInputController.Outcome.SUCCESS, stats.outcome());
        assertEquals(1.0, stats.audioSeconds(), 1e-9);
        assertTrue(stats.releaseToResultMillis() >= 0);
        assertEquals(5, stats.words());
        assertTrue(ui.logs.stream().noneMatch(l -> l.contains("hello")), "release logs never contain the transcript");
        assertTrue(ui.debugs.stream().anyMatch(l -> l.contains("hello this is a test")), "debug lines may");
    }

    @Test
    void repeatedPressReleaseUsesOneSessionPerUtterance() throws Exception {
        create(TEST_MIC);
        loadReady();
        for (int i = 0; i < 3; i++) speak();
        ScriptedBackend b = backends.getFirst();
        assertEquals(3, b.opened.get());
        assertEquals(3, b.finished.get(), "each session finalized exactly once");
        assertEquals(3, b.closed.get());
        assertEquals(3, ui.transcripts.size());
        assertEquals(3, provider.opens.get());
        assertEquals(3, provider.closes.get());
    }

    @Test
    void screenOpeningWhileListeningCancelsWithoutAResult() throws Exception {
        create(TEST_MIC);
        loadReady();
        controller.onPushToTalkPressed();
        ScriptedBackend b = backends.getFirst();
        await(() -> b.acceptedSamples.get() > 0);
        controller.cancelUtterance("a screen was opened");
        assertEquals(VoiceState.READY, controller.state());
        assertEquals(VoiceUi.Notice.CANCELLED, ui.lastNotice());
        await(() -> b.closed.get() == 1 && !mic.isCapturing());
        drain();
        assertEquals(0, b.finished.get(), "a cancelled session is closed, never finalized");
        assertTrue(ui.transcripts.isEmpty());
        assertEquals(1, provider.closes.get());
        controller.onPushToTalkReleased();
        assertEquals(VoiceState.READY, controller.state(), "a late key release after cancel does nothing");
    }

    @Test
    void disconnectDuringRecognitionDropsTheStaleResult() throws Exception {
        create(TEST_MIC);
        loadReady();
        ScriptedBackend b = backends.getFirst();
        b.finishGate = new CountDownLatch(1);
        controller.onPushToTalkPressed();
        await(() -> b.acceptedSamples.get() == 16_000);
        controller.onPushToTalkReleased();
        await(() -> b.finishing.get() == 1); // worker is inside finish()
        controller.onDisconnect();
        assertEquals(VoiceState.READY, controller.state());
        b.finishGate.countDown();
        await(() -> b.closed.get() == 1);
        Thread.sleep(20);
        drain();
        assertTrue(ui.transcripts.isEmpty(), "a result finishing after disconnect must not appear");
        assertNull(controller.latestTranscript());
        assertTrue(ui.logs.stream().anyMatch(l -> l.contains("discarded (stale)")));
    }

    @Test
    void staleResultNeverAttachesToTheNextUtterance() throws Exception {
        create(TEST_MIC);
        loadReady();
        ScriptedBackend b = backends.getFirst();
        b.finishGate = new CountDownLatch(1);
        scriptedText = "first phrase";
        controller.onPushToTalkPressed();
        await(() -> b.acceptedSamples.get() == 16_000);
        controller.onPushToTalkReleased();
        await(() -> b.finishing.get() == 1);
        controller.cancelUtterance(null);             // e.g. the window lost focus
        b.finishGate.countDown();
        b.finishGate = null;
        scriptedText = "second phrase";
        speak();                                       // a new utterance
        await(() -> b.closed.get() == 2);
        drain();
        assertEquals(List.of("Second phrase"), ui.transcripts);
    }

    @Test
    void shutdownDuringCaptureReleasesMicrophoneAndEngine() throws Exception {
        provider.samplesPerStream = Integer.MAX_VALUE;
        create(new MicrophoneManager.Config(1_600, Integer.MAX_VALUE, 10_000, 2, 0));
        loadReady();
        controller.onPushToTalkPressed();
        ScriptedBackend b = backends.getFirst();
        await(() -> b.acceptedSamples.get() > 0);
        controller.shutdown();
        assertFalse(mic.isCapturing(), "shutdown waits for the device to be released");
        assertEquals(1, provider.closes.get());
        assertTrue(b.backendClosed, "the engine is released");
        drain();
        assertTrue(ui.transcripts.isEmpty());
        assertEquals(VoiceState.UNLOADED, controller.state());
    }

    @Test
    void backendFailureIsReportedAndOnlyRetriedOnRequest() throws Exception {
        nextLoadResult = BackendStatus.failed(BackendStatus.FailureReason.MODEL_INVALID, "bad model");
        create(TEST_MIC);
        controller.onPushToTalkPressed();
        await(() -> controller.state() == VoiceState.FAILED);
        assertEquals(VoiceUi.Notice.BACKEND_FAILED, ui.lastNotice());
        controller.onPushToTalkPressed();
        assertEquals(VoiceState.FAILED, controller.state(), "never retries by itself");
        assertEquals(1, backends.size());
        assertEquals(0, crashRecoveries.get());

        nextLoadResult = BackendStatus.ready("scripted");
        assertTrue(controller.retry());
        assertEquals(VoiceState.UNLOADED, controller.state());
        assertEquals(0, crashRecoveries.get(), "an ordinary failure does not touch the crash marker");
        loadReady();
        assertEquals(2, backends.size());
    }

    @Test
    void suspectedCrashNeedsExplicitRetryWhichResetsTheGuard() throws Exception {
        nextLoadResult = BackendStatus.failed(BackendStatus.FailureReason.CRASH_SUSPECTED, "marker found");
        create(TEST_MIC);
        controller.onPushToTalkPressed();
        await(() -> controller.state() == VoiceState.FAILED);
        assertEquals(VoiceUi.Notice.CRASH_SUSPECTED, ui.lastNotice());
        assertTrue(ui.lastDetail().contains("/totalityvoice retry"));
        assertEquals(0, crashRecoveries.get());
        nextLoadResult = BackendStatus.ready("scripted");
        assertTrue(controller.retry());
        assertEquals(1, crashRecoveries.get());
        assertEquals(VoiceUi.Notice.RESET, ui.lastNotice());
        assertFalse(controller.retry(), "nothing to retry once reset");
    }

    @Test
    void unavailableDeviceIsReported() throws Exception {
        create(TEST_MIC);
        loadReady();
        provider.failOpen = true;
        controller.onPushToTalkPressed();
        await(() -> ui.results() == 1);
        assertEquals(VoiceUi.Notice.DEVICE_UNAVAILABLE, ui.lastNotice());
        assertTrue(ui.lastDetail().contains("refused"));
        assertEquals(VoiceState.READY, controller.state());
        assertEquals(0, backends.getFirst().opened.get(), "no session without a microphone");
    }

    @Test
    void chosenDeviceThatDisappearedFallsBackToDefault() throws Exception {
        create(TEST_MIC);
        settings.setDevice("USB Mic That Was Unplugged");
        loadReady();
        speak();
        assertTrue(ui.notices.contains(VoiceUi.Notice.DEVICE_FALLBACK));
        assertEquals("Headset Mic", provider.streams.getFirst().deviceName());
        assertEquals(1, ui.transcripts.size());
    }

    @Test
    void chosenPresentDeviceIsUsed() throws Exception {
        create(TEST_MIC);
        settings.setDevice("Webcam Mic");
        loadReady();
        speak();
        assertEquals("Webcam Mic", provider.streams.getFirst().deviceName());
        assertFalse(ui.notices.contains(VoiceUi.Notice.DEVICE_FALLBACK));
    }

    @Test
    void digitalSilenceIsNoSignalNotAnEmptyTranscript() throws Exception {
        provider.sampleValue = 0;
        create(TEST_MIC);
        loadReady();
        speak();
        assertEquals(VoiceUi.Notice.NO_SIGNAL, ui.lastNotice());
        assertTrue(ui.lastDetail().contains("muted"));
        assertTrue(ui.transcripts.isEmpty());
    }

    @Test
    void quietAndShortCapturesAreExplained() throws Exception {
        provider.sampleValue = 40;
        create(TEST_MIC);
        loadReady();
        speak();
        assertEquals(VoiceUi.Notice.TOO_QUIET, ui.lastNotice());

        provider.sampleValue = 5_000;
        provider.samplesPerStream = 1_600; // 0.1 s
        speak();
        assertEquals(VoiceUi.Notice.TOO_SHORT, ui.lastNotice());
        assertTrue(ui.transcripts.isEmpty());
    }

    @Test
    void emptyTranscriptionIsNotUnderstood() throws Exception {
        scriptedText = "";
        create(TEST_MIC);
        loadReady();
        speak();
        assertEquals(VoiceUi.Notice.NOT_UNDERSTOOD, ui.lastNotice());
        assertTrue(ui.transcripts.isEmpty(), "never an empty chat line");
        assertNull(controller.latestTranscript());
    }

    @Test
    void maximumDurationFinalizesWhatWasSaidAndSaysSo() throws Exception {
        provider.samplesPerStream = Integer.MAX_VALUE;
        create(new MicrophoneManager.Config(1_600, 8_000, 10_000, 2, 0));
        loadReady();
        controller.onPushToTalkPressed();
        await(() -> ui.results() == 1);       // ended by itself while the key is still held
        assertTrue(ui.notices.contains(VoiceUi.Notice.MAX_DURATION));
        assertEquals(1, ui.transcripts.size());
        assertEquals(VoiceState.READY, controller.state());
        controller.onPushToTalkReleased();
        assertEquals(VoiceState.READY, controller.state());
    }

    @Test
    void sessionFailureStopsTheMicrophoneAndIsReported() throws Exception {
        provider.samplesPerStream = Integer.MAX_VALUE;
        create(new MicrophoneManager.Config(1_600, Integer.MAX_VALUE, 10_000, 2, 0));
        loadReady();
        backends.getFirst().failOpen = true;
        controller.onPushToTalkPressed();
        await(() -> !mic.isCapturing() && provider.closes.get() == 1 && ui.results() == 1);
        assertEquals(VoiceUi.Notice.BACKEND_FAILED, ui.lastNotice());
        assertTrue(ui.lastDetail().contains("scripted session failure"));
        assertTrue(ui.transcripts.isEmpty());
        assertEquals(VoiceState.READY, controller.state());
    }

    @Test
    void disabledNeverOpensTheMicrophoneOrLoads() throws Exception {
        create(TEST_MIC);
        controller.setEnabled(false);
        controller.onPushToTalkPressed();
        assertEquals(VoiceState.DISABLED, controller.state());
        assertEquals(VoiceUi.Notice.DISABLED, ui.lastNotice());
        assertTrue(backends.isEmpty());
        assertEquals(0, provider.opens.get());
    }

    @Test
    void disablingWhileLoadingIgnoresTheLateLoadAndReleasesTheEngine() throws Exception {
        create(TEST_MIC);
        CompletableFuture<BackendStatus> gate = new CompletableFuture<>();
        ScriptedBackend.nextLoadGate = gate;
        controller.onPushToTalkPressed();
        assertEquals(VoiceState.LOADING, controller.state());
        controller.setEnabled(false);
        gate.complete(BackendStatus.ready("late"));
        Thread.sleep(20);
        drain();
        assertEquals(VoiceState.DISABLED, controller.state(), "a late load completion must not revive voice");
        assertTrue(backends.getFirst().backendClosed);
        controller.setEnabled(true);
        assertEquals(VoiceState.UNLOADED, controller.state());
    }

    @Test
    void debugWavIsOnlyWrittenWhenExplicitlyEnabled() throws Exception {
        create(TEST_MIC);
        loadReady();
        speak();
        assertFalse(Files.exists(tmp.resolve("debug")), "no audio is kept by default");

        settings.setSaveDebugWav(true);
        speak();
        assertFalse(Files.exists(tmp.resolve("debug")), "saving WAVs also requires debug mode");

        settings.setDebug(true);
        speak();
        List<Path> wavs;
        try (var files = Files.list(tmp.resolve("debug"))) {
            wavs = files.toList();
        }
        assertEquals(1, wavs.size());
        assertEquals(16_000, zcylas.totality.api.voice.audio.WavPcmReader.read(wavs.getFirst()).samples().length);
    }

    @Test
    void classificationOrder() {
        CaptureResult ok = new CaptureResult(EndReason.STOPPED, "mic", 16_000, 5_000, null);
        Transcript words = new Transcript("hello", RecognitionMode.FREEFORM, List.of(), false);
        assertEquals(VoiceInputController.Outcome.SUCCESS, VoiceInputController.classify(ok, words, null));
        assertEquals(VoiceInputController.Outcome.BACKEND_ERROR, VoiceInputController.classify(ok, words, "boom"));
        assertEquals(VoiceInputController.Outcome.DEVICE_UNAVAILABLE, VoiceInputController.classify(
                new CaptureResult(EndReason.OPEN_FAILED, "mic", 0, 0, "x"), null, null));
        assertEquals(VoiceInputController.Outcome.DEVICE_UNAVAILABLE, VoiceInputController.classify(
                new CaptureResult(EndReason.DEVICE_LOST, "mic", 8_000, 5_000, "x"), words, null));
        assertEquals(VoiceInputController.Outcome.NO_SIGNAL, VoiceInputController.classify(
                new CaptureResult(EndReason.NO_DATA, "mic", 0, 0, "x"), null, null));
        assertEquals(VoiceInputController.Outcome.TOO_SHORT, VoiceInputController.classify(
                new CaptureResult(EndReason.STOPPED, "mic", 1_000, 5_000, null), words, null));
        assertEquals(VoiceInputController.Outcome.NO_SIGNAL, VoiceInputController.classify(
                new CaptureResult(EndReason.STOPPED, "mic", 16_000, 0, null), words, null));
        assertEquals(VoiceInputController.Outcome.TOO_QUIET, VoiceInputController.classify(
                new CaptureResult(EndReason.STOPPED, "mic", 16_000, 50, null), words, null));
        assertEquals(VoiceInputController.Outcome.NOT_UNDERSTOOD, VoiceInputController.classify(ok,
                new Transcript("", RecognitionMode.FREEFORM, List.of(), true), null));
        assertEquals(VoiceInputController.Outcome.SUCCESS, VoiceInputController.classify(
                new CaptureResult(EndReason.MAX_DURATION, "mic", 16_000, 5_000, null), words, null));
    }

    @Test
    void displayTextOnlyFormatsAndLimits() {
        assertEquals("Hello this is a test", VoiceInputController.displayText("hello this is a test"));
        assertEquals("Tp home", VoiceInputController.displayText("/tp home"), "never starts with a slash");
        assertEquals("", VoiceInputController.displayText(" / "));
        assertEquals("A b", VoiceInputController.displayText("a\tb\n"));
        assertEquals(256, VoiceInputController.displayText("x".repeat(400)).length());
    }

    // ── fakes ──────────────────────────────────────────────────────────────────

    final class RecordingUi implements VoiceUi {
        final List<Notice> notices = new ArrayList<>();
        final List<String> details = new ArrayList<>();
        final List<String> transcripts = new ArrayList<>();
        final List<String> logs = new ArrayList<>();
        final List<String> debugs = new ArrayList<>();
        final List<Notice> modelInits = new ArrayList<>();
        final List<VoiceFeedback> feedback = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void feedback(VoiceFeedback f) {
            feedback.add(f);
        }

        VoiceFeedback.Kind lastFeedback() {
            return feedback.isEmpty() ? null : feedback.getLast().kind();
        }

        @Override
        public void notice(Notice notice, String detail) {
            notices.add(notice);
            details.add(detail);
        }

        @Override
        public void modelInitialization(Notice notice, String detail) {
            modelInits.add(notice);
            VoiceUi.super.modelInitialization(notice, detail);
        }

        @Override
        public void transcript(String text) {
            transcripts.add(text);
        }

        @Override
        public void log(String line) {
            logs.add(line);
        }

        @Override
        public void debug(String line) {
            debugs.add(line);
        }

        Notice lastNotice() {
            return notices.getLast();
        }

        String lastDetail() {
            return details.getLast();
        }

        /** Number of utterance outcomes shown (a transcript or an outcome notice). */
        int results() {
            int n = transcripts.size();
            for (Notice x : notices) {
                switch (x) {
                    case NOT_UNDERSTOOD, TOO_SHORT, NO_SIGNAL, TOO_QUIET, DEVICE_UNAVAILABLE, BACKEND_FAILED -> n++;
                    default -> { }
                }
            }
            return n;
        }
    }

    /** A backend whose load and sessions are scripted by the test. */
    final class ScriptedBackend implements RecognitionBackend {
        static volatile CompletableFuture<BackendStatus> nextLoadGate;
        final BackendStatus loadResult;
        final AtomicInteger opened = new AtomicInteger();
        final AtomicInteger finished = new AtomicInteger();
        final AtomicInteger finishing = new AtomicInteger();
        final AtomicInteger closed = new AtomicInteger();
        final AtomicInteger acceptedSamples = new AtomicInteger();
        volatile CountDownLatch finishGate;
        volatile boolean failOpen;
        volatile boolean backendClosed;
        volatile RecognitionMode lastMode;
        final List<RecognitionRequest> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
        private volatile BackendStatus status = BackendStatus.UNLOADED;

        ScriptedBackend(BackendStatus loadResult) {
            this.loadResult = loadResult;
        }

        @Override
        public String id() {
            return "scripted";
        }

        @Override
        public BackendStatus status() {
            return status;
        }

        @Override
        public CompletableFuture<BackendStatus> load() {
            CompletableFuture<BackendStatus> gate = nextLoadGate;
            nextLoadGate = null;
            status = BackendStatus.LOADING;
            CompletableFuture<BackendStatus> source = gate != null ? gate
                    : CompletableFuture.supplyAsync(() -> loadResult);
            return source.thenApply(s -> {
                status = backendClosed ? BackendStatus.CLOSED : s;
                return s;
            });
        }

        @Override
        public RecognitionSession openSession(RecognitionRequest request) {
            if (failOpen) throw new RecognitionException("scripted session failure");
            if (!status.isReady()) throw new RecognitionException("not ready");
            opened.incrementAndGet();
            lastMode = request.mode();
            requests.add(request);
            return new RecognitionSession() {
                boolean done;
                boolean isClosed;

                @Override
                public RecognitionRequest request() {
                    return request;
                }

                @Override
                public synchronized void acceptAudio(short[] samples, int count) {
                    if (done || isClosed) throw new RecognitionException("session not accepting audio");
                    acceptedSamples.addAndGet(count);
                }

                @Override
                public String partialText() {
                    return scriptedPartial;
                }

                @Override
                public Transcript finish() {
                    synchronized (this) {
                        if (done || isClosed) throw new RecognitionException("finish twice");
                        done = true;
                    }
                    finishing.incrementAndGet();
                    CountDownLatch gate = finishGate;
                    if (gate != null) {
                        try {
                            gate.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    finished.incrementAndGet();
                    boolean grammar = request.mode() == RecognitionMode.GRAMMAR;
                    String t = grammar ? scriptedGrammarText : scriptedText;
                    boolean unrecognized = t.isBlank() || (grammar && !request.phrases().contains(t));
                    return new Transcript(t, request.mode(), List.of(), unrecognized);
                }

                @Override
                public synchronized boolean isOpen() {
                    return !isClosed;
                }

                @Override
                public synchronized void close() {
                    if (isClosed) return;
                    isClosed = true;
                    closed.incrementAndGet();
                }
            };
        }

        @Override
        public void close() {
            backendClosed = true;
            status = BackendStatus.CLOSED;
        }
    }
}

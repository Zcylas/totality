package zcylas.totality.client.voice;

import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.voice.audio.PcmAudio;
import zcylas.totality.api.voice.command.VoiceCommandContext;
import zcylas.totality.api.voice.command.VoiceCommandContexts;
import zcylas.totality.api.voice.command.OperatorVoice;
import zcylas.totality.api.voice.operator.OperatorChannel;
import zcylas.totality.api.voice.command.VoiceCommandMatcher;
import zcylas.totality.api.voice.audio.WavPcmWriter;
import zcylas.totality.api.voice.capture.AudioLevel;
import zcylas.totality.api.voice.capture.CaptureException;
import zcylas.totality.api.voice.capture.MicrophoneManager;
import zcylas.totality.api.voice.capture.MicrophoneManager.CaptureResult;
import zcylas.totality.api.voice.capture.MicrophoneManager.EndReason;
import zcylas.totality.api.voice.recognition.BackendStatus;
import zcylas.totality.api.voice.recognition.RecognitionBackend;
import zcylas.totality.api.voice.recognition.RecognitionException;
import zcylas.totality.api.voice.recognition.RecognitionRequest;
import zcylas.totality.api.voice.recognition.RecognitionSession;
import zcylas.totality.api.voice.recognition.Transcript;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The push-to-talk state machine behind Voice Input (see {@link VoiceState}). Free of Minecraft
 * types so it can be driven by fakes in tests; {@code VoiceInputClient} connects it to keys, chat
 * and the HUD.
 *
 * <p><b>Threading.</b> Every public method runs on the client thread, and all fields below are
 * only touched there. Two things happen elsewhere and report back through {@code clientThread}:
 * model loading (tagged with a load generation) and each utterance (tagged with an utterance id).
 * A completion whose tag is no longer current — because the utterance was cancelled, the player
 * disconnected, voice was disabled, or the backend was reset — is dropped, so a stale result can
 * never appear later or attach to a new utterance.
 *
 * <p><b>One utterance.</b> Press → a capture starts; its worker opens a fresh FREEFORM session and
 * streams each 100 ms chunk into it while the key is held. Release → capture stops after a short
 * tail, the worker finalizes the session exactly once, closes it, and posts the outcome. The
 * session never outlives its utterance and no audio is kept afterwards (except an opt-in debug WAV).
 *
 * <p><b>Two modes, fixed at the press</b> ({@link PushToTalkMode}):
 * <ul>
 *   <li><b>COMMAND</b> (push-to-talk alone): only if an owning system offers voice commands
 *       ({@link VoiceCommandContexts}). The audio streams into a FREEFORM and a GRAMMAR session;
 *       {@link VoiceCommandMatcher} must accept it from BOTH results and the context — re-checked for
 *       staleness — executes at most one action. It never becomes a transcript. With no context the
 *       press only explains that no command is available: no microphone, no dictation.</li>
 *   <li><b>DICTATION</b> (modifier + push-to-talk): ordinary transcription, exactly as before, even
 *       while a command context exists.</li>
 * </ul>
 *
 * <p><b>Operator Mode</b> (COMMAND mode, only when {@link OperatorChannel#mayOffer()}): the same hold
 * also listens for "operator mode &lt;command&gt;" ({@link OperatorVoice}). While audio streams, the
 * partial results may switch the HUD to Operator Mode and ask the server for authorization — the
 * microphone keeps running, nothing executes. On release, an utterance that uses Operator Mode
 * vocabulary belongs to Operator Mode alone (it never touches the hologram); only a strict, complete
 * phrase is sent — one typed, whitelisted action — and only the SERVER's answer is shown as a result.
 * Anything else follows the hologram rules above, unchanged.
 */
public final class VoiceInputController {

    /** Utterances shorter than this are treated as an accidental tap. */
    public static final double MIN_UTTERANCE_SECONDS = 0.3;
    /** Peak below which a capture counts as "too quiet" (≈ -50 dBFS). */
    public static final int QUIET_PEAK = 104;
    /** Longest transcript shown or inserted (vanilla's chat input limit). */
    public static final int MAX_TRANSCRIPT_CHARS = 256;

    /** How a push-to-talk utterance is interpreted; chosen once, when the key goes down. */
    public enum PushToTalkMode { COMMAND, DICTATION }

    /**
     * Operator Mode within the current COMMAND utterance (presentation only — it never authorizes
     * anything): NONE until the activation phrase is heard mid-hold, REQUESTING while the server is
     * asked, then AUTHORIZED or DENIED as the SERVER answered.
     */
    public enum OperatorPhase { NONE, REQUESTING, AUTHORIZED, DENIED }

    public enum Outcome {
        SUCCESS, NOT_UNDERSTOOD, TOO_SHORT, NO_SIGNAL, TOO_QUIET, DEVICE_UNAVAILABLE, BACKEND_ERROR
    }

    /** Diagnostics of the most recent completed utterance (no transcript text). */
    public record UtteranceStats(int id, Outcome outcome, EndReason endReason, @Nullable String device,
                                 double audioSeconds, double peakDbfs, long finishMillis,
                                 long releaseToResultMillis, int words) {}

    private final Supplier<RecognitionBackend> backendFactory;
    private final Runnable crashRecovery;
    private final MicrophoneManager mic;
    private final Executor clientThread;
    private final VoiceUi ui;
    private final VoiceSettings settings;
    private final LongSupplier nanoClock;
    @Nullable private final Path debugDir;
    private final Supplier<VoiceCommandContext> commandContexts;
    /** Development diagnostics of command attempts; null (the default) records nothing. */
    @Nullable private volatile CommandAttemptListener commandDiagnostics;
    private OperatorChannel operatorChannel = OperatorChannel.NONE;

    private VoiceState state;
    @Nullable private RecognitionBackend backend;
    private int loadGeneration;
    private long loadStartNanos;
    private long lastLoadMillis = -1;
    @Nullable private BackendStatus lastFailure;

    private int utteranceId;
    @Nullable private MicrophoneManager.Capture capture;
    private long listenStartNanos;
    private long releaseNanos;
    @Nullable private String latestTranscript;
    @Nullable private UtteranceStats lastStats;
    private boolean fallbackExplained;
    /** The command context of the current utterance, or null for dictation. */
    @Nullable private VoiceCommandContext commandContext;
    private PushToTalkMode utteranceMode = PushToTalkMode.DICTATION;
    private OperatorPhase operatorPhase = OperatorPhase.NONE;
    /** The current COMMAND utterance also listens for Operator Mode. */
    private boolean operatorOffered;
    /** Utterance id of the Operator Mode request awaiting the server's answer, or -1. */
    private int pendingOperatorId = -1;
    private boolean commandUtterance;

    public VoiceInputController(Supplier<RecognitionBackend> backendFactory, Runnable crashRecovery,
                                MicrophoneManager mic, Executor clientThread, VoiceUi ui, VoiceSettings settings,
                                LongSupplier nanoClock, @Nullable Path debugDir) {
        this(backendFactory, crashRecovery, mic, clientThread, ui, settings, nanoClock, debugDir, VoiceCommandContexts::current);
    }

    public VoiceInputController(Supplier<RecognitionBackend> backendFactory, Runnable crashRecovery,
                                MicrophoneManager mic, Executor clientThread, VoiceUi ui, VoiceSettings settings,
                                LongSupplier nanoClock, @Nullable Path debugDir, Supplier<VoiceCommandContext> commandContexts) {
        this.commandContexts = commandContexts;
        this.backendFactory = backendFactory;
        this.crashRecovery = crashRecovery;
        this.mic = mic;
        this.clientThread = clientThread;
        this.ui = ui;
        this.settings = settings;
        this.nanoClock = nanoClock;
        this.debugDir = debugDir;
        this.state = settings.enabled() ? VoiceState.UNLOADED : VoiceState.DISABLED;
    }

    /**
     * One contextual voice attempt, for explicitly enabled development diagnostics only. Contains
     * recognized text; never audio.
     */
    public record CommandAttempt(int utterance, boolean offered, @Nullable String notOfferedBecause,
                                 java.util.Set<String> phrases, @Nullable Outcome outcome, double audioSeconds,
                                 double peakDbfs, @Nullable Transcript grammar, @Nullable Transcript freeform,
                                 @Nullable VoiceCommandMatcher.Decision decision, @Nullable Boolean currentAtExecution,
                                 String result) {}

    @FunctionalInterface
    public interface CommandAttemptListener {
        void attempt(CommandAttempt attempt);
    }

    /** Connects Operator Mode to the server (the default offers nothing). Client thread. */
    public void setOperatorChannel(OperatorChannel channel) {
        operatorChannel = channel == null ? OperatorChannel.NONE : channel;
    }

    /** Enables (non-null) or disables development diagnostics of command attempts. Client thread. */
    public void setCommandDiagnostics(@Nullable CommandAttemptListener listener) {
        commandDiagnostics = listener;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public VoiceState state() {
        return state;
    }

    @Nullable
    public String latestTranscript() {
        return latestTranscript;
    }

    @Nullable
    public UtteranceStats lastStats() {
        return lastStats;
    }

    public long lastLoadMillis() {
        return lastLoadMillis;
    }

    @Nullable
    public BackendStatus lastFailure() {
        return lastFailure;
    }

    /** Seconds since listening started (0 unless LISTENING). */
    public double listeningSeconds() {
        return state == VoiceState.LISTENING ? (nanoClock.getAsLong() - listenStartNanos) / 1e9 : 0;
    }

    public MicrophoneManager microphone() {
        return mic;
    }

    // ── Push-to-talk ──────────────────────────────────────────────────────────

    /** The mode of the current (or last) utterance. */
    public PushToTalkMode utteranceMode() {
        return utteranceMode;
    }

    /** Operator Mode phase of the current (or last) COMMAND utterance. */
    public OperatorPhase operatorPhase() {
        return operatorPhase;
    }

    /** A fresh DICTATION press (also what loads the model on first use). */
    public void onPushToTalkPressed() {
        onPushToTalkPressed(PushToTalkMode.DICTATION);
    }

    /** A fresh press of push-to-talk during ordinary gameplay; {@code mode} is fixed for this utterance. */
    public void onPushToTalkPressed(PushToTalkMode mode) {
        switch (state) {
            case DISABLED -> ui.notice(VoiceUi.Notice.DISABLED, "Voice Input is disabled (/totalityvoice enable).");
            case UNLOADED -> startLoading();
            case LOADING -> ui.notice(VoiceUi.Notice.LOADING, "Voice Input is still loading…");
            case FAILED -> explainFailure();
            case RECOGNIZING -> ui.notice(VoiceUi.Notice.BUSY, "Still processing the previous phrase.");
            case LISTENING -> { /* already listening; edges are handled by the caller */ }
            case READY -> beginUtterance(mode);
        }
    }

    /** The push-to-talk key was released. */
    public void onPushToTalkReleased() {
        if (state != VoiceState.LISTENING) return;
        releaseNanos = nanoClock.getAsLong();
        state = VoiceState.RECOGNIZING;
        if (capture != null) capture.stop();
        ui.log("utterance #" + utteranceId + ": push-to-talk released after "
                + String.format("%.2f", (releaseNanos - listenStartNanos) / 1e9) + " s");
    }

    /**
     * Abandons the current utterance, if any, without showing a result. Its audio is discarded and
     * any late completion is ignored.
     *
     * @param why shown to the player when non-null (e.g. "a screen was opened")
     */
    public void cancelUtterance(@Nullable String why) {
        if (state != VoiceState.LISTENING && state != VoiceState.RECOGNIZING) return;
        int cancelled = utteranceId;
        utteranceId++;
        if (capture != null) capture.cancel();
        capture = null;
        state = VoiceState.READY;
        VoiceCommandContext context = commandContext;
        commandContext = null;
        operatorPhase = OperatorPhase.NONE;
        if (context != null) context.rejected(VoiceCommandMatcher.Rejection.CANCELLED);
        ui.log("utterance #" + cancelled + " cancelled" + (why == null ? "" : ": " + why));
        if (why != null) ui.notice(VoiceUi.Notice.CANCELLED, "Voice input cancelled: " + why + ".");
        ui.feedback(VoiceFeedback.of(VoiceFeedback.Kind.CANCELLED, utteranceMode, why == null ? "" : why));
    }

    /** Leaving a world or server: cancel silently and forget the last transcript. */
    public void onDisconnect() {
        cancelUtterance(null);
        latestTranscript = null;
    }

    // ── Settings, recovery, shutdown ──────────────────────────────────────────

    public void setEnabled(boolean enabled) {
        if (enabled) {
            if (state == VoiceState.DISABLED) state = VoiceState.UNLOADED;
            return;
        }
        cancelUtterance(null);
        unloadBackend();
        state = VoiceState.DISABLED;
    }

    /**
     * Explicit player action after a failure. After a suspected native crash this also clears the
     * crash marker and forces the model to be re-extracted and re-verified; the next push-to-talk
     * press loads again. Never retries by itself.
     *
     * @return false if there was nothing to retry
     */
    public boolean retry() {
        if (state != VoiceState.FAILED) return false;
        boolean crash = lastFailure != null && lastFailure.reason() == BackendStatus.FailureReason.CRASH_SUSPECTED;
        unloadBackend();
        if (crash) crashRecovery.run();
        lastFailure = null;
        state = VoiceState.UNLOADED;
        ui.notice(VoiceUi.Notice.RESET, crash
                ? "Voice Input reset. The model will be re-verified; press push-to-talk to load it again."
                : "Voice Input reset. Press push-to-talk to load it again.");
        return true;
    }

    /** Client shutdown: release the microphone and the engine. */
    public void shutdown() {
        cancelUtterance(null);
        mic.shutdown(2_000);
        unloadBackend();
        if (state != VoiceState.DISABLED) state = VoiceState.UNLOADED;
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    private void startLoading() {
        RecognitionBackend b = backendFactory.get();
        backend = b;
        state = VoiceState.LOADING;
        int generation = ++loadGeneration;
        loadStartNanos = nanoClock.getAsLong();
        ui.modelInitialization(VoiceUi.Notice.LOADING, "Loading Voice Input… press push-to-talk again when it is ready.");
        ui.log("loading speech backend '" + b.id() + "'");
        b.load().whenComplete((status, error) -> clientThread.execute(() -> onLoaded(generation, status, error)));
    }

    private void onLoaded(int generation, @Nullable BackendStatus status, @Nullable Throwable error) {
        if (generation != loadGeneration || state != VoiceState.LOADING) return; // superseded
        lastLoadMillis = (nanoClock.getAsLong() - loadStartNanos) / 1_000_000L;
        if (status == null) {
            status = BackendStatus.failed(BackendStatus.FailureReason.INTERNAL_ERROR, String.valueOf(error));
        }
        if (status.isReady()) {
            state = VoiceState.READY;
            ui.log("speech backend ready in " + lastLoadMillis + " ms (" + status.detail() + ")");
            ui.modelInitialization(VoiceUi.Notice.READY, "Voice Input ready — hold push-to-talk and speak.");
            return;
        }
        state = VoiceState.FAILED;
        lastFailure = status;
        ui.log("speech backend failed after " + lastLoadMillis + " ms: " + status);
        explainFailure(true);
    }

    private void explainFailure() {
        explainFailure(false);
    }

    /** @param initialization true only for the failure that ends a load, false when repeating it later */
    private void explainFailure(boolean initialization) {
        BackendStatus f = lastFailure;
        VoiceUi.Notice notice;
        String detail;
        if (f != null && f.reason() == BackendStatus.FailureReason.CRASH_SUSPECTED) {
            notice = VoiceUi.Notice.CRASH_SUSPECTED;
            detail = "Voice Input is off: the speech engine did not finish loading last "
                    + "time (possible crash). Use /totalityvoice retry to reset and try again.";
        } else {
            notice = VoiceUi.Notice.BACKEND_FAILED;
            detail = "Voice Input is unavailable: " + (f == null ? "unknown error" : f.detail()) + " (/totalityvoice retry)";
        }
        if (initialization) ui.modelInitialization(notice, detail);
        else ui.notice(notice, detail);
    }

    private void unloadBackend() {
        loadGeneration++;
        RecognitionBackend b = backend;
        backend = null;
        if (b != null) b.close();
    }

    // ── Utterances ────────────────────────────────────────────────────────────

    private void beginUtterance(PushToTalkMode mode) {
        RecognitionBackend b = backend;
        if (b == null || !b.status().isReady()) {
            state = VoiceState.UNLOADED;
            startLoading();
            return;
        }
        VoiceCommandContext context = mode == PushToTalkMode.COMMAND ? commandContexts.get() : null;
        boolean operator = mode == PushToTalkMode.COMMAND && operatorChannel.mayOffer();
        CommandAttemptListener diagnostics = commandDiagnostics;
        if (mode == PushToTalkMode.COMMAND && context == null && !operator) {
            // Command mode never falls back to dictation: nothing is recorded, nothing transcribed.
            if (diagnostics != null) {
                diagnostics.attempt(new CommandAttempt(utteranceId + 1, false, VoiceCommandContexts.describeAbsence(),
                        java.util.Set.of(), null, 0, 0, null, null, null, null, "COMMAND mode, nothing to command: no action, no dictation"));
            }
            ui.notice(VoiceUi.Notice.NO_COMMAND, "No voice command is available right now.");
            ui.feedback(VoiceFeedback.of(VoiceFeedback.Kind.NO_COMMAND, mode, ""));
            return;
        }
        String device = chooseDevice();
        int id = ++utteranceId;
        boolean command = mode == PushToTalkMode.COMMAND;
        RecognitionRequest grammar = null;
        if (command) {
            java.util.Set<String> phrases = new java.util.LinkedHashSet<>();
            if (context != null) phrases.addAll(context.phrases());
            if (operator) phrases.addAll(OperatorVoice.GRAMMAR_PHRASES);
            grammar = RecognitionRequest.grammar(phrases);
        }
        // A command's free-form pass reports ranked alternatives (diagnostics); dictation does not.
        RecognitionRequest freeform = command
                ? RecognitionRequest.freeformWithAlternatives(VoiceCommandMatcher.CORROBORATION_HYPOTHESES)
                : RecognitionRequest.freeform();
        if (mode == PushToTalkMode.DICTATION && diagnostics != null) {
            diagnostics.attempt(new CommandAttempt(id, false, "DICTATION mode (modifier held at the press)", java.util.Set.of(),
                    null, 0, 0, null, null, null, null, "dictation: not a command"));
        }
        Utterance utterance = new Utterance(id, b, settings.saveDebugWav(), freeform, grammar, operator);
        MicrophoneManager.Capture c = mic.start(device, utterance);
        if (c == null) {
            ui.notice(VoiceUi.Notice.BUSY, "The microphone is already in use (e.g. /totalityvoice test).");
            return;
        }
        utterance.capture = c;
        capture = c;
        commandContext = context;
        commandUtterance = command;
        operatorOffered = operator;
        operatorPhase = OperatorPhase.NONE;
        pendingOperatorId = -1;       // a late answer to an earlier request is now stale
        utteranceMode = mode;
        listenStartNanos = nanoClock.getAsLong();
        state = VoiceState.LISTENING;
        if (context != null) context.listening();
        ui.log("utterance #" + id + ": listening on " + (device == null ? "default device" : "'" + device + "'")
                + (context == null ? "" : " (command: " + context.phrases() + ")") + (operator ? " (+ Operator Mode)" : ""));
    }

    @Nullable
    private String chooseDevice() {
        String chosen = settings.device();
        if (chosen == null) return null;
        List<String> available;
        try {
            available = mic.devices();
        } catch (CaptureException e) {
            return chosen; // let the open attempt report the real problem
        }
        VoiceSettings.DeviceChoice choice = VoiceSettings.resolveDevice(chosen, available);
        if (choice.fellBack() && !fallbackExplained) {
            fallbackExplained = true;
            ui.notice(VoiceUi.Notice.DEVICE_FALLBACK, "Microphone '" + chosen + "' not found; using the default.");
        }
        return choice.device();
    }

    /** Runs on the capture worker thread; reports back via {@link #complete}. */
    private final class Utterance implements MicrophoneManager.CaptureListener {
        final int id;
        final RecognitionBackend backend;
        final boolean keepAudio;
        final RecognitionRequest freeformRequest;
        @Nullable final RecognitionRequest grammarRequest;
        final boolean watchActivation;
        boolean activationPosted;
        volatile MicrophoneManager.Capture capture;
        @Nullable RecognitionSession session;
        @Nullable RecognitionSession grammarSession;
        @Nullable String sessionError;
        short[] audio = new short[0];
        int audioLength;

        Utterance(int id, RecognitionBackend backend, boolean keepAudio, RecognitionRequest freeformRequest,
                  @Nullable RecognitionRequest grammarRequest, boolean watchActivation) {
            this.id = id;
            this.watchActivation = watchActivation;
            this.freeformRequest = freeformRequest;
            this.backend = backend;
            this.keepAudio = keepAudio;
            this.grammarRequest = grammarRequest;
        }

        @Override
        public void onStarted(String deviceName) {
            try {
                session = backend.openSession(freeformRequest);
                if (grammarRequest != null) grammarSession = backend.openSession(grammarRequest);
            } catch (RecognitionException e) {
                fail(e.getMessage());
            }
        }

        @Override
        public void onAudio(short[] chunk, int count) {
            if (sessionError != null) {
                // The failure may have happened before the capture handle was known; stop now.
                MicrophoneManager.Capture c = capture;
                if (c != null) c.cancel();
                return;
            }
            RecognitionSession s = session;
            RecognitionSession g = grammarSession;
            try {
                if (s != null) s.acceptAudio(chunk, count);
                if (g != null) g.acceptAudio(chunk, count);
                // Operator Mode interim activation: both partial results begin with the activation phrase.
                // Presentation only (HUD + authorization question); the microphone simply keeps running.
                if (watchActivation && !activationPosted && s != null && g != null
                        && OperatorVoice.activationHeard(s.partialText(), g.partialText())) {
                    activationPosted = true;
                    int utterance = id;
                    clientThread.execute(() -> onOperatorActivation(utterance));
                }
            } catch (RecognitionException e) {
                fail(e.getMessage());
            }
            if (keepAudio) {
                if (audioLength + count > audio.length) audio = Arrays.copyOf(audio, Math.max(audio.length * 2, audioLength + count));
                System.arraycopy(chunk, 0, audio, audioLength, count);
                audioLength += count;
            }
        }

        private void fail(String message) {
            sessionError = message == null ? "recognition failed" : message;
            MicrophoneManager.Capture c = capture;
            if (c != null) c.cancel();
        }

        @Override
        public void onEnded(CaptureResult result) {
            Transcript transcript = null;
            Transcript grammarTranscript = null;
            long finishMillis = -1;
            RecognitionSession s = session;
            RecognitionSession g = grammarSession;
            session = null;
            grammarSession = null;
            try {
                boolean finalize = result.reason() == EndReason.STOPPED || result.reason() == EndReason.MAX_DURATION;
                if (s != null && sessionError == null && finalize) {
                    long t0 = System.nanoTime();
                    transcript = s.finish();
                    if (g != null) grammarTranscript = g.finish();
                    finishMillis = (System.nanoTime() - t0) / 1_000_000L;
                }
            } catch (RuntimeException e) {
                sessionError = e.getMessage() == null ? e.toString() : e.getMessage();
            } finally {
                if (s != null) s.close();
                if (g != null) g.close();
            }
            if (keepAudio && audioLength > 0 && debugDir != null && result.reason() != EndReason.CANCELLED) {
                try {
                    String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                    WavPcmWriter.write(debugDir.resolve("utterance-" + stamp + "-" + id + ".wav"),
                            new PcmAudio(Arrays.copyOf(audio, audioLength), MicrophoneManager.SAMPLE_RATE));
                } catch (Exception ignored) {
                    // Debug aid only.
                }
            }
            audio = new short[0];
            Transcript t = transcript;
            Transcript gt = grammarTranscript;
            long f = finishMillis;
            String err = sessionError;
            clientThread.execute(() -> complete(id, result, t, gt, f, err));
        }
    }

    /** Client thread: apply an utterance's outcome, unless it is stale. */
    private void complete(int id, CaptureResult result, @Nullable Transcript transcript,
                          @Nullable Transcript grammarTranscript, long finishMillis, @Nullable String sessionError) {
        if (id != utteranceId || (state != VoiceState.LISTENING && state != VoiceState.RECOGNIZING)) {
            ui.log("utterance #" + id + " result discarded (stale)");
            return;
        }
        capture = null;
        state = VoiceState.READY;
        VoiceCommandContext context = commandContext;
        commandContext = null;
        long latency = releaseNanos == 0 ? -1 : (nanoClock.getAsLong() - releaseNanos) / 1_000_000L;
        releaseNanos = 0;
        Outcome outcome = classify(result, transcript, sessionError);
        String text = outcome == Outcome.SUCCESS ? displayText(transcript.text()) : null;
        if (text != null && text.isEmpty()) {
            outcome = Outcome.NOT_UNDERSTOOD; // never produce an empty chat line
            text = null;
        }
        int words = transcript == null ? 0 : transcript.text().isBlank() ? 0 : transcript.text().split(" ").length;
        lastStats = new UtteranceStats(id, outcome, result.reason(), result.deviceName(), result.seconds(),
                AudioLevel.toDbfs(result.peak()), finishMillis, latency, words);
        ui.log(String.format("utterance #%d: %s (%s), %.2f s audio, peak %.1f dBFS, finish %d ms, release→result %d ms, %d word(s), device '%s'",
                id, outcome, result.reason(), result.seconds(), AudioLevel.toDbfs(result.peak()), finishMillis,
                latency, words, result.deviceName()));
        if (commandUtterance) {
            completeCommand(id, context, outcome, result, transcript, grammarTranscript, sessionError);
            return;
        }
        if (transcript != null) ui.debug("utterance #" + id + " transcript: \"" + transcript.text() + "\"");

        if (result.reason() == EndReason.MAX_DURATION) {
            ui.notice(VoiceUi.Notice.MAX_DURATION, "Maximum phrase length reached ("
                    + (mic.config().maxSamples() / MicrophoneManager.SAMPLE_RATE) + " s); release push-to-talk.");
        }
        ui.feedback(dictationFeedback(outcome));
        switch (outcome) {
            case SUCCESS -> {
                latestTranscript = text;
                ui.transcript(text);
            }
            case NOT_UNDERSTOOD -> ui.notice(VoiceUi.Notice.NOT_UNDERSTOOD, "Speech not understood — try again.");
            case TOO_SHORT -> ui.notice(VoiceUi.Notice.TOO_SHORT, "Hold push-to-talk while you speak.");
            case NO_SIGNAL -> ui.notice(VoiceUi.Notice.NO_SIGNAL, result.reason() == EndReason.NO_DATA
                    ? "No microphone signal: the device delivered no audio."
                    : "No microphone signal (digital silence). Is the microphone muted or switched off?");
            case TOO_QUIET -> ui.notice(VoiceUi.Notice.TOO_QUIET, "Microphone level too low to recognize speech.");
            case DEVICE_UNAVAILABLE -> ui.notice(VoiceUi.Notice.DEVICE_UNAVAILABLE, "Microphone unavailable: "
                    + (result.error() == null ? result.reason() : result.error()));
            case BACKEND_ERROR -> ui.notice(VoiceUi.Notice.BACKEND_FAILED, "Speech recognition failed: " + sessionError);
        }
    }

    /**
     * A command utterance: never a transcript or chat line. Capture problems keep their usual notices;
     * recognized speech must pass {@link VoiceCommandMatcher}, and the context must still be current.
     */
    private void completeCommand(int id, @Nullable VoiceCommandContext context, Outcome outcome, CaptureResult result,
                                 @Nullable Transcript freeform, @Nullable Transcript grammar, @Nullable String sessionError) {
        CommandAttemptListener diagnostics = commandDiagnostics;
        java.util.Set<String> offered = new java.util.LinkedHashSet<>();
        if (context != null) offered.addAll(context.phrases());
        if (operatorOffered) offered.addAll(OperatorVoice.GRAMMAR_PHRASES);
        java.util.function.BiConsumer<VoiceCommandMatcher.Decision, Boolean> report = (decision, current) -> {
            if (diagnostics == null) return;
            String res = decision == null ? "rejected " + VoiceCommandMatcher.Rejection.NO_SPEECH + " (" + outcome + ")"
                    : !decision.accepted() ? "rejected " + decision.rejection()
                    : Boolean.TRUE.equals(current) ? "EXECUTED '" + decision.command() + "'" : "rejected STALE";
            diagnostics.attempt(new CommandAttempt(id, true, null, offered, outcome, result.seconds(),
                    AudioLevel.toDbfs(result.peak()), grammar, freeform, decision, current, res));
        };
        boolean operatorShown = operatorPhase != OperatorPhase.NONE;
        if (outcome != Outcome.SUCCESS && outcome != Outcome.NOT_UNDERSTOOD) {
            switch (outcome) {
                case TOO_SHORT -> ui.notice(VoiceUi.Notice.TOO_SHORT, "Hold push-to-talk while you speak.");
                case NO_SIGNAL -> ui.notice(VoiceUi.Notice.NO_SIGNAL, "No microphone signal.");
                case TOO_QUIET -> ui.notice(VoiceUi.Notice.TOO_QUIET, "Microphone level too low to recognize speech.");
                case DEVICE_UNAVAILABLE -> ui.notice(VoiceUi.Notice.DEVICE_UNAVAILABLE, "Microphone unavailable: "
                        + (result.error() == null ? result.reason() : result.error()));
                case BACKEND_ERROR -> ui.notice(VoiceUi.Notice.BACKEND_FAILED, "Speech recognition failed: " + sessionError);
                default -> { }
            }
            ui.log("utterance #" + id + ": command rejected (" + outcome + ")");
            ui.feedback(new VoiceFeedback(VoiceFeedback.Kind.FAILED, PushToTalkMode.COMMAND, operatorShown, shortReason(outcome)));
            report.accept(null, null);
            if (context != null) context.rejected(VoiceCommandMatcher.Rejection.NO_SPEECH);
            return;
        }
        if (operatorOffered) {
            OperatorVoice.Decision od = OperatorVoice.decide(grammar, freeform);
            if (od.involved()) {
                completeOperator(id, context, od, outcome, result, grammar, freeform, offered);
                return;
            }
        }
        if (context == null) {
            // Operator-only listening (no hologram) and nothing of Operator Mode was said: no action, no dictation.
            ui.log("utterance #" + id + ": nothing to command");
            ui.feedback(new VoiceFeedback(VoiceFeedback.Kind.NOT_UNDERSTOOD, PushToTalkMode.COMMAND, operatorShown, ""));
            if (diagnostics != null) {
                diagnostics.attempt(new CommandAttempt(id, true, null, offered, outcome, result.seconds(),
                        AudioLevel.toDbfs(result.peak()), grammar, freeform, null, null, "rejected: not an Operator Mode phrase"));
            }
            return;
        }
        VoiceCommandMatcher.Decision decision = VoiceCommandMatcher.decide(context.phrases(), context::commandOf, grammar, freeform);
        if (!decision.accepted()) {
            ui.log("utterance #" + id + ": command rejected (" + decision.rejection() + ")");
            ui.feedback(VoiceFeedback.of(VoiceFeedback.Kind.NOT_UNDERSTOOD, PushToTalkMode.COMMAND, ""));
            report.accept(decision, null);
            context.rejected(decision.rejection());
            return;
        }
        boolean current = context.isCurrent();
        if (!current || !context.execute(decision.command())) {
            ui.log("utterance #" + id + ": command '" + decision.command() + "' ignored (target no longer current)");
            ui.feedback(VoiceFeedback.of(VoiceFeedback.Kind.FAILED, PushToTalkMode.COMMAND, "No longer available"));
            report.accept(decision, false);
            context.rejected(VoiceCommandMatcher.Rejection.STALE);
            return;
        }
        ui.log("utterance #" + id + ": command '" + decision.command() + "' executed");
        ui.feedback(VoiceFeedback.of(VoiceFeedback.Kind.RECOGNIZED, PushToTalkMode.COMMAND, decision.command()));
        report.accept(decision, true);
        context.accepted(decision.command());
    }

    /**
     * An utterance that belongs to Operator Mode: never touches the hologram; at most ONE typed request,
     * and only for a strict complete phrase. The result shown later is the SERVER's.
     */
    private void completeOperator(int id, @Nullable VoiceCommandContext context, OperatorVoice.Decision od, Outcome outcome,
                                  CaptureResult result, @Nullable Transcript grammar, @Nullable Transcript freeform,
                                  java.util.Set<String> offered) {
        if (context != null) context.notAddressed();
        CommandAttemptListener diagnostics = commandDiagnostics;
        String trace = String.join(" | ", od.trace());
        if (!od.accepted()) {
            ui.log("utterance #" + id + ": Operator Mode rejected (" + od.rejection() + ")");
            ui.feedback(new VoiceFeedback(VoiceFeedback.Kind.NOT_UNDERSTOOD, PushToTalkMode.COMMAND, true, ""));
            if (diagnostics != null) {
                diagnostics.attempt(new CommandAttempt(id, true, null, offered, outcome, result.seconds(),
                        AudioLevel.toDbfs(result.peak()), grammar, freeform, null, null, "operator rejected " + od.rejection() + ": " + trace));
            }
            return;
        }
        pendingOperatorId = id;
        lastOperatorAction = od.action();
        ui.log("utterance #" + id + ": Operator Mode request " + od.action() + " sent to the server");
        operatorChannel.submit(id, od.action());
        ui.feedback(new VoiceFeedback(VoiceFeedback.Kind.AWAITING_SERVER, PushToTalkMode.COMMAND, true, od.action().spoken));
        if (diagnostics != null) {
            diagnostics.attempt(new CommandAttempt(id, true, null, offered, outcome, result.seconds(),
                    AudioLevel.toDbfs(result.peak()), grammar, freeform, null, null, "operator REQUESTED " + od.action() + ": " + trace));
        }
    }

    /** Client thread: the activation phrase was heard mid-hold (interim; presentation + authorization question). */
    void onOperatorActivation(int id) {
        if (id != utteranceId || state != VoiceState.LISTENING || operatorPhase != OperatorPhase.NONE) return;
        operatorPhase = OperatorPhase.REQUESTING;
        ui.log("utterance #" + id + ": Operator Mode activation phrase heard; asking the server");
        operatorChannel.requestAuthorization(id);
    }

    /** Client thread: the server's answer to {@link OperatorChannel#requestAuthorization}. Presentation only. */
    public void onOperatorAuthorization(int requestId, boolean authorized) {
        if (requestId != utteranceId || operatorPhase != OperatorPhase.REQUESTING) {
            ui.log("Operator Mode authorization #" + requestId + " ignored (stale)");
            return;
        }
        operatorPhase = authorized ? OperatorPhase.AUTHORIZED : OperatorPhase.DENIED;
        ui.log("utterance #" + requestId + ": server says Operator Mode is " + (authorized ? "authorized" : "NOT authorized"));
    }

    /** Client thread: the server's result for the request of utterance {@code requestId}. */
    public void onOperatorResult(int requestId, zcylas.totality.api.operator.OperatorResult result) {
        if (requestId != pendingOperatorId) {
            ui.log("Operator Mode result #" + requestId + " ignored (stale or unknown)");
            return;
        }
        pendingOperatorId = -1;
        ui.log("utterance #" + requestId + ": server answered " + result);
        VoiceFeedback.Kind kind = switch (result) {
            case EXECUTED, UNCHANGED -> VoiceFeedback.Kind.EXECUTED;
            case DENIED -> VoiceFeedback.Kind.DENIED;
            case REJECTED -> VoiceFeedback.Kind.FAILED;
        };
        String detail = switch (result) {
            case EXECUTED -> lastOperatorDetail();
            case UNCHANGED -> lastOperatorDetail() + " (already)";
            case DENIED -> "Operator permission required";
            case REJECTED -> "Request refused by the server";
        };
        ui.feedback(new VoiceFeedback(kind, PushToTalkMode.COMMAND, true, detail));
    }

    private String lastOperatorDetail() {
        return lastOperatorAction == null ? "" : lastOperatorAction.spoken;
    }

    @Nullable private zcylas.totality.api.operator.OperatorAction lastOperatorAction;

    /** Operator Mode request awaiting the server's answer (diagnostics/tests), or -1. */
    public int pendingOperatorRequest() {
        return pendingOperatorId;
    }

    private VoiceFeedback dictationFeedback(Outcome outcome) {
        return switch (outcome) {
            case SUCCESS -> VoiceFeedback.of(VoiceFeedback.Kind.TRANSCRIBED, PushToTalkMode.DICTATION, "");
            case NOT_UNDERSTOOD -> VoiceFeedback.of(VoiceFeedback.Kind.NOT_UNDERSTOOD, PushToTalkMode.DICTATION, "");
            default -> VoiceFeedback.of(VoiceFeedback.Kind.FAILED, PushToTalkMode.DICTATION, shortReason(outcome));
        };
    }

    /** A few words for the HUD; the full sentence still goes through {@link VoiceUi#notice}. */
    static String shortReason(Outcome outcome) {
        return switch (outcome) {
            case TOO_SHORT -> "Too short — hold while speaking";
            case NO_SIGNAL -> "No microphone signal";
            case TOO_QUIET -> "Too quiet";
            case DEVICE_UNAVAILABLE -> "Microphone unavailable";
            case BACKEND_ERROR -> "Recognition failed";
            case NOT_UNDERSTOOD -> "Not understood";
            case SUCCESS -> "";
        };
    }

    static Outcome classify(CaptureResult result, @Nullable Transcript transcript, @Nullable String sessionError) {
        if (sessionError != null) return Outcome.BACKEND_ERROR;
        switch (result.reason()) {
            case OPEN_FAILED, DEVICE_LOST -> {
                return Outcome.DEVICE_UNAVAILABLE;
            }
            case NO_DATA -> {
                return Outcome.NO_SIGNAL;
            }
            default -> { }
        }
        if (result.seconds() < MIN_UTTERANCE_SECONDS) return Outcome.TOO_SHORT;
        if (result.peak() == 0) return Outcome.NO_SIGNAL;
        if (result.peak() < QUIET_PEAK) return Outcome.TOO_QUIET;
        if (transcript == null || transcript.unrecognized() || transcript.text().isBlank()) return Outcome.NOT_UNDERSTOOD;
        return Outcome.SUCCESS;
    }

    /** Formatting and length safety only: capitalized first letter, no leading '/', max 256 chars. */
    static String displayText(String raw) {
        String t = raw.replaceAll("\\p{Cntrl}", " ").trim().replaceAll("\\s+", " ");
        while (t.startsWith("/")) t = t.substring(1).trim();
        if (t.length() > MAX_TRANSCRIPT_CHARS) t = t.substring(0, MAX_TRANSCRIPT_CHARS).trim();
        if (!t.isEmpty()) t = Character.toUpperCase(t.charAt(0)) + t.substring(1);
        return t;
    }
}

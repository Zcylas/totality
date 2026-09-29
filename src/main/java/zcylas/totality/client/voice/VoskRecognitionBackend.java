package zcylas.totality.client.voice;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import zcylas.totality.api.voice.model.ModelIntegrityException;
import zcylas.totality.api.voice.model.VoiceModelDescriptor;
import zcylas.totality.api.voice.model.VoiceModelInstaller;
import zcylas.totality.api.voice.recognition.BackendStatus;
import zcylas.totality.api.voice.recognition.BackendStatus.FailureReason;
import zcylas.totality.api.voice.recognition.ManagedRecognitionBackend;
import zcylas.totality.api.voice.recognition.NativeCrashGuard;
import zcylas.totality.api.voice.recognition.RecognitionException;
import zcylas.totality.api.voice.recognition.RecognitionMode;
import zcylas.totality.api.voice.recognition.RecognitionRequest;
import zcylas.totality.api.voice.recognition.RecognitionSession;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * Vosk 0.3.45 behind Totality's {@link zcylas.totality.api.voice.recognition.RecognitionBackend}.
 * Client-only; the only place in Totality that touches {@code org.vosk}.
 *
 * <p>Load order (all on the load executor, never the render thread):
 * <ol>
 *   <li>Unsupported OS/arch → FAILED before any native code is touched.</li>
 *   <li>A leftover {@link NativeCrashGuard} marker → FAILED(CRASH_SUSPECTED); the model install is
 *       invalidated so a retry re-extracts it from the verified bundled archive.</li>
 *   <li>Locate the model (normally: verified extraction of the bundled archive).</li>
 *   <li>Under the crash guard: load the native library (first touch of {@link LibVosk}; JNA
 *       extracts it from the jar) and open the model. Java-level failures clear the guard and
 *       become FAILED; only a native abort leaves it behind.</li>
 * </ol>
 *
 * <p>Each session gets a fresh {@link Recognizer}, constructed with its grammar; the model is
 * shared read-only between sessions (Vosk documents models as thread-shareable, recognizers not).
 */
public final class VoskRecognitionBackend extends ManagedRecognitionBackend {

    public static final String ID = "vosk";
    public static final String VOSK_VERSION = "0.3.45";
    public static final String BUNDLED_MODEL_ID = "vosk-model-small-en-us-0.15";
    private static final float SAMPLE_RATE = RecognitionSession.SAMPLE_RATE;

    /** Produces the model directory; may install it first. */
    @FunctionalInterface
    public interface ModelLocator {
        Path locate() throws Exception;
    }

    private final ModelLocator modelLocator;
    private final NativeCrashGuard crashGuard;
    private final Runnable onCrashSuspected;
    private volatile Model model;
    private volatile Path modelDirectory;

    public VoskRecognitionBackend(ModelLocator modelLocator, NativeCrashGuard crashGuard,
                                  Runnable onCrashSuspected, Executor loadExecutor) {
        super(loadExecutor);
        this.modelLocator = modelLocator;
        this.crashGuard = crashGuard;
        this.onCrashSuspected = onCrashSuspected;
    }

    /**
     * The standard configuration: the model bundled in the Totality jar, extracted under
     * {@code <voiceRoot>/models/}, with the crash marker at {@code <voiceRoot>/native-load.marker}.
     */
    public static VoskRecognitionBackend forBundledModel(Path voiceRoot, Executor loadExecutor,
                                                         InstallListener listener) {
        VoiceModelInstaller installer = new VoiceModelInstaller(voiceRoot.resolve("models"));
        NativeCrashGuard guard = new NativeCrashGuard(voiceRoot.resolve("native-load.marker"));
        ModelLocator locator = () -> {
            VoiceModelDescriptor descriptor = VoiceModelDescriptor.load(VoskRecognitionBackend.class, BUNDLED_MODEL_ID);
            long start = System.nanoTime();
            VoiceModelInstaller.InstalledModel installed = installer.install(descriptor,
                    () -> VoskRecognitionBackend.class.getResourceAsStream("/" + descriptor.archiveResource()));
            listener.installed(installed, (System.nanoTime() - start) / 1_000_000L);
            return installed.directory();
        };
        Runnable invalidate = () -> {
            try {
                installer.invalidate(VoiceModelDescriptor.load(VoskRecognitionBackend.class, BUNDLED_MODEL_ID));
            } catch (IOException ignored) {
                // The retry will re-verify the installation anyway.
            }
        };
        return new VoskRecognitionBackend(locator, guard, invalidate, loadExecutor);
    }

    /** Observes the model installation step (timing/diagnostics). */
    @FunctionalInterface
    public interface InstallListener {
        InstallListener NONE = (installed, millis) -> {};

        void installed(VoiceModelInstaller.InstalledModel installed, long millis);
    }

    @Override
    public String id() {
        return ID;
    }

    public Path modelDirectory() {
        return modelDirectory;
    }

    @Override
    protected BackendStatus doLoad() throws IOException {
        if (!VoskPlatform.isSupported()) {
            return BackendStatus.failed(FailureReason.UNSUPPORTED_PLATFORM,
                    "Vosk " + VOSK_VERSION + " natives are bundled for Linux x86-64 and Windows x86-64 only; this is "
                            + VoskPlatform.describe());
        }
        switch (crashGuard.previousAttempt()) {
            case SUSPECTED_CRASH -> {
                onCrashSuspected.run();
                return BackendStatus.failed(FailureReason.CRASH_SUSPECTED,
                        "A previous speech-engine load never finished (" + crashGuard.previousAttemptDescription()
                                + "). Voice recognition stays off until it is reset (/totalityvoice retry, or delete "
                                + crashGuard.marker() + ").");
            }
            // The game was closed normally while loading: not a crash. The model installation is
            // re-verified by the locator below before anything native runs again.
            case INTERRUPTED_BY_SHUTDOWN -> crashGuard.clear();
            case NONE -> { }
        }

        Path dir;
        try {
            dir = modelLocator.locate();
        } catch (ModelIntegrityException e) {
            return BackendStatus.failed(FailureReason.MODEL_INVALID, describe(e));
        } catch (Exception e) {
            return BackendStatus.failed(FailureReason.MODEL_UNAVAILABLE, describe(e));
        }

        crashGuard.begin("vosk " + VOSK_VERSION + " native load + model open: " + dir);
        try {
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS); // first touch: extracts and loads libvosk
            } catch (LinkageError e) {
                return BackendStatus.failed(FailureReason.NATIVE_LIBRARY_UNAVAILABLE, describe(e));
            }
            try {
                model = new Model(dir.toString());
            } catch (IOException e) {
                return BackendStatus.failed(FailureReason.MODEL_INVALID, "Vosk could not open " + dir + ": " + describe(e));
            }
        } finally {
            // Reached on every Java-level outcome; only a native abort leaves the marker behind.
            crashGuard.end();
        }
        modelDirectory = dir;
        return BackendStatus.ready("vosk " + VOSK_VERSION + ", model " + dir.getFileName());
    }

    @Override
    protected RecognitionSession doOpenSession(RecognitionRequest request) throws IOException {
        Model m = model;
        Recognizer recognizer = request.mode() == RecognitionMode.GRAMMAR
                ? new Recognizer(m, SAMPLE_RATE, VoskResultParser.grammarJson(request.phrases()))
                : new Recognizer(m, SAMPLE_RATE);
        // The 0.3.45 grammar constructor does not check for a NULL native handle (the free-form
        // one does); using a NULL recognizer would crash in native code, so check here.
        if (recognizer.getPointer() == null) {
            throw new RecognitionException("Vosk could not create a " + request.mode() + " recognizer");
        }
        recognizer.setWords(true);
        if (request.alternatives() > 0) recognizer.setMaxAlternatives(request.alternatives());
        return new VoskRecognitionSession(recognizer, request, this::sessionClosed);
    }

    @Override
    protected void onClosedWhileLoading() {
        crashGuard.markInterruptedByCleanShutdown();
    }

    /**
     * Player-requested recovery after a suspected crash: removes the crash marker (and nothing else)
     * and deletes the model's install record so the next load re-extracts and re-verifies the model
     * from the bundled archive. Never deletes model directories or other files.
     */
    public static void resetAfterSuspectedCrash(Path voiceRoot) throws IOException {
        new NativeCrashGuard(voiceRoot.resolve("native-load.marker")).clear();
        new VoiceModelInstaller(voiceRoot.resolve("models"))
                .invalidate(VoiceModelDescriptor.load(VoskRecognitionBackend.class, BUNDLED_MODEL_ID));
    }

    @Override
    protected void doRelease() {
        Model m = model;
        model = null;
        if (m != null) m.close();
    }
}

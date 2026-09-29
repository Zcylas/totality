package zcylas.totality.client.voice;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import zcylas.totality.Totality;
import zcylas.totality.api.voice.capture.MicrophoneManager;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client-side home of the Voice Input runtime. Deliberately inert at startup: registering it creates
 * the push-to-talk controller and hooks keys, HUD, commands and shutdown, but nothing is extracted,
 * no native library is loaded and no microphone is opened until the player first presses
 * push-to-talk — so a client that never uses voice pays nothing. Never referenced from common/server
 * code.
 */
public final class VoiceRuntime {

    private static ExecutorService loadExecutor;
    private static VoiceInputController controller;

    private VoiceRuntime() {}

    /** Called once from {@code TotalityClient}. */
    public static void registerLifecycle() {
        VoiceSettings settings = VoiceSettings.load(FabricLoader.getInstance().getConfigDir().resolve("totality-voice.properties"));
        VoiceInputController c = createController(settings);
        synchronized (VoiceRuntime.class) {
            controller = c;
        }
        // Registered first so the controller releases microphone and engine before the loader stops.
        VoiceInputClient.register(c, settings);
        // Operator Mode: the controller only talks to the engine-neutral channel; the sender is installed by
        // TotalityClient outside the voice packages. Answers come back to this controller.
        c.setOperatorChannel(zcylas.totality.api.voice.operator.OperatorChannels.CURRENT);
        zcylas.totality.api.voice.operator.OperatorChannels.listen(new zcylas.totality.api.voice.operator.OperatorChannel.Listener() {
            @Override
            public void authorization(int requestId, boolean authorized) {
                c.onOperatorAuthorization(requestId, authorized);
            }

            @Override
            public void result(int requestId, zcylas.totality.api.operator.OperatorResult result) {
                c.onOperatorResult(requestId, result);
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
        VoiceLiveVerification.registerIfRequested();
        VoiceHologramCapture.registerIfRequested(c);
        VoiceCommandDiagnostics.registerIfRequested(c);
    }

    /** The push-to-talk controller, or null before {@link #registerLifecycle()}. */
    public static synchronized VoiceInputController controller() {
        return controller;
    }

    /** {@code <gameDir>/totality/voice}: extracted models, crash marker, verification and debug output. */
    public static Path voiceRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("totality").resolve("voice");
    }

    /** Single daemon thread for model extraction and native loading (never the render thread). */
    static synchronized ExecutorService loadExecutor() {
        if (loadExecutor == null) {
            loadExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Totality-Voice-Loader");
                t.setDaemon(true);
                return t;
            });
        }
        return loadExecutor;
    }

    private static VoiceInputController createController(VoiceSettings settings) {
        Path root = voiceRoot();
        // Development capture run only: lets scripted scenes play a WAV instead of the microphone.
        MicrophoneManager mic = new MicrophoneManager(VoiceHologramCapture.wrapIfRequested(new OpenAlCaptureDeviceProvider()), r -> {
            Thread t = new Thread(r, "Totality-Voice-Capture");
            t.setDaemon(true);
            return t;
        }, MicrophoneManager.Config.DEFAULT);
        return new VoiceInputController(
                () -> VoskRecognitionBackend.forBundledModel(root, loadExecutor(), (installed, millis) ->
                        Totality.LOGGER.info("[Totality Voice] model {} in {} ms ({} files)",
                                installed.reused() ? "verified" : "extracted", millis, installed.fileCount())),
                () -> {
                    try {
                        VoskRecognitionBackend.resetAfterSuspectedCrash(root);
                    } catch (IOException e) {
                        Totality.LOGGER.warn("[Totality Voice] crash-guard reset failed", e);
                    }
                },
                mic,
                runnable -> Minecraft.getInstance().execute(runnable),
                new MinecraftVoiceUi(settings),
                settings,
                System::nanoTime,
                root.resolve("debug"));
    }

    private static synchronized void shutdown() {
        if (loadExecutor != null) {
            loadExecutor.shutdownNow();
            loadExecutor = null;
        }
    }
}

package zcylas.totality.client.voice;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import zcylas.totality.Totality;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Opt-in verification hook (not a gameplay feature), the live-microphone counterpart of
 * {@link VoiceWavVerification}: with {@code -Dtotality.voice.liveVerification=<seconds>} the started
 * client drives the real push-to-talk controller exactly as the key would — press (loads the
 * model), press again, hold for the given seconds on the real microphone, release — and writes the
 * outcome to {@code <gameDir>/totality/voice/verification/}. {@code ...liveVerification.exit=true}
 * closes the client afterwards. It proves the capture → recognition → display path in a real
 * client; it cannot make anyone speak. Without the property it does nothing.
 */
public final class VoiceLiveVerification {

    public static final String PROPERTY = "totality.voice.liveVerification";
    public static final String EXIT_PROPERTY = PROPERTY + ".exit";

    private static volatile boolean driving;

    private VoiceLiveVerification() {}

    /** True only while this opt-in hook is driving push-to-talk itself. */
    static boolean isDriving() {
        return driving;
    }

    static void registerIfRequested() {
        String seconds = System.getProperty(PROPERTY);
        if (seconds == null || seconds.isBlank()) return;
        double holdSeconds = Double.parseDouble(seconds.trim());
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            Thread t = new Thread(() -> run(client, holdSeconds), "Totality-Voice-LiveVerification");
            t.setDaemon(true);
            t.start();
        });
    }

    private static void run(Minecraft client, double holdSeconds) {
        StringBuilder report = new StringBuilder();
        driving = true;
        try {
            VoiceInputController c = VoiceRuntime.controller();
            report.append("state.initial=").append(onClient(client, c::state)).append('\n');
            try {
                report.append("devices=").append(c.microphone().devices()).append('\n');
                report.append("defaultDevice=").append(c.microphone().defaultDevice()).append('\n');
            } catch (Exception e) {
                report.append("devices.error=").append(e.getMessage()).append('\n');
            }
            long t0 = System.nanoTime();
            onClient(client, () -> { c.onPushToTalkPressed(); return null; });           // loads
            VoiceState s = waitFor(client, c, st -> st != VoiceState.LOADING && st != VoiceState.UNLOADED, 120);
            report.append("state.afterLoad=").append(s).append(" loadMillis=").append(onClient(client, c::lastLoadMillis))
                    .append(" (wall ").append((System.nanoTime() - t0) / 1_000_000L).append(" ms)\n");
            if (s != VoiceState.READY) {
                report.append("failure=").append(onClient(client, c::lastFailure)).append('\n');
            } else {
                onClient(client, () -> { c.onPushToTalkPressed(); return null; });       // listen
                report.append("state.pressed=").append(onClient(client, c::state)).append('\n');
                Thread.sleep((long) (holdSeconds * 1000));
                onClient(client, () -> { c.onPushToTalkReleased(); return null; });      // release
                waitFor(client, c, st -> st == VoiceState.READY, 30);
                VoiceInputController.UtteranceStats stats = onClient(client, c::lastStats);
                report.append("utterance=").append(stats).append('\n');
                report.append("transcript=").append(onClient(client, c::latestTranscript)).append('\n');
                report.append("microphoneStillCapturing=").append(c.microphone().isCapturing()).append('\n');
            }
        } catch (Exception e) {
            report.append("error=").append(e).append('\n');
        } finally {
            driving = false;
        }
        for (String line : report.toString().split("\n")) Totality.LOGGER.info("[Totality Voice][live] {}", line);
        try {
            Path dir = VoiceRuntime.voiceRoot().resolve("verification");
            Files.createDirectories(dir);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Files.writeString(dir.resolve("live-verification-" + stamp + ".txt"), report.toString());
        } catch (Exception e) {
            Totality.LOGGER.warn("[Totality Voice] could not write live verification report", e);
        }
        if (Boolean.getBoolean(EXIT_PROPERTY)) client.execute(client::stop);
    }

    private static <T> T onClient(Minecraft client, Supplier<T> action) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        client.execute(() -> {
            try {
                f.complete(action.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f.get(30, TimeUnit.SECONDS);
    }

    private static VoiceState waitFor(Minecraft client, VoiceInputController c,
                                      java.util.function.Predicate<VoiceState> done, int timeoutSeconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (true) {
            VoiceState s = onClient(client, c::state);
            if (done.test(s) || System.nanoTime() > deadline) return s;
            Thread.sleep(50);
        }
    }
}

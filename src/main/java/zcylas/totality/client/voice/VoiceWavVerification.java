package zcylas.totality.client.voice;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import zcylas.totality.Totality;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Opt-in verification hook (not a gameplay feature): with {@code -Dtotality.voice.wavVerification=<wav>}
 * the client, once started, decodes that recording through the bundled model on a worker thread and
 * logs/writes the measured result to {@code <gameDir>/totality/voice/verification/}. Adding
 * {@code -Dtotality.voice.wavVerification.exit=true} closes the client afterwards.
 *
 * <p>Works in production jars as well as in dev, because its purpose is to prove the packaged
 * natives and model load from the real distributable. Without the property it does nothing at all.
 */
public final class VoiceWavVerification {

    public static final String WAV_PROPERTY = "totality.voice.wavVerification";
    public static final String EXIT_PROPERTY = "totality.voice.wavVerification.exit";

    private static final AtomicLong CLIENT_TICKS = new AtomicLong();

    private VoiceWavVerification() {}

    public static void registerIfRequested() {
        String wav = System.getProperty(WAV_PROPERTY);
        if (wav == null || wav.isBlank()) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> CLIENT_TICKS.incrementAndGet());
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> start(client, Path.of(wav)));
    }

    private static void start(Minecraft client, Path wav) {
        Thread worker = new Thread(() -> {
            Totality.LOGGER.info("[Totality Voice] WAV verification starting: {}", wav);
            VoiceWavTranscriptionProbe.Result result = VoiceWavTranscriptionProbe.run(
                    VoiceRuntime.voiceRoot(), wav, VoiceRuntime.loadExecutor(), CLIENT_TICKS::get);
            String report = "wav=" + wav.toAbsolutePath() + "\n" + result.toReport();
            for (String line : report.split("\n")) Totality.LOGGER.info("[Totality Voice] {}", line);
            try {
                Path dir = VoiceRuntime.voiceRoot().resolve("verification");
                Files.createDirectories(dir);
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                Files.writeString(dir.resolve("wav-verification-" + stamp + ".txt"), report);
            } catch (Exception e) {
                Totality.LOGGER.warn("[Totality Voice] Could not write verification report", e);
            }
            if (Boolean.getBoolean(EXIT_PROPERTY)) client.execute(client::stop);
        }, "Totality-Voice-WavVerification");
        worker.setDaemon(true);
        worker.start();
    }
}

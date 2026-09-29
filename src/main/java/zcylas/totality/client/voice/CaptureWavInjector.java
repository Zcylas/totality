package zcylas.totality.client.voice;

import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.voice.capture.CaptureDeviceProvider;
import zcylas.totality.api.voice.capture.CaptureException;
import zcylas.totality.api.voice.capture.CaptureStream;

import java.util.List;

/**
 * Development capture run only ({@link VoiceHologramCapture}): wraps the real OpenAL provider so the
 * next capture plays an armed 16 kHz WAV as if it were the microphone, paced in real time and followed
 * by silence until push-to-talk is released. Every other capture goes to the real device unchanged.
 * Only installed when the opt-in capture run is requested; never in normal play.
 */
final class CaptureWavInjector implements CaptureDeviceProvider {

    static final String DEVICE_NAME = "Injected WAV (capture run)";

    private final CaptureDeviceProvider real;
    private volatile short @Nullable [] armed;

    CaptureWavInjector(CaptureDeviceProvider real) {
        this.real = real;
    }

    /** The next {@link #open} streams these samples instead of opening the microphone. */
    void arm(short[] samples) {
        armed = samples;
    }

    @Override
    public List<String> captureDevices() throws CaptureException {
        return real.captureDevices();
    }

    @Override
    public @Nullable String defaultCaptureDevice() throws CaptureException {
        return real.defaultCaptureDevice();
    }

    @Override
    public CaptureStream open(@Nullable String deviceName, int sampleRate) throws CaptureException {
        short[] samples = armed;
        armed = null;
        return samples == null ? real.open(deviceName, sampleRate) : new WavStream(samples, sampleRate);
    }

    private static final class WavStream implements CaptureStream {
        private final short[] samples;
        private final int sampleRate;
        private final long startNanos = System.nanoTime();
        private long delivered;

        WavStream(short[] samples, int sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }

        @Override
        public String deviceName() {
            return DEVICE_NAME;
        }

        @Override
        public int availableSamples() {
            long due = (System.nanoTime() - startNanos) * sampleRate / 1_000_000_000L;
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, due - delivered));
        }

        @Override
        public void read(short[] dst, int count) {
            for (int i = 0; i < count; i++) {
                long index = delivered + i;
                dst[i] = index < samples.length ? samples[(int) index] : 0;
            }
            delivered += count;
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public void close() {}
    }
}

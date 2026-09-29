package zcylas.totality.api.voice.capture;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scripted microphone for tests. Each opened stream offers {@link #samplesPerStream} samples of
 * constant value {@link #sampleValue} (in chunks as the worker asks), then nothing; optionally it
 * reports a disconnect once its audio is used up.
 */
public final class FakeCaptureDeviceProvider implements CaptureDeviceProvider {

    public volatile List<String> devices = List.of("Headset Mic", "Webcam Mic");
    public volatile String defaultDevice = "Headset Mic";
    public volatile int samplesPerStream = 16_000;
    public volatile short sampleValue = 5_000;
    public volatile boolean failOpen;
    public volatile boolean disconnectWhenDrained;
    public final AtomicInteger opens = new AtomicInteger();
    public final AtomicInteger closes = new AtomicInteger();
    public final List<FakeStream> streams = new ArrayList<>();

    @Override
    public List<String> captureDevices() {
        return devices;
    }

    @Override
    @Nullable
    public String defaultCaptureDevice() {
        return defaultDevice;
    }

    @Override
    public CaptureStream open(@Nullable String deviceName, int sampleRate) throws CaptureException {
        if (failOpen) throw new CaptureException(CaptureException.Kind.OPEN_FAILED, "fake device refused to open");
        opens.incrementAndGet();
        FakeStream s = new FakeStream(deviceName == null ? defaultDevice : deviceName);
        synchronized (streams) {
            streams.add(s);
        }
        return s;
    }

    public final class FakeStream implements CaptureStream {
        private final String name;
        private int remaining = samplesPerStream;
        public volatile boolean closed;
        public final AtomicInteger readsAfterClose = new AtomicInteger();

        FakeStream(String name) {
            this.name = name;
        }

        @Override
        public String deviceName() {
            return name;
        }

        @Override
        public synchronized int availableSamples() {
            if (closed) readsAfterClose.incrementAndGet();
            return Math.min(remaining, 1_600);
        }

        @Override
        public synchronized void read(short[] dst, int count) {
            if (closed) readsAfterClose.incrementAndGet();
            for (int i = 0; i < count; i++) dst[i] = (i % 2 == 0) ? sampleValue : (short) -sampleValue;
            remaining -= count;
        }

        @Override
        public synchronized boolean isConnected() {
            return !(disconnectWhenDrained && remaining == 0);
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            closes.incrementAndGet();
        }
    }
}

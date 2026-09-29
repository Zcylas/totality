package zcylas.totality.api.voice.capture;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs microphone captures: at most one at a time, each on its own capture worker thread, which
 * alone opens, reads and closes the device. That single owner is what rules out a device being
 * closed while another thread reads it, a double close, or a use after close: callers only ever set
 * {@link Capture#stop()}/{@link Capture#cancel()} flags, and the worker acts on them between reads.
 *
 * <p>Audio is delivered in chunks of {@link Config#chunkSamples()} (100 ms at 16 kHz) to the
 * {@link CaptureListener} on the worker thread; the chunk buffer is reused, so a listener must
 * consume it before returning. Nothing is retained after a capture ends.
 *
 * <p>A capture ends exactly once, with exactly one {@link CaptureListener#onEnded} call, for one of
 * the {@link EndReason}s. {@link Capture#stop()} drains what the device has already recorded (the
 * words spoken just before key release) before ending.
 */
public final class MicrophoneManager {

    public static final int SAMPLE_RATE = 16_000;

    /**
     * @param chunkSamples        samples per delivered chunk
     * @param maxSamples          hard limit on one capture (ends with {@link EndReason#MAX_DURATION})
     * @param noDataTimeoutMillis a device delivering nothing for this long ends with {@link EndReason#NO_DATA}
     * @param pollMillis          sleep between device polls when not enough audio is buffered
     * @param tailMillis          how long capture continues after {@link Capture#stop()}, so the end of
     *                            a word spoken as the key is released is not clipped
     */
    public record Config(int chunkSamples, int maxSamples, long noDataTimeoutMillis, long pollMillis, long tailMillis) {
        public static final Config DEFAULT = new Config(SAMPLE_RATE / 10, SAMPLE_RATE * 30, 1_500, 10, 250);
    }

    public enum EndReason {
        /** Released normally; everything recorded up to the stop was delivered. */
        STOPPED,
        /** Cancelled; remaining audio was discarded. */
        CANCELLED,
        /** The capture reached {@link Config#maxSamples()}. */
        MAX_DURATION,
        /** The device could not be opened. */
        OPEN_FAILED,
        /** The device was disconnected or a read failed. */
        DEVICE_LOST,
        /** The device stayed open but delivered no samples at all. */
        NO_DATA
    }

    /**
     * @param peak peak absolute sample over the whole capture (0 = digital silence)
     */
    public record CaptureResult(EndReason reason, @Nullable String deviceName, long samples, int peak,
                                @Nullable String error) {
        public double seconds() {
            return samples / (double) SAMPLE_RATE;
        }
    }

    /** Receives one capture's events, all on the capture worker thread. */
    public interface CaptureListener {
        /** The device is open and capturing. */
        default void onStarted(String deviceName) {}

        /** {@code chunk[0..count)} is valid only during this call. */
        void onAudio(short[] chunk, int count);

        /** Called exactly once, after the device has been closed. */
        void onEnded(CaptureResult result);
    }

    /** Handle for one running capture. Both methods are safe from any thread and idempotent. */
    public static final class Capture {
        private final java.util.concurrent.atomic.AtomicLong stopAtNanos = new java.util.concurrent.atomic.AtomicLong();
        private final AtomicBoolean cancel = new AtomicBoolean();
        private volatile Thread worker;

        /** Ends after the configured tail, delivering everything recorded up to then. */
        public void stop() {
            stopAtNanos.compareAndSet(0, System.nanoTime());
        }

        /** Ends as soon as possible, discarding remaining audio. */
        public void cancel() {
            cancel.set(true);
            Thread t = worker;
            if (t != null) t.interrupt();
        }
    }

    private final CaptureDeviceProvider provider;
    private final ThreadFactory threadFactory;
    private final Config config;
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicInteger level = new AtomicInteger();
    private volatile Capture current;
    private volatile Thread currentWorker;

    public MicrophoneManager(CaptureDeviceProvider provider, ThreadFactory threadFactory, Config config) {
        this.provider = provider;
        this.threadFactory = threadFactory;
        this.config = config;
    }

    public Config config() {
        return config;
    }

    public List<String> devices() throws CaptureException {
        return provider.captureDevices();
    }

    @Nullable
    public String defaultDevice() throws CaptureException {
        return provider.defaultCaptureDevice();
    }

    public boolean isCapturing() {
        return active.get();
    }

    /** Peak of the most recent chunk of the running capture (0 when idle). */
    public int currentPeak() {
        return active.get() ? level.get() : 0;
    }

    /**
     * Starts a capture on a new worker thread; the device is opened there, never on the caller's
     * thread. Returns null (and starts nothing) if a capture is already running.
     *
     * @param deviceName device to open, or null for the default
     */
    @Nullable
    public Capture start(@Nullable String deviceName, CaptureListener listener) {
        if (!active.compareAndSet(false, true)) return null;
        Capture capture = new Capture();
        level.set(0);
        current = capture;
        Thread worker = threadFactory.newThread(() -> run(capture, deviceName, listener));
        capture.worker = worker;
        currentWorker = worker;
        worker.start();
        return capture;
    }

    /** Cancels any running capture and waits (bounded) for its worker to release the device. */
    public void shutdown(long timeoutMillis) {
        Capture c = current;
        Thread t = currentWorker;
        if (c != null) c.cancel();
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(timeoutMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void run(Capture capture, @Nullable String deviceName, CaptureListener listener) {
        CaptureStream stream = null;
        EndReason reason;
        String error = null;
        String opened = deviceName;
        long samples = 0;
        int peak = 0;
        try {
            try {
                stream = provider.open(deviceName, SAMPLE_RATE);
                opened = stream.deviceName();
            } catch (CaptureException e) {
                reason = EndReason.OPEN_FAILED;
                error = e.getMessage();
                stream = null;
                throw new EndOfCapture(reason);
            }
            if (capture.cancel.get()) throw new EndOfCapture(EndReason.CANCELLED);
            listener.onStarted(opened);

            short[] chunk = new short[config.chunkSamples()];
            long lastData = System.nanoTime();
            while (true) {
                if (capture.cancel.get()) throw new EndOfCapture(EndReason.CANCELLED);
                long stopAt = capture.stopAtNanos.get();
                boolean stopping = stopAt != 0
                        && System.nanoTime() - stopAt >= TimeUnit.MILLISECONDS.toNanos(config.tailMillis());
                int available = stream.availableSamples();
                int remaining = (int) Math.min(Integer.MAX_VALUE, config.maxSamples() - samples);
                if (available >= config.chunkSamples() || (stopping && available > 0)) {
                    int n = Math.min(Math.min(available, config.chunkSamples()), remaining);
                    stream.read(chunk, n);
                    samples += n;
                    int chunkPeak = AudioLevel.peak(chunk, n);
                    peak = Math.max(peak, chunkPeak);
                    level.set(chunkPeak);
                    lastData = System.nanoTime();
                    listener.onAudio(chunk, n);
                    if (samples >= config.maxSamples()) throw new EndOfCapture(EndReason.MAX_DURATION);
                    continue;
                }
                if (stopping) throw new EndOfCapture(EndReason.STOPPED); // drained
                if (!stream.isConnected()) throw new EndOfCapture(EndReason.DEVICE_LOST, "Microphone disconnected");
                if (System.nanoTime() - lastData > TimeUnit.MILLISECONDS.toNanos(config.noDataTimeoutMillis())) {
                    throw new EndOfCapture(EndReason.NO_DATA, "The microphone delivered no audio");
                }
                try {
                    Thread.sleep(config.pollMillis());
                } catch (InterruptedException e) {
                    // cancel() interrupts; the loop re-checks the flag.
                }
            }
        } catch (EndOfCapture end) {
            reason = end.reason;
            if (end.getMessage() != null) error = end.getMessage();
        } catch (CaptureException e) {
            reason = EndReason.DEVICE_LOST;
            error = e.getMessage();
        } catch (RuntimeException e) {
            reason = EndReason.DEVICE_LOST;
            error = e.toString();
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (RuntimeException ignored) {
                    // Closing is best-effort; the handle is never touched again either way.
                }
            }
            level.set(0);
        }
        current = null;
        currentWorker = null;
        active.set(false);
        Thread.interrupted(); // clear a late cancel() interrupt before calling out
        listener.onEnded(new CaptureResult(reason, opened, samples, peak, error));
    }

    /** Internal control flow for ending a capture from inside the loop. */
    private static final class EndOfCapture extends Exception {
        final EndReason reason;

        EndOfCapture(EndReason reason) {
            this(reason, null);
        }

        EndOfCapture(EndReason reason, String message) {
            super(message, null, false, false);
            this.reason = reason;
        }
    }
}

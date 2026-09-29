package zcylas.totality.api.voice.capture;

import org.junit.jupiter.api.Test;
import zcylas.totality.api.voice.capture.MicrophoneManager.CaptureResult;
import zcylas.totality.api.voice.capture.MicrophoneManager.EndReason;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MicrophoneManagerTest {

    private static final MicrophoneManager.Config FAST =
            new MicrophoneManager.Config(1_600, 16_000 * 30, 10_000, 2, 0);

    private final FakeCaptureDeviceProvider provider = new FakeCaptureDeviceProvider();

    private MicrophoneManager manager(MicrophoneManager.Config config) {
        return new MicrophoneManager(provider, r -> new Thread(r, "test-capture"), config);
    }

    /** Records one capture. */
    static final class Recorder implements MicrophoneManager.CaptureListener {
        final CompletableFuture<CaptureResult> ended = new CompletableFuture<>();
        final List<String> threads = new CopyOnWriteArrayList<>();
        final AtomicInteger samples = new AtomicInteger();
        final AtomicInteger endedCalls = new AtomicInteger();
        volatile String started;

        @Override
        public void onStarted(String deviceName) {
            started = deviceName;
            threads.add(Thread.currentThread().getName());
        }

        @Override
        public void onAudio(short[] chunk, int count) {
            samples.addAndGet(count);
        }

        @Override
        public void onEnded(CaptureResult result) {
            endedCalls.incrementAndGet();
            ended.complete(result);
        }

        CaptureResult await() throws Exception {
            return ended.get(10, TimeUnit.SECONDS);
        }
    }

    private static void waitFor(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail("condition not reached");
            Thread.sleep(2);
        }
    }

    @Test
    void stopDeliversRecordedAudioThenClosesTheDeviceOnce() throws Exception {
        MicrophoneManager mic = manager(FAST);
        Recorder r = new Recorder();
        MicrophoneManager.Capture c = mic.start(null, r);
        assertNotNull(c);
        waitFor(() -> r.samples.get() == 16_000);
        c.stop();
        c.stop();
        CaptureResult result = r.await();
        assertEquals(EndReason.STOPPED, result.reason());
        assertEquals(16_000, result.samples());
        assertEquals(1.0, result.seconds(), 1e-9);
        assertEquals(5_000, result.peak());
        assertEquals("Headset Mic", result.deviceName());
        assertEquals("test-capture", r.threads.get(0), "the device is opened on the capture worker, not the caller");
        assertEquals(1, provider.closes.get());
        assertEquals(1, r.endedCalls.get());
        assertEquals(0, provider.streams.get(0).readsAfterClose.get());
        assertFalse(mic.isCapturing());
        assertEquals(0, mic.currentPeak());
    }

    @Test
    void onlyOneCaptureAtATime() throws Exception {
        MicrophoneManager mic = manager(FAST);
        Recorder first = new Recorder();
        MicrophoneManager.Capture c = mic.start(null, first);
        assertNotNull(c);
        assertNull(mic.start(null, new Recorder()), "a second concurrent capture must be refused");
        c.cancel();
        first.await();
        Recorder second = new Recorder();
        MicrophoneManager.Capture again = mic.start("Webcam Mic", second);
        assertNotNull(again, "the microphone is free again once the first capture ended");
        again.cancel();
        assertEquals("Webcam Mic", second.await().deviceName());
    }

    @Test
    void cancelDiscardsAndReleasesTheDevice() throws Exception {
        provider.samplesPerStream = Integer.MAX_VALUE;
        MicrophoneManager mic = manager(new MicrophoneManager.Config(1_600, Integer.MAX_VALUE, 10_000, 2, 0));
        Recorder r = new Recorder();
        MicrophoneManager.Capture c = mic.start(null, r);
        waitFor(() -> r.samples.get() > 0);
        c.cancel();
        assertEquals(EndReason.CANCELLED, r.await().reason());
        assertEquals(1, provider.closes.get());
        assertFalse(mic.isCapturing());
    }

    @Test
    void maximumDurationEndsTheCapture() throws Exception {
        provider.samplesPerStream = Integer.MAX_VALUE;
        MicrophoneManager mic = manager(new MicrophoneManager.Config(1_600, 4_800, 10_000, 2, 0));
        Recorder r = new Recorder();
        mic.start(null, r);
        CaptureResult result = r.await();
        assertEquals(EndReason.MAX_DURATION, result.reason());
        assertEquals(4_800, result.samples());
        assertEquals(1, provider.closes.get());
    }

    @Test
    void openFailureIsReportedAndNothingIsLeftOpen() throws Exception {
        provider.failOpen = true;
        MicrophoneManager mic = manager(FAST);
        Recorder r = new Recorder();
        mic.start("Missing Mic", r);
        CaptureResult result = r.await();
        assertEquals(EndReason.OPEN_FAILED, result.reason());
        assertTrue(result.error().contains("refused"));
        assertNull(r.started);
        assertFalse(mic.isCapturing());
    }

    @Test
    void deviceWithoutAudioEndsWithNoData() throws Exception {
        provider.samplesPerStream = 0;
        MicrophoneManager mic = manager(new MicrophoneManager.Config(1_600, 16_000, 50, 2, 0));
        Recorder r = new Recorder();
        mic.start(null, r);
        assertEquals(EndReason.NO_DATA, r.await().reason());
        assertEquals(1, provider.closes.get());
    }

    @Test
    void disconnectedDeviceEndsWithDeviceLost() throws Exception {
        provider.samplesPerStream = 3_200;
        provider.disconnectWhenDrained = true;
        MicrophoneManager mic = manager(FAST);
        Recorder r = new Recorder();
        mic.start(null, r);
        CaptureResult result = r.await();
        assertEquals(EndReason.DEVICE_LOST, result.reason());
        assertEquals(3_200, result.samples());
    }

    @Test
    void silenceHasZeroPeak() throws Exception {
        provider.sampleValue = 0;
        MicrophoneManager mic = manager(FAST);
        Recorder r = new Recorder();
        MicrophoneManager.Capture c = mic.start(null, r);
        waitFor(() -> r.samples.get() == 16_000);
        c.stop();
        assertEquals(0, r.await().peak());
    }

    @Test
    void stopWaitsForTheConfiguredTail() throws Exception {
        provider.samplesPerStream = 1_600;
        MicrophoneManager mic = manager(new MicrophoneManager.Config(1_600, 16_000, 10_000, 2, 150));
        Recorder r = new Recorder();
        MicrophoneManager.Capture c = mic.start(null, r);
        waitFor(() -> r.samples.get() == 1_600);
        long t0 = System.nanoTime();
        c.stop();
        r.await();
        assertTrue(System.nanoTime() - t0 >= TimeUnit.MILLISECONDS.toNanos(140), "capture continues for the tail");
    }

    @Test
    void shutdownCancelsAndWaitsForTheWorker() throws Exception {
        provider.samplesPerStream = Integer.MAX_VALUE;
        MicrophoneManager mic = manager(new MicrophoneManager.Config(1_600, Integer.MAX_VALUE, 10_000, 2, 0));
        Recorder r = new Recorder();
        mic.start(null, r);
        waitFor(() -> r.samples.get() > 0);
        mic.shutdown(5_000);
        assertTrue(r.ended.isDone(), "shutdown returns only after the device was released");
        assertEquals(EndReason.CANCELLED, r.await().reason());
        assertEquals(1, provider.closes.get());
    }

    @Test
    void levelHelpers() {
        assertEquals(AudioLevel.FLOOR_DBFS, AudioLevel.toDbfs(0));
        assertEquals(0.0, AudioLevel.toDbfs(32_768), 1e-9);
        assertEquals(-6.02, AudioLevel.toDbfs(16_384), 0.01);
        assertEquals(0f, AudioLevel.meter(0));
        assertEquals(1f, AudioLevel.meter(32_767), 0.001f);
        assertEquals(7, AudioLevel.peak(new short[]{1, -7, 3}, 3));
        assertEquals(1, AudioLevel.peak(new short[]{1, -7, 3}, 1));
    }
}

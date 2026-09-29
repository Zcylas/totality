package zcylas.totality.client.voice;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALUtil;
import org.lwjgl.openal.EXTDisconnect;
import zcylas.totality.api.voice.capture.CaptureDeviceProvider;
import zcylas.totality.api.voice.capture.CaptureException;
import zcylas.totality.api.voice.capture.CaptureStream;

import java.util.List;

/**
 * Microphone input through the OpenAL (OpenAL Soft) that Minecraft already ships and initializes.
 * A capture device is independent of the game's playback device and context.
 *
 * <p>The device is opened for exactly mono signed 16-bit PCM at the requested rate; OpenAL Soft
 * converts from whatever the hardware/PipeWire delivers, and a device that cannot provide the format
 * fails to open (reported as {@link CaptureException.Kind#OPEN_FAILED}) rather than delivering
 * something else.
 */
final class OpenAlCaptureDeviceProvider implements CaptureDeviceProvider {

    /** OpenAL-side ring buffer: one second, so a briefly delayed poll never loses audio. */
    private static final int BUFFER_SECONDS = 1;

    @Override
    public List<String> captureDevices() throws CaptureException {
        try {
            List<String> devices = ALUtil.getStringList(0L, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);
            return devices == null ? List.of() : List.copyOf(devices);
        } catch (Throwable t) {
            throw new CaptureException(CaptureException.Kind.NO_DEVICE, "Audio input is unavailable: " + t, t);
        }
    }

    @Override
    @Nullable
    public String defaultCaptureDevice() throws CaptureException {
        try {
            String name = ALC10.alcGetString(0L, ALC11.ALC_CAPTURE_DEFAULT_DEVICE_SPECIFIER);
            return name == null || name.isEmpty() ? null : name;
        } catch (Throwable t) {
            throw new CaptureException(CaptureException.Kind.NO_DEVICE, "Audio input is unavailable: " + t, t);
        }
    }

    @Override
    public CaptureStream open(@Nullable String deviceName, int sampleRate) throws CaptureException {
        long device;
        try {
            device = ALC11.alcCaptureOpenDevice(deviceName, sampleRate, AL10.AL_FORMAT_MONO16, sampleRate * BUFFER_SECONDS);
        } catch (Throwable t) {
            throw new CaptureException(CaptureException.Kind.OPEN_FAILED, "Audio input is unavailable: " + t, t);
        }
        if (device == 0L) {
            throw new CaptureException(CaptureException.Kind.OPEN_FAILED, "Could not open "
                    + (deviceName == null ? "the default microphone" : "microphone '" + deviceName + "'")
                    + " for 16 kHz mono 16-bit capture");
        }
        String opened = deviceName;
        try {
            String actual = ALC10.alcGetString(device, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);
            if (actual != null && !actual.isEmpty()) opened = actual;
            ALC11.alcCaptureStart(device);
        } catch (Throwable t) {
            ALC11.alcCaptureCloseDevice(device);
            throw new CaptureException(CaptureException.Kind.OPEN_FAILED, "Could not start capture: " + t, t);
        }
        boolean canDetectDisconnect = ALC10.alcIsExtensionPresent(device, "ALC_EXT_disconnect");
        return new Stream(device, opened == null ? "default" : opened, canDetectDisconnect);
    }

    /** One open OpenAL capture device. Used by a single capture worker thread; close is idempotent. */
    private static final class Stream implements CaptureStream {
        private final String name;
        private final boolean canDetectDisconnect;
        private long device;

        Stream(long device, String name, boolean canDetectDisconnect) {
            this.device = device;
            this.name = name;
            this.canDetectDisconnect = canDetectDisconnect;
        }

        @Override
        public String deviceName() {
            return name;
        }

        @Override
        public synchronized int availableSamples() throws CaptureException {
            ensureOpen();
            return ALC10.alcGetInteger(device, ALC11.ALC_CAPTURE_SAMPLES);
        }

        @Override
        public synchronized void read(short[] dst, int count) throws CaptureException {
            ensureOpen();
            if (count <= 0) return;
            if (count > dst.length) throw new IllegalArgumentException("count " + count);
            if (count == dst.length) {
                ALC11.alcCaptureSamples(device, dst, count);
            } else {
                short[] exact = new short[count];
                ALC11.alcCaptureSamples(device, exact, count);
                System.arraycopy(exact, 0, dst, 0, count);
            }
        }

        @Override
        public synchronized boolean isConnected() {
            if (device == 0L) return false;
            return !canDetectDisconnect || ALC10.alcGetInteger(device, EXTDisconnect.ALC_CONNECTED) != 0;
        }

        @Override
        public synchronized void close() {
            long d = device;
            device = 0L;
            if (d == 0L) return;
            try {
                ALC11.alcCaptureStop(d);
            } finally {
                ALC11.alcCaptureCloseDevice(d);
            }
        }

        private void ensureOpen() throws CaptureException {
            if (device == 0L) throw new CaptureException(CaptureException.Kind.READ_FAILED, "Microphone is closed");
        }
    }
}

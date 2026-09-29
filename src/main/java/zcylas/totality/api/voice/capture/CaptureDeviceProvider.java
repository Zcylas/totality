package zcylas.totality.api.voice.capture;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Platform audio input (OpenAL in the client; fakes in tests). Implementations only open, read and
 * close devices; threading, chunking, levels and limits belong to {@link MicrophoneManager}.
 */
public interface CaptureDeviceProvider {

    /** Names of the available capture devices, in the platform's order. */
    List<String> captureDevices() throws CaptureException;

    /** The platform's default capture device, or null if there is none. */
    @Nullable String defaultCaptureDevice() throws CaptureException;

    /**
     * Opens a device for mono signed 16-bit PCM at {@code sampleRate} and starts capturing.
     * The returned stream must deliver exactly that format; if the device cannot, this throws
     * {@link CaptureException.Kind#OPEN_FAILED} instead of returning a stream in another format.
     *
     * @param deviceName device to open, or null for the default device
     */
    CaptureStream open(@Nullable String deviceName, int sampleRate) throws CaptureException;
}

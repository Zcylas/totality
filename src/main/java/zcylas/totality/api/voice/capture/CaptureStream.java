package zcylas.totality.api.voice.capture;

/**
 * An open, capturing device. Owned by exactly one thread (the {@link MicrophoneManager} capture
 * worker), which is also the only thread that closes it. {@link #close()} is idempotent.
 */
public interface CaptureStream extends AutoCloseable {

    /** The name of the device actually opened. */
    String deviceName();

    /** Samples captured and ready to read without blocking. */
    int availableSamples() throws CaptureException;

    /** Reads {@code count} samples into {@code dst[0..count)}; {@code count} must not exceed {@link #availableSamples()}. */
    void read(short[] dst, int count) throws CaptureException;

    /** False once the platform reports the device as disconnected. */
    boolean isConnected();

    @Override
    void close();
}

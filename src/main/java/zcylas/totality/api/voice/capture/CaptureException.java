package zcylas.totality.api.voice.capture;

/** A capture device could not be listed, opened or read. */
public class CaptureException extends Exception {

    public enum Kind {
        /** No capture device exists (or the audio system is unavailable). */
        NO_DEVICE,
        /** The device refused to open with the requested 16 kHz mono 16-bit format. */
        OPEN_FAILED,
        /** The device disappeared while capturing (e.g. headset unplugged). */
        DISCONNECTED,
        /** Reading from an open device failed. */
        READ_FAILED
    }

    private final Kind kind;

    public CaptureException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public CaptureException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}

package zcylas.totality.api.voice.recognition;

/** A recognition operation could not be performed (backend not ready, session closed, engine error). */
public class RecognitionException extends RuntimeException {

    public RecognitionException(String message) {
        super(message);
    }

    public RecognitionException(String message, Throwable cause) {
        super(message, cause);
    }
}

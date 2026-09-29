package zcylas.totality.api.voice.recognition;

/**
 * What a {@link RecognitionSession} is allowed to hear.
 *
 * <p>FREEFORM is open transcription (the future chat-dictation test). GRAMMAR restricts the
 * recognizer to a fixed phrase set (future Confirm/Cancel/incantation commands); anything else is
 * reported as unrecognized rather than force-fitted onto the nearest phrase.
 */
public enum RecognitionMode {
    FREEFORM,
    GRAMMAR
}

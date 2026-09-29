package zcylas.totality.api.voice.audio;

import java.io.IOException;

/** Audio input that is malformed or in a format recognition does not accept. */
public class UnsupportedAudioException extends IOException {

    public UnsupportedAudioException(String message) {
        super(message);
    }
}

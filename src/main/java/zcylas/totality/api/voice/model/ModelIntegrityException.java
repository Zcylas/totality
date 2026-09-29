package zcylas.totality.api.voice.model;

import java.io.IOException;

/** A model archive or extracted model does not match what Totality pinned, or is unsafe to extract. */
public class ModelIntegrityException extends IOException {

    public ModelIntegrityException(String message) {
        super(message);
    }
}

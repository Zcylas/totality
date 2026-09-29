package zcylas.totality.client.voice;

/** Player-visible state of Voice Input. */
public enum VoiceState {
    /** Turned off in settings; the microphone is never opened and no model is loaded. */
    DISABLED,
    /** Enabled; nothing loaded yet (first push-to-talk press starts loading). */
    UNLOADED,
    /** The speech model is loading on a worker thread. */
    LOADING,
    /** Ready: holding push-to-talk starts listening. */
    READY,
    /** The microphone is open and speech is streamed to the recognizer. */
    LISTENING,
    /** Push-to-talk was released; the utterance is being finalized. */
    RECOGNIZING,
    /** The speech engine could not load; see the failure detail. */
    FAILED
}

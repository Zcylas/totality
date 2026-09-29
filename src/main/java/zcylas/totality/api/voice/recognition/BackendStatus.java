package zcylas.totality.api.voice.recognition;

/**
 * Availability of a {@link RecognitionBackend}. A backend that is not {@link State#READY} simply
 * cannot open sessions; it never affects the rest of the game.
 */
public record BackendStatus(State state, FailureReason reason, String detail) {

    public enum State {
        /** Constructed; nothing native or on disk has been touched. */
        UNLOADED,
        LOADING,
        READY,
        FAILED,
        CLOSED
    }

    public enum FailureReason {
        NONE,
        UNSUPPORTED_PLATFORM,
        NATIVE_LIBRARY_UNAVAILABLE,
        MODEL_UNAVAILABLE,
        MODEL_INVALID,
        /** A previous attempt died inside native code (see {@link NativeCrashGuard}). */
        CRASH_SUSPECTED,
        INTERNAL_ERROR
    }

    public static final BackendStatus UNLOADED = new BackendStatus(State.UNLOADED, FailureReason.NONE, "");
    public static final BackendStatus LOADING = new BackendStatus(State.LOADING, FailureReason.NONE, "");
    public static final BackendStatus CLOSED = new BackendStatus(State.CLOSED, FailureReason.NONE, "");

    public static BackendStatus ready(String detail) {
        return new BackendStatus(State.READY, FailureReason.NONE, detail);
    }

    public static BackendStatus failed(FailureReason reason, String detail) {
        return new BackendStatus(State.FAILED, reason, detail);
    }

    public boolean isReady() {
        return state == State.READY;
    }
}

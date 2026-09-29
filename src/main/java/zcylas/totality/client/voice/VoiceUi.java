package zcylas.totality.client.voice;

/**
 * Everything {@link VoiceInputController} shows or logs. Always called on the client thread. The
 * Minecraft implementation shows notices through {@code NotificationManager} and transcripts as a
 * local-only chat line; tests record calls.
 */
public interface VoiceUi {

    enum Notice {
        DISABLED,
        LOADING,
        READY,
        BUSY,
        NOT_UNDERSTOOD,
        TOO_SHORT,
        NO_SIGNAL,
        TOO_QUIET,
        DEVICE_UNAVAILABLE,
        DEVICE_FALLBACK,
        BACKEND_FAILED,
        CRASH_SUSPECTED,
        MAX_DURATION,
        CANCELLED,
        RESET,
        /** Command push-to-talk while nothing offers a voice command (dictation needs the modifier). */
        NO_COMMAND
    }

    /** Short player-facing feedback. */
    void notice(Notice notice, String detail);

    /**
     * A milestone of one lazy speech-model initialization: {@link Notice#LOADING} when it starts, then
     * exactly one of {@link Notice#READY}, {@link Notice#BACKEND_FAILED} or {@link Notice#CRASH_SUSPECTED}
     * when it ends. Later push-to-talk presses never repeat these (they use {@link #notice}). Defaults to
     * an ordinary notice; the Minecraft UI presents them as System holograms.
     */
    default void modelInitialization(Notice notice, String detail) {
        notice(notice, detail);
    }

    /** How an utterance ended, for the Voice Input HUD (never contains a transcript). */
    default void feedback(VoiceFeedback feedback) {}

    /** A successful transcription, shown only to this player. */
    void transcript(String text);

    /** Release-safe diagnostic line: never contains transcript text or audio. */
    void log(String line);

    /** Debug-only diagnostic line (may contain transcript text); dropped unless debug is on. */
    void debug(String line);
}

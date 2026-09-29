package zcylas.totality.client.voice;

/**
 * A short outcome the Voice Input HUD shows once an utterance ends. Never a transcript: a command's
 * {@code detail} is the canonical command it ran ("confirm"), a failure's a few words of reason.
 *
 * @param operator the utterance was in (or entered) the Operator Mode context
 */
public record VoiceFeedback(Kind kind, VoiceInputController.PushToTalkMode mode, boolean operator, String detail) {

    public enum Kind {
        /** A command's owner accepted and ran it. */
        RECOGNIZED,
        /** Dictation produced a transcript (shown in chat, Edit & Send available). */
        TRANSCRIBED,
        NOT_UNDERSTOOD,
        /** Command mode with nothing to command. */
        NO_COMMAND,
        /** Capture/recognition problem, stale target and similar; {@code detail} says which. */
        FAILED,
        CANCELLED,
        /** An Operator Mode action was sent; the server has not answered yet. */
        AWAITING_SERVER,
        /** The server executed the Operator Mode action. */
        EXECUTED,
        /** The server refused (not authorized). */
        DENIED
    }

    public static VoiceFeedback of(Kind kind, VoiceInputController.PushToTalkMode mode, String detail) {
        return new VoiceFeedback(kind, mode, false, detail);
    }
}

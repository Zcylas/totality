package zcylas.totality.client.voice;

import org.jetbrains.annotations.Nullable;

/**
 * Decides what "Edit & Send" may do with vanilla chat. Pure, so it can be unit-tested.
 *
 * <p>Vanilla 26.2 restores an unsent chat draft whenever chat opens. The transcript is only put into
 * an <i>empty</i> MESSAGE-mode chat input; an existing draft is always left exactly as it was.
 * Nothing here ever sends: the player reviews the text and presses Enter themselves.
 */
final class ChatInsertionPolicy {

    enum Decision {
        /** Chat opens empty; insert the transcript for the player to review. */
        INSERT,
        /** Chat opens with the player's own unsent draft; keep it and insert nothing. */
        KEEP_DRAFT,
        /** There is no transcript yet. */
        NOTHING_TO_INSERT
    }

    private ChatInsertionPolicy() {}

    /**
     * @param initial the text vanilla is about to open chat with (a restored draft, or the MESSAGE prefix "")
     * @param isDraft whether vanilla is restoring a draft
     */
    static Decision decide(@Nullable String transcript, String initial, boolean isDraft) {
        if (transcript == null || transcript.isBlank()) return Decision.NOTHING_TO_INSERT;
        // Any existing text — normally a restored draft — is the player's; never add to or replace it.
        if (isDraft || !initial.isEmpty()) return Decision.KEEP_DRAFT;
        return Decision.INSERT;
    }

    /**
     * The text to put in the chat box: the same sanitized transcript shown locally, never starting
     * with '/', so Enter sends an ordinary chat message rather than running a command.
     */
    static String textFor(String transcript) {
        return VoiceInputController.displayText(transcript);
    }
}

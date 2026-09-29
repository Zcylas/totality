package zcylas.totality.api.voice.command;

import java.util.Set;

/**
 * Spoken commands an owning system offers right now (§26K.11: the Voice API recognizes, the owner
 * decides what a phrase means). A context is a snapshot bound to the exact target that was current
 * when it was obtained — e.g. one particular System hologram — so a result that arrives after that
 * target closed, changed or was interrupted is refused by {@link #isCurrent()}/{@link #execute}.
 *
 * <p>All methods are called on the client thread. Engine-neutral: no Vosk, no Minecraft types.
 */
public interface VoiceCommandContext {

    /**
     * The spoken phrases this context accepts, already normalized (lower case, single spaces), including
     * aliases ("confirmed" for Confirm). Never empty.
     */
    Set<String> phrases();

    /** The canonical command a spoken phrase names ("confirmed" → "confirm"). */
    default String commandOf(String phrase) {
        return phrase;
    }

    /** Whether the target is still the same one and still accepting commands. */
    boolean isCurrent();

    /**
     * Performs the owner's ordinary action for the canonical {@code command} (the same path as clicking
     * it), after re-checking {@link #isCurrent()}. Called at most once per utterance.
     *
     * @return false if the owner refused (stale target, unknown command)
     */
    boolean execute(String command);

    /** Push-to-talk started an utterance for this context. */
    default void listening() {}

    /** The utterance was recognized as {@code command} and executed. */
    default void accepted(String command) {}

    /** The utterance performed no action. */
    default void rejected(VoiceCommandMatcher.Rejection reason) {}

    /** The utterance was addressed to something else (Operator Mode); this target was not touched. */
    default void notAddressed() {}
}

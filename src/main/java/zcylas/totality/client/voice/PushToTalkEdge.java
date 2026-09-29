package zcylas.totality.client.voice;

/** The push-to-talk press rule, kept pure so it can be unit-tested. */
final class PushToTalkEdge {

    private PushToTalkEdge() {}

    /**
     * A press starts an utterance only on the tick the key goes down, in a world, with no screen
     * open and the window focused. Holding the key while a screen closes, or re-focusing the window
     * with the key held, is not a press — the player must press again.
     */
    static boolean isPress(boolean down, boolean wasDown, boolean inWorld, boolean screenOpen, boolean focused) {
        return down && !wasDown && inWorld && !screenOpen && focused;
    }
}

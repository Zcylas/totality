package zcylas.totality.client.voice;

/**
 * Push-to-talk activation, with vanilla's debug shortcuts taking precedence (F3+B shows hitboxes).
 * <ul>
 *   <li>A press starts an utterance only on the tick the key goes down (see {@link PushToTalkEdge}).</li>
 *   <li>While the debug modifier (F3) is held, push-to-talk is a debug chord, not voice: pressing it never starts
 *       listening, and pressing F3 while already listening cancels that utterance (nothing is recognized or run).</li>
 *   <li>A press suppressed this way stays suppressed until push-to-talk is released: letting go of F3 while still
 *       holding it never starts voice late. A fresh press is needed.</li>
 * </ul>
 * Plain state, no game classes, so it can be unit-tested.
 */
final class PushToTalkGate {

    enum Action { NONE, PRESS, CANCEL }

    private boolean wasDown;
    private boolean suppressed;

    Action update(boolean down, boolean debugModifierDown, boolean inWorld, boolean screenOpen, boolean focused, boolean listening) {
        Action action = Action.NONE;
        if (down && debugModifierDown) {
            if (!suppressed && listening) action = Action.CANCEL;
            suppressed = true;
        } else if (!down) {
            suppressed = false;
        }
        if (action == Action.NONE && !suppressed && PushToTalkEdge.isPress(down, wasDown, inWorld, screenOpen, focused)) {
            action = Action.PRESS;
        }
        wasDown = down;
        return action;
    }

    boolean suppressed() {
        return suppressed;
    }
}

package zcylas.totality.api.voice.operator;

import zcylas.totality.api.operator.OperatorAction;
import zcylas.totality.api.operator.OperatorResult;

/**
 * How Voice Input reaches the server for Operator Mode, without the voice code itself ever sending
 * anything: the voice controller talks to this engine-neutral interface; the only implementation that
 * sends (a typed, whitelisted request) lives outside the voice packages and is installed through
 * {@link OperatorChannels}. Answers come back through {@link Listener}.
 */
public interface OperatorChannel {

    /** No Operator Mode (tests, not connected, or nothing installed). */
    OperatorChannel NONE = new OperatorChannel() {
        @Override public boolean mayOffer() { return false; }
        @Override public void requestAuthorization(int requestId) {}
        @Override public void submit(int requestId, OperatorAction action) {}
    };

    /**
     * Whether to listen for Operator Mode at all — a client-side HINT from the permission level the server
     * last reported (so the microphone is not opened for players known not to be operators). Never the
     * security gate: the server re-checks every request.
     */
    boolean mayOffer();

    /** Presentation: ask the server whether this player is authorized (the answer grants nothing). */
    void requestAuthorization(int requestId);

    /** Ask the server to perform ONE whitelisted action for this completed utterance. */
    void submit(int requestId, OperatorAction action);

    /** The server's answers (client thread). */
    interface Listener {
        void authorization(int requestId, boolean authorized);

        void result(int requestId, OperatorResult result);
    }
}

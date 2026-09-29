package zcylas.totality.api.voice.operator;

import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.operator.OperatorAction;
import zcylas.totality.api.operator.OperatorResult;

/**
 * The installed {@link OperatorChannel} and the listener its answers go to (the same owner/registry
 * pattern as {@code VoiceCommandContexts}). Client thread.
 */
public final class OperatorChannels {

    private static volatile OperatorChannel installed = OperatorChannel.NONE;
    private static volatile OperatorChannel.@Nullable Listener listener;

    /** Always forwards to whatever channel is installed at the time of the call. */
    public static final OperatorChannel CURRENT = new OperatorChannel() {
        @Override public boolean mayOffer() { return installed.mayOffer(); }
        @Override public void requestAuthorization(int requestId) { installed.requestAuthorization(requestId); }
        @Override public void submit(int requestId, OperatorAction action) { installed.submit(requestId, action); }
    };

    private OperatorChannels() {}

    public static void install(OperatorChannel channel) {
        installed = channel == null ? OperatorChannel.NONE : channel;
    }

    public static void listen(OperatorChannel.@Nullable Listener l) {
        listener = l;
    }

    /** Called by the installed channel with the server's authorization answer. */
    public static void deliverAuthorization(int requestId, boolean authorized) {
        OperatorChannel.Listener l = listener;
        if (l != null) l.authorization(requestId, authorized);
    }

    /** Called by the installed channel with the server's result. */
    public static void deliverResult(int requestId, OperatorResult result) {
        OperatorChannel.Listener l = listener;
        if (l != null) l.result(requestId, result);
    }
}

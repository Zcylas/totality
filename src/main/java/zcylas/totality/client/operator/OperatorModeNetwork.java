package zcylas.totality.client.operator;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.server.permissions.Permissions;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.operator.OperatorAction;
import zcylas.totality.api.voice.operator.OperatorChannel;
import zcylas.totality.api.voice.operator.OperatorChannels;
import zcylas.totality.networking.operator.OperatorActionPayload;
import zcylas.totality.networking.operator.OperatorActionResultPayload;
import zcylas.totality.networking.operator.OperatorAuthorizationPayload;
import zcylas.totality.networking.operator.OperatorAuthorizationQueryPayload;

/**
 * The client's only Operator Mode sender, deliberately outside the voice packages (voice code never
 * sends to the server): typed requests — an action ordinal, never text — and the server's answers
 * back through {@link OperatorChannels}. {@link #mayOffer()} is a hint from the permission level the
 * server last reported, so non-operators never get their microphone opened for it; the server
 * re-checks every request on its own.
 */
public final class OperatorModeNetwork implements OperatorChannel {

    /**
     * Development capture run only: null = the real permission hint; TRUE = offer Operator Mode even when
     * the client believes the player is not an operator (to prove the SERVER refuses it); FALSE = behave
     * as a non-operator client. Ignored outside the capture run.
     */
    public static volatile @Nullable Boolean captureOverride;

    private OperatorModeNetwork() {}

    public static void register() {
        OperatorChannels.install(new OperatorModeNetwork());
        ClientPlayNetworking.registerGlobalReceiver(OperatorAuthorizationPayload.TYPE, (payload, context) ->
                context.client().execute(() -> OperatorChannels.deliverAuthorization(payload.requestId(), payload.authorized())));
        ClientPlayNetworking.registerGlobalReceiver(OperatorActionResultPayload.TYPE, (payload, context) ->
                context.client().execute(() -> OperatorChannels.deliverResult(payload.requestId(), payload.result())));
    }

    private static boolean captureRun() {
        return VerificationReporter.isDevEnvironment() && Boolean.getBoolean("totality.hologram.capture");
    }

    @Override
    public boolean mayOffer() {
        var player = Minecraft.getInstance().player;
        if (player == null || !ClientPlayNetworking.canSend(OperatorActionPayload.TYPE)) return false;
        Boolean override = captureOverride;
        if (override != null && captureRun()) return override;
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @Override
    public void requestAuthorization(int requestId) {
        if (ClientPlayNetworking.canSend(OperatorAuthorizationQueryPayload.TYPE)) {
            ClientPlayNetworking.send(new OperatorAuthorizationQueryPayload(requestId));
        }
    }

    @Override
    public void submit(int requestId, OperatorAction action) {
        if (ClientPlayNetworking.canSend(OperatorActionPayload.TYPE)) {
            ClientPlayNetworking.send(new OperatorActionPayload(requestId, action));
        }
    }
}

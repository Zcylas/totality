package zcylas.totality.networking.dialogue;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import zcylas.totality.api.dialogue.DialogueSessionManager;

public final class DialogueChoiceHandler {

    private DialogueChoiceHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                DialogueChoicePayload.TYPE,
                (payload, context) -> context.server().execute(
                        () -> DialogueSessionManager.handleChoice(context.player(), payload.choiceIndex())
                )
        );
    }
}

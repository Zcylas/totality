package zcylas.totality.networking.quest;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import zcylas.totality.api.quest.QuestManager;

public final class OpenQuestAppHandler {

    private OpenQuestAppHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                OpenQuestAppPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    var player = context.player();
                    QuestManager.onQuestAppOpened(player);
                    ServerPlayNetworking.send(player, new ShowQuestStatePayload(QuestManager.buildDisplay(player)));
                })
        );
    }
}

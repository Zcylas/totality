package zcylas.totality.networking.quest;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.quest.QuestManager;

public final class FinishQuestHandler {

    private FinishQuestHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                FinishQuestPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    Identifier questId = Identifier.tryParse(payload.questId());
                    if (questId == null) return;
                    var player = context.player();
                    QuestManager.finishQuest(player, questId);
                    ServerPlayNetworking.send(player, new ShowQuestStatePayload(QuestManager.buildDisplay(player)));
                })
        );
    }
}

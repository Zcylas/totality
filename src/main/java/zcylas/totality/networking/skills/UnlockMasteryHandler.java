package zcylas.totality.networking.skills;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.skills.core.*;
import zcylas.totality.networking.notification.SendNotificationPayload;

public class UnlockMasteryHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                UnlockMasteryPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> handle(player, payload.skill(), payload.masteryId()));
                }
        );
    }

    private static void handle(ServerPlayer player, Skill skill, String masteryId) {
        var mastery = MasteryRegistry.get(skill, masteryId).orElse(null);
        if (mastery == null) return;

        int skillLevel = SkillsComponents.getSkills(player).getLevel(skill);
        var masteryComp = MasteriesComponents.get(player);
        boolean success = masteryComp.getMasteries().unlockNextRank(mastery, skillLevel);

        if (success) {
            int newRank = masteryComp.getMasteries().getUnlockedRank(masteryId);
            masteryComp.sync();
            SendNotificationPayload.send(player,
                    "✦ " + mastery.getName() + " rank " + newRank + " unlocked!",
                    SendNotificationPayload.GOLD);
        }
    }

    private UnlockMasteryHandler() {}
}
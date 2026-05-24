package zcylas.totality.networking.stats;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.PlayerResourceRecalculator;
import zcylas.totality.api.rpg.stats.StatsComponents;
import zcylas.totality.networking.notification.SendNotificationPayload;

public class SpendAttributePointHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                SpendAttributePointPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> handle(player, payload.score()));
                }
        );
    }

    private static void handle(ServerPlayer player, AbilityScore score) {
        PlayerStats stats = StatsComponents.getStats(player);

        if (stats.getUnspentAttributePoints() <= 0) return;

        boolean spent = stats.spendAttributePoint(score);
        if (!spent) return;

        // Recalculate all resources — handles HP, stamina, mana, sync
        PlayerResourceRecalculator.recalculate(player);

        // Notify
        SendNotificationPayload.send(player,
                score.getDisplayName() + " increased to " + stats.getScore(score) + "!",
                SendNotificationPayload.GOLD);
    }

    private SpendAttributePointHandler() {}
}
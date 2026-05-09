package zcylas.totality.networking.stats;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatAttributeApplier;
import zcylas.totality.api.rpg.stats.StatsComponents;
import zcylas.totality.networking.notification.SendNotificationPayload;
import zcylas.totality.networking.stamina.StaminaServerTick;
import zcylas.totality.api.rpg.mana.PlayerManaManager;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;

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

        // Reapply attribute modifiers (CON → HP etc.)
        StatAttributeApplier.apply(player);

        // If END or INT changed, clamp current resources to new max
        // (spending into these increases max, so no clamping needed — player keeps current)
        // But we sync so client sees updated max
        StaminaServerTick.syncStamina(player);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
                new zcylas.totality.networking.mana.SyncManaPayload(
                        PlayerManaManager.getMana(player),
                        PlayerManaManager.getMaxMana(player)));

        // Sync stats to client so screen updates
        StatsComponents.get(player).sync();

        // Notify
        SendNotificationPayload.send(player,
                score.getDisplayName() + " increased to " + stats.getScore(score) + "!",
                SendNotificationPayload.GOLD);
    }

    private SpendAttributePointHandler() {}
}
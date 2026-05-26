package zcylas.totality.networking.mana;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.mana.PlayerManaManager;
import zcylas.totality.api.rpg.resources.ResourceComponents;

public class ManaServerTick {
    private static int tickCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter % 20 == 0) {
                tickCounter = 0;
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    int current = PlayerManaManager.getMana(player);
                    int max = PlayerManaManager.getMaxMana(player);
                    if (current < max) {
                        PlayerManaManager.addMana(player,
                                PlayerManaManager.calculateRegenAmount(player));
                    }
// Clamp current mana to max in case a Fortify effect just expired
                    PlayerManaManager.setMana(player, PlayerManaManager.getMana(player));
                    syncMana(player);

                }
            }
        });
    }

    public static void syncMana(ServerPlayer player) {
        ServerPlayNetworking.send(player, new SyncManaPayload(
                PlayerManaManager.getMana(player),
                PlayerManaManager.getMaxMana(player)));
    }
}
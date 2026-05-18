package zcylas.totality.api.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.impl.VeinminerAbility;

public class AbilityServerTick {

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                VeinminerAbility.tickPlayer(player);
            }
        });
    }

    private AbilityServerTick() {}
}
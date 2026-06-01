package zcylas.totality.api.rpg.rest;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;

public final class RestManager {

    public static void shortRest(ServerPlayer player) {
        Totality.LOGGER.info("Short rest: {}", player.getName().getString());
        RestEventBus.fire(player, RestType.SHORT);
    }

    public static void longRest(ServerPlayer player) {
        Totality.LOGGER.info("Long rest: {}", player.getName().getString());
        RestEventBus.fire(player, RestType.LONG);
    }

    private RestManager() {}
}
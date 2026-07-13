package zcylas.totality.api.rpg.rest;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;

public final class RestManager {

    /** BG3-style cap: only 2 Short Rests allowed before a Long Rest is required. */
    public static final int SHORT_REST_CAP = 2;

    public static boolean hasShortRestAvailable(ServerPlayer player) {
        return RestComponents.get(player).getShortRestsUsed() < SHORT_REST_CAP;
    }

    public static int getShortRestRemaining(ServerPlayer player) {
        return Math.max(0, SHORT_REST_CAP - RestComponents.get(player).getShortRestsUsed());
    }

    public static int getShortRestCount(ServerPlayer player) {
        return RestComponents.get(player).getShortRestsUsed();
    }

    public static void shortRest(ServerPlayer player) {
        Totality.LOGGER.debug("Short rest: {}", player.getName().getString());
        RestComponents.get(player).incrementShortRestsUsed();
        RestEventBus.fire(player, RestType.SHORT);
    }

    public static void longRest(ServerPlayer player) {
        Totality.LOGGER.debug("Long rest: {}", player.getName().getString());
        RestComponents.get(player).resetShortRestsUsed();
        RestEventBus.fire(player, RestType.LONG);
    }

    private RestManager() {}
}

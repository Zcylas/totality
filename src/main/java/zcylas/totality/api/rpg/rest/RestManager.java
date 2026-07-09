package zcylas.totality.api.rpg.rest;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RestManager {

    /** BG3-style cap: only 2 Short Rests allowed before a Long Rest is required. */
    public static final int SHORT_REST_CAP = 2;

    private static final Map<UUID, Integer> SHORT_REST_COUNTS = new HashMap<>();

    public static boolean hasShortRestAvailable(ServerPlayer player) {
        return SHORT_REST_COUNTS.getOrDefault(player.getUUID(), 0) < SHORT_REST_CAP;
    }

    public static int getShortRestRemaining(ServerPlayer player) {
        return Math.max(0, SHORT_REST_CAP - SHORT_REST_COUNTS.getOrDefault(player.getUUID(), 0));
    }

    public static int getShortRestCount(ServerPlayer player) {
        return SHORT_REST_COUNTS.getOrDefault(player.getUUID(), 0);
    }

    public static void shortRest(ServerPlayer player) {
        Totality.LOGGER.debug("Short rest: {}", player.getName().getString());
        SHORT_REST_COUNTS.merge(player.getUUID(), 1, Integer::sum);
        RestEventBus.fire(player, RestType.SHORT);
    }

    public static void longRest(ServerPlayer player) {
        Totality.LOGGER.debug("Long rest: {}", player.getName().getString());
        SHORT_REST_COUNTS.remove(player.getUUID());
        RestEventBus.fire(player, RestType.LONG);
    }

    public static void clearPlayer(UUID playerId) {
        SHORT_REST_COUNTS.remove(playerId);
    }

    private RestManager() {}
}
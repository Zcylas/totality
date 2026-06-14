package zcylas.totality.api.rpg.rest;

import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public final class RestEventBus {

    private static final Map<UUID, List<RestListener>> LISTENERS = new HashMap<>();

    public static void register(ServerPlayer player, RestListener listener) {
        LISTENERS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(listener);
    }

    public static void fire(ServerPlayer player, RestType type) {
        List<RestListener> listeners = LISTENERS.get(player.getUUID());
        if (listeners == null) return;
        // Sort by priority before firing — lower number = higher priority.
        // Resources restore before abilities (priority 0 before 10).
        listeners.stream()
                .sorted(Comparator.comparingInt(RestListener::restPriority))
                .forEach(l -> l.onRest(player, type));
    }

    public static void clearPlayer(UUID playerId) { LISTENERS.remove(playerId); }

    private RestEventBus() {}
}
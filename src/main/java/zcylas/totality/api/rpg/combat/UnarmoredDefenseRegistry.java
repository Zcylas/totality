package zcylas.totality.api.rpg.combat;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.stats.PlayerStats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class UnarmoredDefenseRegistry {

    @FunctionalInterface
    public interface UnarmoredDefenseProvider {
        int calculate(ServerPlayer player, PlayerStats stats);
    }

    private static final Map<UUID, UnarmoredDefenseProvider> PROVIDERS = new HashMap<>();

    public static void register(ServerPlayer player, UnarmoredDefenseProvider provider) {
        PROVIDERS.put(player.getUUID(), provider);
    }

    public static void remove(ServerPlayer player) {
        PROVIDERS.remove(player.getUUID());
    }

    public static void clearPlayer(UUID uuid) {
        PROVIDERS.remove(uuid);
    }

    public static UnarmoredDefenseProvider get(ServerPlayer player) {
        return PROVIDERS.get(player.getUUID());
    }

    private UnarmoredDefenseRegistry() {}
}
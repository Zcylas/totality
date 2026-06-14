package zcylas.totality.client.mob;

import zcylas.totality.api.mob.stats.SpawnRarity;

import java.util.HashMap;
import java.util.Map;

public final class MobStatsClientCache {

    public record MobClientData(int level, int rankOrdinal, int ac, int rarityOrdinal) {
        public SpawnRarity rarity() {
            SpawnRarity[] values = SpawnRarity.values();
            return rarityOrdinal >= 0 && rarityOrdinal < values.length
                    ? values[rarityOrdinal] : SpawnRarity.COMMON;
        }
    }

    private static final Map<Integer, MobClientData> CACHE = new HashMap<>();

    public static void update(int entityId, int level, int rankOrdinal, int ac, int rarityOrdinal) {
        CACHE.put(entityId, new MobClientData(level, rankOrdinal, ac, rarityOrdinal));
    }

    public static MobClientData get(int entityId) {
        return CACHE.get(entityId);
    }

    public static void clear() { CACHE.clear(); }

    private MobStatsClientCache() {}
}
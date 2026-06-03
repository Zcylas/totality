package zcylas.totality.client.mob;

import java.util.HashMap;
import java.util.Map;

public final class MobStatsClientCache {

    public record MobClientData(int level, int rankOrdinal, int ac) {}

    private static final Map<Integer, MobClientData> CACHE = new HashMap<>();

    public static void update(int entityId, int level, int rankOrdinal, int ac) {
        CACHE.put(entityId, new MobClientData(level, rankOrdinal, ac));
    }

    public static MobClientData get(int entityId) {
        return CACHE.get(entityId);
    }

    public static void clear() { CACHE.clear(); }

    private MobStatsClientCache() {}
}
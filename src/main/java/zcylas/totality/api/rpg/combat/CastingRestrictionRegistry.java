package zcylas.totality.api.rpg.combat;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import java.util.*;

public final class CastingRestrictionRegistry {

    @FunctionalInterface
    public interface RestrictionProvider {
        @Nullable String getRestriction(ServerPlayer player);
    }

    private static final Map<UUID, Map<Identifier, RestrictionProvider>> RESTRICTIONS = new HashMap<>();

    public static void register(ServerPlayer player, Identifier sourceId, RestrictionProvider provider) {
        RESTRICTIONS.computeIfAbsent(player.getUUID(), k -> new LinkedHashMap<>())
                .put(sourceId, provider);
    }

    public static void remove(ServerPlayer player, Identifier sourceId) {
        Map<Identifier, RestrictionProvider> map = RESTRICTIONS.get(player.getUUID());
        if (map != null) map.remove(sourceId);
    }

    @Nullable
    public static String check(ServerPlayer player) {
        Map<Identifier, RestrictionProvider> map = RESTRICTIONS.get(player.getUUID());
        if (map == null || map.isEmpty()) return null;
        for (RestrictionProvider provider : map.values()) {
            String restriction = provider.getRestriction(player);
            if (restriction != null) return restriction;
        }
        return null;
    }

    public static void clearPlayer(UUID playerId) { RESTRICTIONS.remove(playerId); }

    private CastingRestrictionRegistry() {}
}
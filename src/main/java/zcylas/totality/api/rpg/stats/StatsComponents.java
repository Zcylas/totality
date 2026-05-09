package zcylas.totality.api.rpg.stats;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class StatsComponents {

    public static final ComponentKey<PlayerStatsComponent> PLAYER_STATS = ComponentRegistry.getOrCreate(
            Identifier.fromNamespaceAndPath("totality", "player_stats"),
            PlayerStatsComponent.class
    );

    private StatsComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                PLAYER_STATS,
                PlayerStatsComponent::new,
                RespawnStrategy.ALWAYS_COPY  // stats survive death
        );
        PlayerComponentEvents.registerClientComponent(
                PLAYER_STATS,
                () -> new PlayerStatsComponent(null)
        );
    }

    /**
     * Convenience method to get the PlayerStatsComponent from a player.
     */
    public static PlayerStatsComponent get(ServerPlayer player) {
        return PLAYER_STATS.get((ComponentProvider) player);
    }

    /**
     * Convenience method to get the PlayerStats data directly.
     */
    public static PlayerStats getStats(ServerPlayer player) {
        return get(player).getStats();
    }
}
package zcylas.totality.api.rpg.skills.core;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class MasteriesComponents {

    public static final ComponentKey<PlayerMasteriesComponent> PLAYER_MASTERIES =
            ComponentRegistry.getOrCreate(
                    Identifier.fromNamespaceAndPath("totality", "player_masteries"),
                    PlayerMasteriesComponent.class
            );

    private MasteriesComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                PLAYER_MASTERIES,
                PlayerMasteriesComponent::new,
                RespawnStrategy.ALWAYS_COPY
        );
        PlayerComponentEvents.registerClientComponent(
                PLAYER_MASTERIES,
                () -> new PlayerMasteriesComponent(null)
        );
    }

    public static PlayerMasteriesComponent get(ServerPlayer player) {
        return PLAYER_MASTERIES.get((ComponentProvider) player);
    }

    public static PlayerMasteries getMasteries(ServerPlayer player) {
        return get(player).getMasteries();
    }
}
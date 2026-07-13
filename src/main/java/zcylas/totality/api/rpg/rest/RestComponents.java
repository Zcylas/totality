package zcylas.totality.api.rpg.rest;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class RestComponents {

    public static final ComponentKey<RestStateComponent> REST_STATE = ComponentRegistry.getOrCreate(
            Identifier.fromNamespaceAndPath("totality", "rest_state"),
            RestStateComponent.class
    );

    private RestComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                REST_STATE,
                player -> new RestStateComponent(),
                RespawnStrategy.ALWAYS_COPY
        );
    }

    public static RestStateComponent get(ServerPlayer player) {
        return REST_STATE.get((ComponentProvider) player);
    }
}

package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class ClassComponents {

    public static final ComponentKey<PlayerClassComponent> PLAYER_CLASS =
            ComponentRegistry.getOrCreate(
                    Identifier.fromNamespaceAndPath("totality", "player_class"),
                    PlayerClassComponent.class
            );

    private ClassComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                PLAYER_CLASS,
                PlayerClassComponent::new,
                RespawnStrategy.ALWAYS_COPY
        );
        PlayerComponentEvents.registerClientComponent(
                PLAYER_CLASS,
                () -> new PlayerClassComponent(null)
        );
    }

    public static PlayerClassComponent get(ServerPlayer player) {
        return PLAYER_CLASS.get((ComponentProvider) player);
    }
}
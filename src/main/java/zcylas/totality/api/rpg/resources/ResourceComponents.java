package zcylas.totality.api.rpg.resources;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class ResourceComponents {

    public static final ComponentKey<PlayerResourceComponent> RESOURCES =
            ComponentRegistry.getOrCreate(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "resources"),
                    PlayerResourceComponent.class
            );

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                RESOURCES,
                PlayerResourceComponent::new,
                RespawnStrategy.ALWAYS_COPY
        );
    }

    public static PlayerResourceComponent get(ServerPlayer player) {
        return RESOURCES.get((ComponentProvider) player);
    }

    private ResourceComponents() {}
}
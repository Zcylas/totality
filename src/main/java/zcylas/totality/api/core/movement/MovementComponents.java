package zcylas.totality.api.core.movement;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class MovementComponents {

    public static final ComponentKey<PlayerMovementComponent> MOVEMENT =
            ComponentRegistry.getOrCreate(
                    Identifier.fromNamespaceAndPath("totality", "movement"),
                    PlayerMovementComponent.class
            );

    private MovementComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                MOVEMENT,
                PlayerMovementComponent::new,
                RespawnStrategy.ALWAYS_COPY
        );
        PlayerComponentEvents.registerClientComponent(
                MOVEMENT,
                () -> new PlayerMovementComponent(null)
        );
    }
}
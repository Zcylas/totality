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

    /**
     * Non-throwing, non-mutating lookup — returns {@link java.util.Optional#empty()} rather than
     * throwing if the component has not been attached to {@code player} yet, instead of {@link #get}'s
     * throw-on-absent contract. Added for {@code ManaResourceAdapter}/{@code StaminaResourceAdapter}
     * (Phase 2C): a read-only Generic Resource query must never throw, and must never go through
     * {@code PlayerManaManager.getMana}/{@code PlayerStaminaManager.getStamina} (which lazily
     * initialize the component as a side effect) just to read a value that may not exist yet.
     */
    public static java.util.Optional<PlayerResourceComponent> maybeGet(ServerPlayer player) {
        return RESOURCES.maybeGet((ComponentProvider) player);
    }

    private ResourceComponents() {}
}
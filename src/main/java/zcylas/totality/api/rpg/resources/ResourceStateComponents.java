package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

/**
 * Registers {@link PlayerResourceStateComponent} under the new {@code totality:resource_state}
 * identifier.
 *
 * This is deliberately a <b>separate</b> identifier from the legacy {@code totality:resources}
 * component ({@link ResourceComponents}, which owns Stamina and Mana today). The legacy
 * identifier is not reused or replaced by this patch — it remains the sole authority for
 * Stamina/Mana until an explicit later migration imports its data into this component and
 * retires it. See the readiness audit's migration matrix.
 */
public final class ResourceStateComponents {

    public static final ComponentKey<PlayerResourceStateComponent> RESOURCE_STATE =
            ComponentRegistry.getOrCreate(
                    Identifier.fromNamespaceAndPath("totality", "resource_state"),
                    PlayerResourceStateComponent.class
            );

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                RESOURCE_STATE,
                PlayerResourceStateComponent::new,
                RespawnStrategy.ALWAYS_COPY
        );
        PlayerComponentEvents.registerClientComponent(
                RESOURCE_STATE,
                () -> new PlayerResourceStateComponent(null)
        );
    }

    public static PlayerResourceStateComponent get(ServerPlayer player) {
        return RESOURCE_STATE.get((ComponentProvider) player);
    }

    private ResourceStateComponents() {}
}

package zcylas.totality.api.rpg.ancestry;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class AncestryComponents {

    public static final ComponentKey<PlayerAncestryComponent> PLAYER_ANCESTRY =
            ComponentRegistry.getOrCreate(
                    Identifier.fromNamespaceAndPath("totality", "player_ancestry"),
                    PlayerAncestryComponent.class
            );

    private AncestryComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                PLAYER_ANCESTRY,
                PlayerAncestryComponent::new,
                RespawnStrategy.ALWAYS_COPY
        );
        PlayerComponentEvents.registerClientComponent(
                PLAYER_ANCESTRY,
                () -> new PlayerAncestryComponent(null)
        );
    }

    public static PlayerAncestryComponent get(ServerPlayer player) {
        return PLAYER_ANCESTRY.get((ComponentProvider) player);
    }
}
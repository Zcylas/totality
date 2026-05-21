package zcylas.totality.api.rpg.race;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class RaceComponents {

    public static final ComponentKey<RaceComponent> PLAYER_RACE = ComponentRegistry.getOrCreate(
            Identifier.fromNamespaceAndPath("totality", "player_race"),
            RaceComponent.class
    );

    private RaceComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                PLAYER_RACE,
                RaceComponent::new,
                RespawnStrategy.ALWAYS_COPY  // race survives death
        );
        PlayerComponentEvents.registerClientComponent(
                PLAYER_RACE,
                () -> new RaceComponent(null)
        );
    }

    public static RaceComponent get(ServerPlayer player) {
        return PLAYER_RACE.get((ComponentProvider) player);
    }

    public static Race getRace(ServerPlayer player) {
        return get(player).getRace();
    }
}
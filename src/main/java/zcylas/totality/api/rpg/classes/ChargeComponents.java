package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.*;

public final class ChargeComponents {

    public static final ComponentKey<PlayerChargesComponent> PLAYER_CHARGES =
            ComponentRegistry.getOrCreate(
                    Identifier.fromNamespaceAndPath("totality", "player_charges"),
                    PlayerChargesComponent.class
            );

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                PLAYER_CHARGES,
                player -> {
                    PlayerChargesComponent comp = new PlayerChargesComponent(player);
                    return comp;
                },
                RespawnStrategy.ALWAYS_COPY
        );
        PlayerComponentEvents.registerClientComponent(
                PLAYER_CHARGES,
                () -> new PlayerChargesComponent(null)
        );
    }

    public static PlayerChargesComponent get(ServerPlayer player) {
        return PLAYER_CHARGES.get((ComponentProvider) player);
    }
}
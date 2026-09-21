package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.*;

import java.util.Optional;

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

    /**
     * Gets the component without throwing if it is absent — the safe, read-only lookup path a
     * generic Resource API query must use instead of {@link #get}, which throws
     * {@code IllegalStateException} for a player the component was never attached to. Mirrors
     * {@code ResourceComponents.maybeGet(ServerPlayer)}/{@code SpellSlotComponents.maybeGet(ServerPlayer)}'s
     * established precedent, added here for the Rage external adapter (Phase 2E).
     */
    public static Optional<PlayerChargesComponent> maybeGet(ServerPlayer player) {
        return PLAYER_CHARGES.maybeGet((ComponentProvider) player);
    }
}
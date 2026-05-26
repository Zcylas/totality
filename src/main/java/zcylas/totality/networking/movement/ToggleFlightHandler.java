package zcylas.totality.networking.movement;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.movement.MovementComponents;
import zcylas.totality.api.core.movement.MovementMode;
import zcylas.totality.api.core.movement.MovementModeProvider;
import zcylas.totality.api.core.movement.PlayerMovementComponent;

public final class ToggleFlightHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                ToggleFlightPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> handle(player, payload));
                }
        );
    }

    private static void handle(ServerPlayer player, ToggleFlightPayload payload) {
        // Validate — player must have a passive ability that grants FLIGHT
        AbilityComponent abilities = AbilityComponents.ABILITIES.get(
                (ComponentProvider) player);

        boolean hasFlight = abilities.getUnlocked().stream()
                .anyMatch(id -> {
                    Ability ability = AbilityRegistry.get(id);
                    return ability instanceof MovementModeProvider provider
                            && ability.getType() == Ability.Type.PASSIVE
                            && provider.getGrantedModes().contains(MovementMode.FLIGHT);
                });

        if (!hasFlight) return;

        PlayerMovementComponent movement = MovementComponents.MOVEMENT.get(
                (ComponentProvider) player);
        movement.setActivelyFlying(payload.enabled());
    }

    private ToggleFlightHandler() {}
}
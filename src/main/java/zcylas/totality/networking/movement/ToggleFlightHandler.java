package zcylas.totality.networking.movement;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.movement.MovementComponents;
import zcylas.totality.api.core.movement.MovementMode;
import zcylas.totality.api.core.movement.MovementStaminaCosts;
import zcylas.totality.api.core.movement.PlayerMovementComponent;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;

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
        boolean hasFlight = MovementStaminaHandler.hasMovementMode(player, MovementMode.FLIGHT);

        if (!hasFlight) return;

        if (payload.enabled()
                && !PlayerStaminaManager.hasStamina(player, MovementStaminaCosts.FLIGHT_TOGGLE_MINIMUM)) {
            return;
        }

        PlayerMovementComponent movement = MovementComponents.MOVEMENT.get(
                (ComponentProvider) player);
        if (payload.enabled()) {
            movement.setPowerSprinting(false);
        }

        movement.setActivelyFlying(payload.enabled());
    }

    private ToggleFlightHandler() {}
}
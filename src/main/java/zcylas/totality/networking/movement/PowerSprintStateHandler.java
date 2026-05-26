package zcylas.totality.networking.movement;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.movement.MovementComponents;
import zcylas.totality.api.core.movement.MovementMode;
import zcylas.totality.api.core.movement.PlayerMovementComponent;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;

public final class PowerSprintStateHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                PowerSprintStatePayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> handle(player, payload.active()));
                }
        );
    }

    private static void handle(ServerPlayer player, boolean active) {
        PlayerMovementComponent movement = MovementComponents.MOVEMENT.get(
                (ComponentProvider) player
        );

        boolean allowed = active
                && player.isSprinting()
                && !movement.isActivelyFlying()
                && MovementStaminaHandler.hasMovementMode(player, MovementMode.POWER_SPRINT)
                && PlayerStaminaManager.getStamina(player) > 0;

        movement.setPowerSprinting(allowed);
    }

    private PowerSprintStateHandler() {}
}
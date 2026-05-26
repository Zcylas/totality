package zcylas.totality.networking.movement;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.movement.MovementMode;
import zcylas.totality.api.core.movement.MovementModeProvider;
import zcylas.totality.api.core.movement.MovementStaminaCosts;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.networking.stamina.StaminaServerTick;

public final class MovementStaminaHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                MovementStaminaPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> handle(player, payload.mode()));
                }
        );
    }

    private static void handle(ServerPlayer player, MovementMode mode) {
        if (player.isCreative()) return;
        if (!hasMovementMode(player, mode)) return;

        int cost = switch (mode) {
            case POWER_SPRINT -> MovementStaminaCosts.POWER_SPRINT_COST;
            case SUPER_LEAP -> MovementStaminaCosts.SUPER_LEAP_COST;
            case FLIGHT -> 0;
        };

        if (cost <= 0) return;
        if (!PlayerStaminaManager.hasStamina(player, cost)) return;

        PlayerStaminaManager.removeStamina(player, cost);
        StaminaServerTick.syncStamina(player);
    }

    public static boolean hasMovementMode(ServerPlayer player, MovementMode mode) {
        AbilityComponent abilities = AbilityComponents.ABILITIES.get(
                (ComponentProvider) player);

        return abilities.getUnlocked().stream()
                .anyMatch(id -> {
                    Ability ability = AbilityRegistry.get(id);
                    return ability instanceof MovementModeProvider provider
                            && ability.getType() == Ability.Type.PASSIVE
                            && provider.getGrantedModes().contains(mode);
                });
    }

    private MovementStaminaHandler() {}
}
package zcylas.totality.networking.ability;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.core.component.ComponentProvider;

public class EquipAbilityHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                EquipAbilityPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> handle(player, payload));
                }
        );
    }

    private static void handle(ServerPlayer player, EquipAbilityPayload payload) {
        AbilityComponent comp = AbilityComponents.ABILITIES.get(
                (ComponentProvider) player);

        if (payload.abilityId() == null) {
            comp.setEquippedAbility(null);
            return;
        }

        // Validate — must be unlocked and not passive
        if (!comp.hasAbility(payload.abilityId())) return;
        Ability ability = AbilityRegistry.get(payload.abilityId());
        if (ability == null) return;
        if (ability.getType() == Ability.Type.PASSIVE) return;

        comp.setEquippedAbility(payload.abilityId());
    }

    private EquipAbilityHandler() {}
}
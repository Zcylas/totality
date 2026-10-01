// networking/ability/ToggleAbilityHandler.java
package zcylas.totality.networking.ability;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.entitlement.EntitlementActions;
import zcylas.totality.api.entitlement.ProgressionContext;
import zcylas.totality.api.entitlement.integration.AbilityEntitlements;

public final class ToggleAbilityHandler {

    private ToggleAbilityHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                ToggleAbilityPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> handle(player, payload));
                }
        );
    }

    private static void handle(ServerPlayer player, ToggleAbilityPayload payload) {
        Ability ability = AbilityRegistry.get(payload.abilityId());
        if (ability == null) return;
        if (ability.getType() != Ability.Type.CHANNELED) return;
        // Starting a channel is a protected action; stopping one never needs authorization.
        if (payload.active() && !AbilityEntitlements.canUse(player, payload.abilityId(), EntitlementActions.ACTIVATE)) return;
        if (!ability.canActivate(player, null)) return;

        AbilityComponent comp = AbilityComponents.ABILITIES.get(
                (ComponentProvider) player);

        if (payload.active()) {
            if (comp.isChanneling()) return; // already channeling something
            comp.startChanneling(payload.abilityId());
            ProgressionContext.run(player, AbilityEntitlements.isDebugOnly(player, payload.abilityId()),
                    () -> ability.onChannelStart(player, null));
        } else {
            if (!comp.isChanneling(payload.abilityId())) return;
            comp.stopChanneling();
            ability.onChannelStop(player, null);
        }
    }
}
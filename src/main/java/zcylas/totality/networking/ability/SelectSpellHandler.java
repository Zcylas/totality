package zcylas.totality.networking.ability;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.entitlement.EntitlementActions;
import zcylas.totality.api.entitlement.integration.AbilityEntitlements;
import zcylas.totality.api.magic.spell.SpellRegistry;

public class SelectSpellHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                SelectSpellPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> handle(player, payload));
                }
        );
    }

    private static void handle(ServerPlayer player, SelectSpellPayload payload) {
        AbilityComponent comp = AbilityComponents.ABILITIES.get(
                (ComponentProvider) player);

        if (payload.spellId() == null) {
            comp.setSelectedSpell(null);
            return;
        }

        // Validate — must be selectable per the Entitlement API and actually be a spell
        if (!AbilityEntitlements.canUse(player, payload.spellId(), EntitlementActions.SELECT)) return;
        if (SpellRegistry.get(payload.spellId()) == null) return;

        comp.setSelectedSpell(payload.spellId());
    }

    private SelectSpellHandler() {}
}

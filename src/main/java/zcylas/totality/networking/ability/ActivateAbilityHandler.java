package zcylas.totality.networking.ability;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.core.component.ComponentProvider;

public class ActivateAbilityHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                ActivateAbilityPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> handle(player, payload));
                }
        );
    }

    private static void handle(ServerPlayer player, ActivateAbilityPayload payload) {
        AbilityComponent comp = AbilityComponents.ABILITIES.get(
                (ComponentProvider) player);

        if (!comp.hasAbility(payload.abilityId())) return;
        if (comp.isOnCooldown(payload.abilityId())) return;

        Ability ability = AbilityRegistry.get(payload.abilityId());
        if (ability == null) return;

        // Reconstruct context from the block pos the client sent
        AbilityContext context = null;
        if (payload.pos() != null) {
            BlockPos pos = payload.pos();
            BlockState state = player.level().getBlockState(pos);
            context = new AbilityContext(pos, state, ability.getDisplayName());
        }

        ability.onActivate(player, context);

        if (ability.getCooldownTicks() > 0) {
            comp.startCooldown(payload.abilityId());
        }
    }

    private ActivateAbilityHandler() {}
}
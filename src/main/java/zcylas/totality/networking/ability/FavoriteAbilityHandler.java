package zcylas.totality.networking.ability;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.core.component.ComponentProvider;

public class FavoriteAbilityHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                FavoriteAbilityPayload.TYPE,
                (payload, context) -> {
                    ServerPlayer player = context.player();
                    context.server().execute(() -> {
                        AbilityComponent comp = AbilityComponents.ABILITIES.get(
                                (ComponentProvider) player);
                        if (comp.hasAbility(payload.abilityId())) {
                            comp.toggleFavorite(payload.abilityId());
                        }
                    });
                }
        );
    }

    private FavoriteAbilityHandler() {}
}
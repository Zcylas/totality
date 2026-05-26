package zcylas.totality.api.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentProvider;

public class AbilityServerTick {

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                AbilityComponent comp = AbilityComponents.ABILITIES.get((ComponentProvider) player);

                comp.tickCooldowns();

                for (Identifier id : comp.getUnlocked()) {
                    Ability ability = AbilityRegistry.get(id);
                    if (ability == null) continue;

                    if (ability.getType() == Ability.Type.PASSIVE) {
                        ability.onPassiveTick(player);
                    }

                    if (ability.getType() == Ability.Type.CHANNELED) {
                        ability.onChannel(player, null);
                    }
                }
            }
        });
    }

    private AbilityServerTick() {}
}
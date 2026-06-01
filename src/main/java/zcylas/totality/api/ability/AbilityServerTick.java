package zcylas.totality.api.ability;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentProvider;

import java.util.Set;

public class AbilityServerTick {

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                AbilityComponent comp = AbilityComponents.ABILITIES.get((ComponentProvider) player);

                comp.tickCooldowns();

                // Tick passives
                for (Identifier id : comp.getUnlocked()) {
                    Ability ability = AbilityRegistry.get(id);
                    if (ability == null) continue;
                    if (ability.getType() == Ability.Type.PASSIVE) {
                        ability.onPassiveTick(player);
                    }
                }

                // Tick active toggles
                for (Identifier id : Set.copyOf(comp.getActiveToggles())) {
                    Ability ability = AbilityRegistry.get(id);
                    if (ability == null) { comp.deactivateToggle(id); continue; }

                    // Countdown timer
                    int remaining = comp.getToggleTimers().getOrDefault(id, 0) - 1;
                    if (remaining <= 0) {
                        ability.onToggleOff(player);
                        comp.deactivateToggle(id);
                        continue;
                    }
                    comp.getToggleTimers().put(id, remaining);

                    // Inactivity check (400 ticks)
                    int lastCombat = comp.getLastCombatTick().getOrDefault(id, 0);
                    if (player.tickCount - lastCombat > 400 && lastCombat != 0) {
                        ability.onToggleOff(player);
                        comp.deactivateToggle(id);
                        continue;
                    }

                    ability.onToggleTick(player);
                }

                // Tick active channel — only the currently channeling ability
                Identifier channelingId = comp.getChannelingAbility();
                if (channelingId != null) {
                    Ability channeled = AbilityRegistry.get(channelingId);
                    if (channeled != null) {
                        channeled.onChannel(player, null);
                    } else {
                        // Ability no longer exists — stop channeling
                        comp.stopChanneling();
                    }
                }


            }
        });
    }

    private AbilityServerTick() {}
}
package zcylas.totality.api.rpg.combat;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class CombatComponents {

    public static final ComponentKey<CombatStateComponent> COMBAT_STATE = ComponentRegistry.getOrCreate(
            Identifier.fromNamespaceAndPath("totality", "combat_state"),
            CombatStateComponent.class
    );

    private CombatComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                COMBAT_STATE,
                player -> new CombatStateComponent(
                        ((zcylas.totality.api.core.component.ComponentProvider) player).getComponentContainer()),
                RespawnStrategy.ALWAYS_COPY
        );
        // No client component needed — combat state is server-only
    }
}
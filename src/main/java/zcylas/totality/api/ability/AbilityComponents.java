package zcylas.totality.api.ability;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class AbilityComponents {

    public static final ComponentKey<AbilityComponent> ABILITIES = ComponentRegistry.getOrCreate(
            Identifier.fromNamespaceAndPath("totality", "abilities"),
            AbilityComponent.class
    );

    private AbilityComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                ABILITIES,
                AbilityComponent::new,
                RespawnStrategy.ALWAYS_COPY
        );
        PlayerComponentEvents.registerClientComponent(
                ABILITIES,
                () -> new AbilityComponent(null)
        );
    }
}
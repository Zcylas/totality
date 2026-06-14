package zcylas.totality.api.magic.spell;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class ConcentrationComponents {

    public static final ComponentKey<ConcentrationComponent> CONCENTRATION =
            ComponentRegistry.getOrCreate(
                    Identifier.fromNamespaceAndPath("totality", "concentration"),
                    ConcentrationComponent.class
            );

    private ConcentrationComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                CONCENTRATION,
                ConcentrationComponent::new,      // ServerPlayer → ConcentrationComponent
                RespawnStrategy.NEVER_COPY         // death always ends concentration
        );
        PlayerComponentEvents.registerClientComponent(
                CONCENTRATION,
                () -> new ConcentrationComponent(null)
        );
    }

    public static ConcentrationComponent get(ServerPlayer player) {
        return CONCENTRATION.get((ComponentProvider) player);
    }
}
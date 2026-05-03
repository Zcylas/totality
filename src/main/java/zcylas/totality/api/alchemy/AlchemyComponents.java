package zcylas.totality.api.alchemy;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.component.ComponentKey;
import zcylas.totality.api.component.ComponentRegistry;
import zcylas.totality.api.component.PlayerComponentEvents;
import zcylas.totality.api.component.RespawnStrategy;

public final class AlchemyComponents {

    public static final ComponentKey<AlchemyKnowledgeComponent> KNOWLEDGE = ComponentRegistry.getOrCreate(
            Identifier.fromNamespaceAndPath("totality", "alchemy_knowledge"),
            AlchemyKnowledgeComponent.class
    );

    private AlchemyComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                KNOWLEDGE,
                AlchemyKnowledgeComponent::new,
                RespawnStrategy.ALWAYS_COPY  // discovered effects persist through death
        );
        PlayerComponentEvents.registerClientComponent(
                KNOWLEDGE,
                () -> new AlchemyKnowledgeComponent(null)
        );
    }
}
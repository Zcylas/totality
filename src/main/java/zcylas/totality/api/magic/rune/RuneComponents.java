package zcylas.totality.api.magic.rune;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public final class RuneComponents {

    public static final ComponentKey<RuneKnowledgeComponent> KNOWLEDGE = ComponentRegistry.getOrCreate(
            Identifier.fromNamespaceAndPath("totality", "rune_knowledge"),
            RuneKnowledgeComponent.class
    );

    private RuneComponents() {}

    public static void register() {
        PlayerComponentEvents.registerForPlayers(
                KNOWLEDGE,
                RuneKnowledgeComponent::new,
                RespawnStrategy.ALWAYS_COPY  // learned runes persist through death
        );
        PlayerComponentEvents.registerClientComponent(
                KNOWLEDGE,
                () -> new RuneKnowledgeComponent(null)
        );
    }
}
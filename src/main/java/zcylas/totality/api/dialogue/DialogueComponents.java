package zcylas.totality.api.dialogue;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public class DialogueComponents {
    public static ComponentKey<NarrativeFlagsComponent> FLAGS;

    public static void register() {
        FLAGS = ComponentRegistry.getOrCreate(
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "narrative_flags"),
                NarrativeFlagsComponent.class
        );
        PlayerComponentEvents.registerForPlayers(
                FLAGS,
                player -> new NarrativeFlagsComponent(),
                RespawnStrategy.ALWAYS_COPY
        );
    }
}

package zcylas.totality.api.quest;

import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.core.component.ComponentKey;
import zcylas.totality.api.core.component.ComponentRegistry;
import zcylas.totality.api.core.component.PlayerComponentEvents;
import zcylas.totality.api.core.component.RespawnStrategy;

public class QuestComponents {
    public static ComponentKey<QuestProgressComponent> PROGRESS;

    public static void register() {
        PROGRESS = ComponentRegistry.getOrCreate(
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "quest_progress"),
                QuestProgressComponent.class
        );
        PlayerComponentEvents.registerForPlayers(
                PROGRESS,
                player -> new QuestProgressComponent(),
                RespawnStrategy.ALWAYS_COPY
        );
    }
}

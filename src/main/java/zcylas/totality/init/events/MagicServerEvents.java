package zcylas.totality.init.events;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.magic.grimoire.rune.RuneComponents;
import zcylas.totality.api.magic.grimoire.rune.RuneKnowledgeComponent;
import zcylas.totality.api.core.component.ComponentProvider;

public final class MagicServerEvents {

    private MagicServerEvents() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            RuneKnowledgeComponent knowledge = RuneComponents.KNOWLEDGE.get(
                    (ComponentProvider) player
            );
            if (knowledge.getKnownRunes().isEmpty()) {
                knowledge.learnRune("touch");
                knowledge.learnRune("projectile");
                knowledge.learnRune("self");
                knowledge.learnRune("break");
                knowledge.learnRune("harm");
                knowledge.sync();
            }
        });
    }
}
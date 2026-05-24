package zcylas.totality.init.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.api.rpg.combat.bow.BowStaminaHandler;
import zcylas.totality.api.rpg.combat.exhaustion.ExhaustionManager;
import zcylas.totality.networking.ability.veinminer.VeinminerKeyHandler;
import zcylas.totality.networking.ancestry.OpenAncestrySelectionPayload;

public class PlayerConnectionEvents {

    public static void register() {
        // ── Ancestry selection on first join ──────────────────────────────────
        ServerPlayerEvents.JOIN.register(player -> {
            if (!AncestryComponents.get(player).hasAncestry()) {
                ServerPlayNetworking.send(player, new OpenAncestrySelectionPayload());
            } else {
                AncestryComponents.get(player).sync();
                player.refreshDimensions();
            }
        });

        // ── Cleanup on disconnect ─────────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ExhaustionManager.onPlayerLeave(handler.player);
            BowStaminaHandler.onPlayerLeave(handler.player);
            VeinminerKeyHandler.onPlayerLeave(handler.player);
        });
    }

    private PlayerConnectionEvents() {}
}
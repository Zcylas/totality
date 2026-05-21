package zcylas.totality.init.events;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import zcylas.totality.api.rpg.combat.bow.BowStaminaHandler;
import zcylas.totality.api.rpg.combat.exhaustion.ExhaustionManager;
import zcylas.totality.api.rpg.race.RaceComponents;
import zcylas.totality.networking.ability.veinminer.VeinminerKeyHandler;
import zcylas.totality.networking.race.OpenRaceSelectionPayload;

public class PlayerConnectionEvents {

    public static void register() {
        // ── Race selection on first join ──────────────────────────────────────
        ServerPlayerEvents.JOIN.register(player -> {
            if (!RaceComponents.get(player).hasRace()) {
                ServerPlayNetworking.send(player, new OpenRaceSelectionPayload());
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
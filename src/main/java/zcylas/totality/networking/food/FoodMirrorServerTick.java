package zcylas.totality.networking.food;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.resources.food.FoodVanillaCompatibilityBridge;

/**
 * The universal safety net closing the "stale mirror" gap the 2026-09-17 Food correction pass fixes:
 * an authoritative {@code totality:food} mutation that never passes through a vanilla call site
 * (a {@code TotalityFoodItem} eating, {@code /totality food set}, or any future Generic Food caller)
 * would otherwise never refresh vanilla's own {@code foodLevel} compatibility mirror. Rather than
 * scattering a manual {@code syncMirror()} call into Pizza and the debug command — which would leave
 * every future caller unsafe, exactly what this correction pass rejects — this runs once per player
 * every server tick and self-heals any drift, following the same established
 * {@code ServerTickEvents.END_SERVER_TICK}-per-player idiom {@code ManaServerTick}/
 * {@code StaminaServerTick} already use for their own per-tick bookkeeping. Cheap when nothing
 * changed: one Resource query and one int comparison per player; a {@code setFoodLevel} call only
 * happens when the mirror has actually drifted. See {@link FoodVanillaCompatibilityBridge
 * #resyncMirrorIfStale} for the one-directional (authoritative -> mirror, never the reverse) math
 * this delegates to — it can never create a feedback loop and never touches the authoritative value.
 */
public final class FoodMirrorServerTick {

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                FoodVanillaCompatibilityBridge.resyncMirrorIfStale(player);
            }
        });
    }

    private FoodMirrorServerTick() {}
}

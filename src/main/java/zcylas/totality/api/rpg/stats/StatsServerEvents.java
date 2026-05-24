package zcylas.totality.api.rpg.stats;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Registers server-side events for the RPG stats system.
 *
 * On join:
 *   1. Recalculate all stat-driven modifiers (CON → HP etc.)
 *   2. Restore saved stamina, mana and HP values
 *   3. Sync everything to client
 *
 * On disconnect:
 *   1. Save current stamina, mana and HP into PlayerStatsComponent
 *
 * On respawn:
 *   1. Recalculate all stat-driven modifiers
 *   2. Restore to full resources
 *   3. Sync everything to client
 */
public final class StatsServerEvents {

    private StatsServerEvents() {}

    public static void register() {

        // ── On join ───────────────────────────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerResourceRecalculator.recalculateAndRestore(handler.player);
        });

        // ── On disconnect ─────────────────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            StatsComponents.get(handler.player).saveCurrentResources();
        });

        // ── On respawn ────────────────────────────────────────────────────────
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            PlayerResourceRecalculator.recalculateAndRestore(newPlayer);
        });
    }
}
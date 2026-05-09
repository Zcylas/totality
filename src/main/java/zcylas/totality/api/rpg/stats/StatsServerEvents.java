package zcylas.totality.api.rpg.stats;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import zcylas.totality.api.rpg.mana.PlayerManaManager;
import zcylas.totality.networking.mana.SyncManaPayload;
import zcylas.totality.networking.stamina.StaminaServerTick;

/**
 * Registers server-side events for the RPG stats system.
 *
 * On join:
 *   1. Apply stat-driven attribute modifiers (CON → HP etc.)
 *   2. Restore saved stamina, mana and HP values
 *   3. Sync stamina and mana to client
 *
 * On disconnect:
 *   1. Save current stamina, mana and HP into PlayerStatsComponent
 *      so they persist across world reloads
 *
 * On respawn:
 *   1. Reapply stat modifiers
 *   2. Restore to full resources (copyFrom sets saved values to -1)
 */
public final class StatsServerEvents {

    private StatsServerEvents() {}

    public static void register() {

        // ── On join — apply modifiers then restore resources ──────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;
            var component = StatsComponents.get(player);

            // 1. Apply CON → MAX_HEALTH and other stat modifiers
            StatAttributeApplier.apply(player);

            // 2. Restore saved stamina, mana and HP (clamped to new max)
            component.restoreResources();

            // 3. Sync stamina and mana to client
            StaminaServerTick.syncStamina(player);
            ServerPlayNetworking.send(player, new SyncManaPayload(
                    PlayerManaManager.getMana(player),
                    PlayerManaManager.getMaxMana(player)));
        });

        // ── On disconnect — save current resource values ───────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.player;
            var component = StatsComponents.get(player);

            // Save current stamina, mana and HP into component
            // writeData will persist them to disk via MixinServerPlayer
            component.saveCurrentResources();
        });

        // ── On respawn — reapply modifiers, restore to full ───────────────────
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            // Stats already copied by RespawnStrategy.ALWAYS_COPY
            // savedStamina/Mana/Hp set to -1 in copyFrom → restoreResources gives full
            StatAttributeApplier.apply(newPlayer);
            StatsComponents.get(newPlayer).restoreResources();

            // Sync to client
            StaminaServerTick.syncStamina(newPlayer);
            ServerPlayNetworking.send(newPlayer, new SyncManaPayload(
                    PlayerManaManager.getMana(newPlayer),
                    PlayerManaManager.getMaxMana(newPlayer)));
        });
    }
}
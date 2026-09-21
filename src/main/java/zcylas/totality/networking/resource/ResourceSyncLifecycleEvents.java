package zcylas.totality.networking.resource;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Schedules a final authoritative full Resource snapshot at every lifecycle point the Phase 3A
 * contract requires, and clears server-side sync state on disconnect. Registered last within
 * {@code ModEvents.register()} so join/respawn snapshots are scheduled only after every other
 * listener (component attach, recalculation, class/Rage repair) has already run for that event —
 * the actual send happens at the next {@code ResourceSyncManager.flush}, i.e. end of the current
 * tick, by which point that settled state is what gets queried. This class only schedules
 * synchronization; it never mutates, restores, or initializes any Resource itself.
 */
public final class ResourceSyncLifecycleEvents {

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ResourceSyncManager.scheduleFullSnapshot(handler.getPlayer().getUUID()));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                ResourceSyncManager.scheduleFullSnapshot(newPlayer.getUUID()));

        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
                ResourceSyncManager.scheduleFullSnapshot(player.getUUID()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ResourceSyncManager.onDisconnect(handler.player.getUUID());
            ResourceResyncRequestHandler.clearRateLimit(handler.player.getUUID());
        });
    }

    private ResourceSyncLifecycleEvents() {}
}

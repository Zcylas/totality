package zcylas.totality.networking.resource;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Registers the single END_SERVER_TICK flush for {@link ResourceSyncManager} — one orchestration
 *  loop for the whole generic Resource sync contract, not one loop per resource. */
public final class ResourceSyncServerTick {

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ResourceSyncManager::flush);
    }

    private ResourceSyncServerTick() {}
}

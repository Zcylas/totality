package zcylas.totality.client.camera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import zcylas.totality.client.photo.Gallery;
import zcylas.totality.client.photo.PhotoTextures;

/** Client lifecycle for the Camera and Gallery apps: per-tick checks, texture release, and clean-up on leaving a world. */
public final class CameraClient {

    private CameraClient() {}

    public static void register() {
        // Before vanilla's key handling in the same tick, so no queued click ever becomes a gameplay action.
        ClientTickEvents.START_CLIENT_TICK.register(CameraSession::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> PhotoTextures.tick());
        // Leaving a world/server: the viewfinder, the open Gallery and their GPU resources go; the files stay.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            CameraSession.reset();
            Gallery.closeCurrent();
        });
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> CameraSession.reset());
    }
}

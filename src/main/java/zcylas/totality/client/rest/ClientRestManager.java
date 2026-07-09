package zcylas.totality.client.rest;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import zcylas.totality.api.rpg.rest.RestType;
import zcylas.totality.networking.rest.RestTimeSyncPayload;

/** Client-side mirror of the server's active RestSession, updated by RestTimeSyncPayload. */
public final class ClientRestManager {

    private static boolean active = false;
    private static boolean inGrace = false;
    private static RestType type = RestType.SHORT;
    private static int remainingTicks = 0;
    private static int graceRemainingTicks = 0;
    private static boolean lyingDown = false;

    /** Whatever the player had their camera set to before we forced third-person for lyingDown. */
    private static CameraType savedCameraType = null;

    public static void handle(RestTimeSyncPayload payload) {
        boolean wasLyingDown = lyingDown;
        active = payload.active();
        inGrace = payload.inGrace();
        type = payload.restType();
        remainingTicks = payload.remainingTicks();
        graceRemainingTicks = payload.graceRemainingTicks();
        lyingDown = payload.lyingDown();

        // Our forced Pose.SLEEPING (used whenever we can't ride out a genuine vanilla sleep —
        // see RestSessionManager's class doc) drops the entity's eye height to almost nothing.
        // Real vanilla sleep avoids ever looking broken in first person by locking the camera
        // to third-person on its own; we have to do the same manually since we deliberately
        // never touch isSleeping().
        Minecraft client = Minecraft.getInstance();
        if (lyingDown && !wasLyingDown) {
            savedCameraType = client.options.getCameraType();
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!lyingDown && wasLyingDown && savedCameraType != null) {
            client.options.setCameraType(savedCameraType);
            savedCameraType = null;
        }
    }

    public static boolean isActive() { return active; }
    public static boolean isInGrace() { return inGrace; }
    public static RestType getType() { return type; }
    public static int getRemainingTicks() { return remainingTicks; }
    public static int getGraceRemainingTicks() { return graceRemainingTicks; }
    public static boolean isLyingDown() { return lyingDown; }

    /**
     * Called on joining a world/server. A fresh join never gets an explicit "cleared" sync — the
     * server only sends RestTimeSyncPayload when a rest actually starts/changes — so without this,
     * rejoining mid-way through what used to be an active rest leaves this HUD-driving state (and
     * a still-forced third-person camera) stuck from the previous session forever.
     */
    public static void reset() {
        active = false;
        inGrace = false;
        remainingTicks = 0;
        graceRemainingTicks = 0;
        if (lyingDown && savedCameraType != null) {
            Minecraft.getInstance().options.setCameraType(savedCameraType);
        }
        lyingDown = false;
        savedCameraType = null;
    }

    private ClientRestManager() {}
}

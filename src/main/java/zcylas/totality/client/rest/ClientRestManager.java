package zcylas.totality.client.rest;

import zcylas.totality.api.rpg.rest.RestType;
import zcylas.totality.networking.rest.RestTimeSyncPayload;

/**
 * Client-side mirror of the server's active RestSession, updated by RestTimeSyncPayload. Purely
 * a HUD data source now — every rest type rides real vanilla sleep server-side, so vanilla's own
 * client code already forces third-person and the "look up" sleep camera on its own.
 */
public final class ClientRestManager {

    private static boolean active = false;
    private static boolean inGrace = false;
    private static RestType type = RestType.SHORT;
    private static int remainingTicks = 0;
    private static int graceRemainingTicks = 0;

    public static void handle(RestTimeSyncPayload payload) {
        active = payload.active();
        inGrace = payload.inGrace();
        type = payload.restType();
        remainingTicks = payload.remainingTicks();
        graceRemainingTicks = payload.graceRemainingTicks();
    }

    public static boolean isActive() { return active; }
    public static boolean isInGrace() { return inGrace; }
    public static RestType getType() { return type; }
    public static int getRemainingTicks() { return remainingTicks; }
    public static int getGraceRemainingTicks() { return graceRemainingTicks; }

    /**
     * Called on joining a world/server. A fresh join never gets an explicit "cleared" sync — the
     * server only sends RestTimeSyncPayload when a rest actually starts/changes — so without this,
     * rejoining mid-way through what used to be an active rest leaves this HUD state stuck from
     * the previous session forever.
     */
    public static void reset() {
        active = false;
        inGrace = false;
        remainingTicks = 0;
        graceRemainingTicks = 0;
    }

    private ClientRestManager() {}
}

package zcylas.totality.api.rpg.rest;

import net.minecraft.server.level.ServerPlayer;

/**
 * Resolves rest durations, expressed in Minecraft time (1 MC hour = 1000 ticks).
 */
public final class RestDurations {

    /** 8 MC hours — the real 5e Long Rest figure. */
    public static final int DEFAULT_LONG_REST_TICKS = 8000;

    /** 1 MC hour — matches the real 5e "under 1 hour of interruption is forgiven" rule. */
    public static final int GRACE_WINDOW_TICKS = 1000;

    public static int getLongRestTicks(ServerPlayer player) {
        return AncestryRestOverrides.getLongRestTicks(player);
    }

    private RestDurations() {}
}

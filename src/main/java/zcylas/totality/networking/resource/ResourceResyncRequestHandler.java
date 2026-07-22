package zcylas.totality.networking.resource;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import zcylas.totality.api.rpg.resources.sync.ResourceResyncRateLimiter;

import java.util.UUID;

/**
 * Server-side handler for {@link ResourceResyncRequestPayload}. Always resolves against
 * {@code context.player()} — the sending connection's own player — so this path structurally
 * cannot read or affect another player's Resource view. Rate-limited so a hostile/broken client
 * cannot force repeated full-snapshot generation.
 */
public final class ResourceResyncRequestHandler {

    private static final long MIN_INTERVAL_TICKS = 100; // 5 seconds at 20 TPS

    private static final ResourceResyncRateLimiter LIMITER = new ResourceResyncRateLimiter(MIN_INTERVAL_TICKS);

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ResourceResyncRequestPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    var player = context.player();
                    long tick = player.level().getServer().getTickCount();
                    if (LIMITER.tryAcquire(player.getUUID(), tick)) {
                        ResourceSyncManager.scheduleFullSnapshot(player.getUUID());
                    }
                }));
    }

    public static void clearRateLimit(UUID playerId) {
        LIMITER.clear(playerId);
    }

    private ResourceResyncRequestHandler() {}
}

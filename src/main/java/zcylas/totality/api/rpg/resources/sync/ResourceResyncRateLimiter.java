package zcylas.totality.api.rpg.resources.sync;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pure per-player minimum-interval rate limiter for the serverbound resync-request path. Takes an
 * explicit tick value rather than reading a clock itself, so it is unit-testable without a running
 * server.
 */
public final class ResourceResyncRateLimiter {

    private final long minIntervalTicks;
    private final Map<UUID, Long> lastAcceptedTick = new HashMap<>();

    public ResourceResyncRateLimiter(long minIntervalTicks) {
        if (minIntervalTicks < 0) {
            throw new IllegalArgumentException("minIntervalTicks must be >= 0");
        }
        this.minIntervalTicks = minIntervalTicks;
    }

    /** Returns {@code true} and records the acceptance if {@code playerId} has not been accepted
     *  within the configured interval; otherwise returns {@code false} without side effects. */
    public boolean tryAcquire(UUID playerId, long currentTick) {
        Long last = lastAcceptedTick.get(playerId);
        if (last != null && currentTick - last < minIntervalTicks) {
            return false;
        }
        lastAcceptedTick.put(playerId, currentTick);
        return true;
    }

    public void clear(UUID playerId) {
        lastAcceptedTick.remove(playerId);
    }
}

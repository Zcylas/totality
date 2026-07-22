package zcylas.totality.api.rpg.resources.sync;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResourceResyncRateLimiterTest {

    private static final UUID PLAYER_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_B = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void firstRequestIsAlwaysAccepted() {
        ResourceResyncRateLimiter limiter = new ResourceResyncRateLimiter(100);
        assertTrue(limiter.tryAcquire(PLAYER_A, 0));
    }

    @Test
    void secondRequestWithinIntervalIsRejected() {
        ResourceResyncRateLimiter limiter = new ResourceResyncRateLimiter(100);
        assertTrue(limiter.tryAcquire(PLAYER_A, 0));
        assertFalse(limiter.tryAcquire(PLAYER_A, 50));
    }

    @Test
    void requestAfterIntervalElapsedIsAccepted() {
        ResourceResyncRateLimiter limiter = new ResourceResyncRateLimiter(100);
        assertTrue(limiter.tryAcquire(PLAYER_A, 0));
        assertTrue(limiter.tryAcquire(PLAYER_A, 100));
    }

    @Test
    void limitIsPerPlayer() {
        ResourceResyncRateLimiter limiter = new ResourceResyncRateLimiter(100);
        assertTrue(limiter.tryAcquire(PLAYER_A, 0));
        assertTrue(limiter.tryAcquire(PLAYER_B, 1), "a different player must not be throttled by A's request");
    }

    @Test
    void clearingAPlayerResetsTheirThrottle() {
        ResourceResyncRateLimiter limiter = new ResourceResyncRateLimiter(100);
        assertTrue(limiter.tryAcquire(PLAYER_A, 0));
        limiter.clear(PLAYER_A);
        assertTrue(limiter.tryAcquire(PLAYER_A, 1));
    }
}

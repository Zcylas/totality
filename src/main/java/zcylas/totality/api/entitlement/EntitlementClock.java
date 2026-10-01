package zcylas.totality.api.entitlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The three expiry clocks (canonical §3.5) sampled at one instant. {@code onlineTicks} is the
 * player's own persisted online-tick counter, so an {@link Clock#ONLINE_TICKS} expiry pauses while
 * the player is offline; {@link Clock#REAL_TIME_UTC} keeps running.
 */
public record EntitlementClock(long onlineTicks, long gameTime, long utcMillis) {

    public enum Clock {
        ONLINE_TICKS,
        SERVER_GAME_TIME,
        REAL_TIME_UTC
    }

    /** A point on one clock after which a grant or suspension stops applying. */
    public record Expiry(Clock clock, long expiresAt) {

        public static final Codec<Expiry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.xmap(Clock::valueOf, Clock::name).fieldOf("clock").forGetter(Expiry::clock),
                Codec.LONG.fieldOf("expires_at").forGetter(Expiry::expiresAt)
        ).apply(i, Expiry::new));

        public boolean isExpired(EntitlementClock now) {
            long current = switch (clock) {
                case ONLINE_TICKS -> now.onlineTicks();
                case SERVER_GAME_TIME -> now.gameTime();
                case REAL_TIME_UTC -> now.utcMillis();
            };
            return current >= expiresAt;
        }
    }
}

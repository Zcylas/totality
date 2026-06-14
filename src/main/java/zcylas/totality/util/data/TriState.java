package zcylas.totality.util.data;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;

/**
 * A three-state boolean: {@link #TRUE}, {@link #FALSE}, or {@link #UNDEFINED}.
 *
 * <p>{@code UNDEFINED} means "no opinion / use the default" — useful wherever a
 * tri-state override is needed, e.g. advantage/disadvantage checks in
 * {@code AbilityCheckResolver}, forced pass/fail ability gates, or optional
 * config overrides.</p>
 *
 * <p>Ported from ResourcefulLib (TeamResourceful). ByteCodec dependency removed.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * TriState advantage = resolver.getAdvantage(player, check);
 * boolean result = advantage.map(defaultRoll); // use default if UNDEFINED
 * }</pre>
 */
public enum TriState {
    TRUE,
    FALSE,
    UNDEFINED;

    public static final Codec<TriState> CODEC = Codec.STRING.xmap(
            s -> TriState.valueOf(s.toUpperCase(Locale.ROOT)),
            t -> t.name().toLowerCase(Locale.ROOT)
    );

    public static final StreamCodec<ByteBuf, TriState> STREAM_CODEC = ByteBufCodecs.VAR_INT
            .map(i -> TriState.values()[i], TriState::ordinal);

    // -----------------------------------------------------------------------
    // Factories
    // -----------------------------------------------------------------------

    public static TriState of(boolean value) {
        return value ? TRUE : FALSE;
    }

    public static TriState of(Boolean value) {
        return value == null ? UNDEFINED : of(value.booleanValue());
    }

    /**
     * Map a Number to a TriState: 0 → FALSE, 1 → TRUE, null or ≥2 → UNDEFINED.
     */
    public static TriState of(Number number) {
        if (number == null || number.longValue() >= 2) return UNDEFINED;
        return of(number.longValue() != 0);
    }

    // -----------------------------------------------------------------------
    // Queries
    // -----------------------------------------------------------------------

    public boolean isTrue()      { return this == TRUE;      }
    public boolean isFalse()     { return this == FALSE;     }
    public boolean isUndefined() { return this == UNDEFINED; }
    public boolean isDefined()   { return this != UNDEFINED; }

    /**
     * Resolve to a boolean: if this state is {@link #UNDEFINED}, return {@code defaultValue};
     * otherwise return whether this is {@link #TRUE}.
     */
    public boolean map(boolean defaultValue) {
        return this == UNDEFINED ? defaultValue : this == TRUE;
    }

    /**
     * If {@code state} is null or {@link #UNDEFINED}, return {@code fallback}; otherwise return {@code state}.
     */
    public static TriState map(TriState state, TriState fallback) {
        return state == null || state == UNDEFINED ? fallback : state;
    }
}

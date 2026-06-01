// api/dice/RollOutcome.java
package zcylas.totality.api.dice;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Outcome of a dice roll check.
 * CRITICAL_SUCCESS = natural max (e.g. nat 20 on d20).
 * CRITICAL_FAILURE = natural 1 (d20 only).
 */
public enum RollOutcome {
    CRITICAL_SUCCESS,
    SUCCESS,
    FAILURE,
    CRITICAL_FAILURE;

    public boolean isSuccess() {
        return this == CRITICAL_SUCCESS || this == SUCCESS;
    }

    /** Alias for isSuccess() — reads more naturally in attack roll context. */
    public boolean isHit() {
        return isSuccess();
    }

    public static final StreamCodec<FriendlyByteBuf, RollOutcome> STREAM_CODEC =
            StreamCodec.of(
                    (buf, r) -> buf.writeInt(r.ordinal()),
                    buf -> values()[buf.readInt()]
            );
}
// api/dice/RollType.java
package zcylas.totality.api.dice;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Whether this roll uses advantage, disadvantage, or is a straight roll. */
public enum RollType {
    NORMAL,
    ADVANTAGE,
    DISADVANTAGE;

    public static final StreamCodec<FriendlyByteBuf, RollType> STREAM_CODEC =
            StreamCodec.of(
                    (buf, r) -> buf.writeInt(r.ordinal()),
                    buf -> values()[buf.readInt()]
            );
}
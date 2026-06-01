// api/dice/Dice.java
package zcylas.totality.api.dice;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;

/**
 * Dice types available in Totality's dice roll system.
 * D2 is a coin flip. D100 is a percentile roll.
 */
public enum Dice {
    D2(2),
    D4(4),
    D6(6),
    D8(8),
    D10(10),
    D12(12),
    D20(20),
    D100(100);

    private final int sides;

    Dice(int sides) { this.sides = sides; }

    public int getSides() { return sides; }

    /** Roll this die using the given random source. Returns 1–sides. */
    public int roll(RandomSource random) {
        return random.nextInt(sides) + 1;
    }

    /** Display label, e.g. "d20". */
    public String getLabel() { return "d" + sides; }

    public static final StreamCodec<FriendlyByteBuf, Dice> STREAM_CODEC =
            StreamCodec.of(
                    (buf, d) -> buf.writeInt(d.ordinal()),
                    buf -> values()[buf.readInt()]
            );
}
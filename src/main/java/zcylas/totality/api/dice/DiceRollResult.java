// api/dice/DiceRollResult.java
package zcylas.totality.api.dice;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The result of a completed dice roll.
 * roll2 is -1 for NORMAL rolls (no advantage/disadvantage).
 */
public record DiceRollResult(
        DiceRollContext context,
        int             roll1,       // first die value
        int             roll2,       // second die value (-1 if NORMAL)
        int             usedRoll,    // roll that actually counts
        int             totalBonus,  // sum of all DiceBonus values
        int             total,       // usedRoll + totalBonus
        RollOutcome     outcome
) {
    public boolean hasSecondDie() { return roll2 != -1; }

    public static final StreamCodec<RegistryFriendlyByteBuf, DiceRollResult> STREAM_CODEC =
            StreamCodec.of(
                    (buf, r) -> {
                        DiceRollContext.STREAM_CODEC.encode(buf, r.context());
                        buf.writeInt(r.roll1());
                        buf.writeInt(r.roll2());
                        buf.writeInt(r.usedRoll());
                        buf.writeInt(r.totalBonus());
                        buf.writeInt(r.total());
                        RollOutcome.STREAM_CODEC.encode(buf, r.outcome());
                    },
                    buf -> {
                        DiceRollContext context    = DiceRollContext.STREAM_CODEC.decode(buf);
                        int             roll1      = buf.readInt();
                        int             roll2      = buf.readInt();
                        int             usedRoll   = buf.readInt();
                        int             totalBonus = buf.readInt();
                        int             total      = buf.readInt();
                        RollOutcome     outcome    = RollOutcome.STREAM_CODEC.decode(buf);
                        return new DiceRollResult(context, roll1, roll2, usedRoll, totalBonus, total, outcome);
                    }
            );
}
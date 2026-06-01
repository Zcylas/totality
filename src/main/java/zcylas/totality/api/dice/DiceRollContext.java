// api/dice/DiceRollContext.java
package zcylas.totality.api.dice;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes a pending dice roll: what's being rolled, the DC, the bonuses.
 * Sent from server to client before the player clicks to roll.
 */
public record DiceRollContext(
        String   checkName,    // e.g. "Deception"
        String   checkSubtype, // e.g. "Charisma Check"
        Dice     dice,         // usually D20
        int      dc,           // difficulty class
        RollType rollType,     // NORMAL, ADVANTAGE, DISADVANTAGE
        List<DiceBonus> bonuses
) {
    /** Total of all flat bonuses. */
    public int totalBonus() {
        return bonuses.stream().mapToInt(DiceBonus::value).sum();
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DiceRollContext> STREAM_CODEC =
            StreamCodec.of(
                    (buf, ctx) -> {
                        buf.writeUtf(ctx.checkName());
                        buf.writeUtf(ctx.checkSubtype());
                        Dice.STREAM_CODEC.encode(buf, ctx.dice());
                        buf.writeInt(ctx.dc());
                        RollType.STREAM_CODEC.encode(buf, ctx.rollType());
                        buf.writeInt(ctx.bonuses().size());
                        for (DiceBonus b : ctx.bonuses()) DiceBonus.STREAM_CODEC.encode(buf, b);
                    },
                    buf -> {
                        String   checkName    = buf.readUtf();
                        String   checkSubtype = buf.readUtf();
                        Dice     dice         = Dice.STREAM_CODEC.decode(buf);
                        int      dc           = buf.readInt();
                        RollType rollType     = RollType.STREAM_CODEC.decode(buf);
                        int      bonusCount   = buf.readInt();
                        List<DiceBonus> bonuses = new ArrayList<>(bonusCount);
                        for (int i = 0; i < bonusCount; i++) bonuses.add(DiceBonus.STREAM_CODEC.decode(buf));
                        return new DiceRollContext(checkName, checkSubtype, dice, dc, rollType, bonuses);
                    }
            );
}
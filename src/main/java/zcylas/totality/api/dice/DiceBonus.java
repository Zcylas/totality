// api/dice/DiceBonus.java
package zcylas.totality.api.dice;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * A single modifier contributing to a dice roll.
 * e.g. "+4 Charisma", "+3 Proficiency", "+1d4 Guidance"
 *
 * iconId is optional — used to display the source icon in the bonus card.
 */
public record DiceBonus(String label, int value, @Nullable String iconId) {

    /** Convenience — no icon. */
    public DiceBonus(String label, int value) {
        this(label, value, null);
    }

    /** Signed display string, e.g. "+4" or "-1". */
    public String valueString() {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DiceBonus> STREAM_CODEC =
            StreamCodec.of(
                    (buf, b) -> {
                        buf.writeUtf(b.label());
                        buf.writeInt(b.value());
                        buf.writeBoolean(b.iconId() != null);
                        if (b.iconId() != null) buf.writeUtf(b.iconId());
                    },
                    buf -> {
                        String label  = buf.readUtf();
                        int    value  = buf.readInt();
                        String iconId = buf.readBoolean() ? buf.readUtf() : null;
                        return new DiceBonus(label, value, iconId);
                    }
            );
}
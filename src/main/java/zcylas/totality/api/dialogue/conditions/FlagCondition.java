package zcylas.totality.api.dialogue.conditions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dialogue.DialogueCondition;
import zcylas.totality.api.dialogue.NarrativeFlagsComponent;

public record FlagCondition(String flag, int min, int max) implements DialogueCondition {
    public static final MapCodec<FlagCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("flag").forGetter(FlagCondition::flag),
            Codec.INT.optionalFieldOf("min", 1).forGetter(FlagCondition::min),
            Codec.INT.optionalFieldOf("max", Integer.MAX_VALUE).forGetter(FlagCondition::max)
    ).apply(i, FlagCondition::new));

    @Override
    public boolean test(ServerPlayer player, NarrativeFlagsComponent flags) {
        int value = flags.getFlag(flag);
        return value >= min && value <= max;
    }

    @Override
    public String type() { return "flag"; }
}

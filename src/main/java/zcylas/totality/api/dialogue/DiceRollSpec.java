package zcylas.totality.api.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DiceRollSpec(
        String skill,
        String subtype,
        int dc,
        String success,
        String failure
) {
    public static final Codec<DiceRollSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("skill").forGetter(DiceRollSpec::skill),
            Codec.STRING.optionalFieldOf("subtype", "").forGetter(DiceRollSpec::subtype),
            Codec.INT.fieldOf("dc").forGetter(DiceRollSpec::dc),
            Codec.STRING.fieldOf("success").forGetter(DiceRollSpec::success),
            Codec.STRING.fieldOf("failure").forGetter(DiceRollSpec::failure)
    ).apply(i, DiceRollSpec::new));
}

package zcylas.totality.api.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import zcylas.totality.api.dice.RollOutcome;

import java.util.Optional;

public record DiceRollSpec(
        String skill,
        String subtype,
        int dc,
        String success,
        String failure,
        Optional<String> criticalSuccess,
        Optional<String> criticalFailure,
        Optional<String> ability
) {
    public static final Codec<DiceRollSpec> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("skill").forGetter(DiceRollSpec::skill),
            Codec.STRING.optionalFieldOf("subtype", "").forGetter(DiceRollSpec::subtype),
            Codec.INT.fieldOf("dc").forGetter(DiceRollSpec::dc),
            Codec.STRING.fieldOf("success").forGetter(DiceRollSpec::success),
            Codec.STRING.fieldOf("failure").forGetter(DiceRollSpec::failure),
            Codec.STRING.optionalFieldOf("critical_success").forGetter(DiceRollSpec::criticalSuccess),
            Codec.STRING.optionalFieldOf("critical_failure").forGetter(DiceRollSpec::criticalFailure),
            // Overrides the skill's default governing ability (see SkillAbilityMap) — e.g. a
            // Barbarian's Intimidation check using STR instead of the usual CHA.
            Codec.STRING.optionalFieldOf("ability").forGetter(DiceRollSpec::ability)
    ).apply(i, DiceRollSpec::new));

    /** Picks the next dialogue state key for the given outcome, falling back to the plain
     *  success/failure node when a dialogue doesn't define a dedicated critical branch. */
    public String resolveNext(RollOutcome outcome) {
        return switch (outcome) {
            case CRITICAL_SUCCESS -> criticalSuccess.orElse(success);
            case SUCCESS -> success;
            case FAILURE -> failure;
            case CRITICAL_FAILURE -> criticalFailure.orElse(failure);
        };
    }
}

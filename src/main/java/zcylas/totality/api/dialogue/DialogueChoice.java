package zcylas.totality.api.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;
import java.util.Optional;

public record DialogueChoice(
        Component text,
        Optional<String> next,
        Optional<DiceRollSpec> roll,
        List<DialogueCondition> conditions,
        Optional<DialogueAction> action,
        boolean hidden
) {
    public static final Codec<DialogueChoice> CODEC = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("text").forGetter(DialogueChoice::text),
            Codec.STRING.optionalFieldOf("next").forGetter(DialogueChoice::next),
            DiceRollSpec.CODEC.optionalFieldOf("roll").forGetter(DialogueChoice::roll),
            DialogueCondition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(DialogueChoice::conditions),
            DialogueAction.CODEC.optionalFieldOf("action").forGetter(DialogueChoice::action),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(DialogueChoice::hidden)
    ).apply(i, DialogueChoice::new));

    public boolean hasRoll() { return roll.isPresent(); }
}

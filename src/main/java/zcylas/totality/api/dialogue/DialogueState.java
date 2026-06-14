package zcylas.totality.api.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.List;

public record DialogueState(
        Component text,
        StateType type,
        List<DialogueChoice> choices
) {
    public static final Codec<DialogueState> CODEC = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.fieldOf("text").forGetter(DialogueState::text),
            StateType.CODEC.optionalFieldOf("type", StateType.DEFAULT).forGetter(DialogueState::type),
            DialogueChoice.CODEC.listOf().optionalFieldOf("choices", List.of()).forGetter(DialogueState::choices)
    ).apply(i, DialogueState::new));
}

package zcylas.totality.api.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public record DialogueTemplate(
        String start,
        boolean unskippable,
        Map<String, DialogueState> states
) {
    public static final Codec<DialogueTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("start_at").forGetter(DialogueTemplate::start),
            Codec.BOOL.optionalFieldOf("unskippable", false).forGetter(DialogueTemplate::unskippable),
            Codec.unboundedMap(Codec.STRING, DialogueState.CODEC).fieldOf("states").forGetter(DialogueTemplate::states)
    ).apply(i, DialogueTemplate::new));

    public @Nullable DialogueState startState() {
        return states.get(start);
    }

    public @Nullable DialogueState getState(String key) {
        return states.get(key);
    }
}

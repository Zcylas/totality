package zcylas.totality.api.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record QuestTemplate(
        String name,
        String description,
        QuestType type,
        List<String> objectives,
        QuestReward reward,
        List<String> resetFlags
) {
    public static final Codec<QuestTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(QuestTemplate::name),
            Codec.STRING.fieldOf("description").forGetter(QuestTemplate::description),
            QuestType.CODEC.fieldOf("type").forGetter(QuestTemplate::type),
            Codec.STRING.listOf().fieldOf("objectives").forGetter(QuestTemplate::objectives),
            QuestReward.CODEC.fieldOf("reward").forGetter(QuestTemplate::reward),
            Codec.STRING.listOf().optionalFieldOf("reset_flags", List.of()).forGetter(QuestTemplate::resetFlags)
    ).apply(i, QuestTemplate::new));
}

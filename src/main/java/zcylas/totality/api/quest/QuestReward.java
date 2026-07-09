package zcylas.totality.api.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record QuestReward(long credits, int xp) {
    public static final Codec<QuestReward> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("credits", 0L).forGetter(QuestReward::credits),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(QuestReward::xp)
    ).apply(i, QuestReward::new));
}

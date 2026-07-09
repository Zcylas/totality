package zcylas.totality.api.quest;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum QuestType implements StringRepresentable {
    STARTER, DAILY, STANDARD;

    public static final Codec<QuestType> CODEC = StringRepresentable.fromEnum(QuestType::values);

    public String label() {
        return switch (this) {
            case STARTER -> "Starter Quest";
            case DAILY -> "Daily Quest";
            case STANDARD -> "Quest";
        };
    }

    @Override
    public String getSerializedName() { return name().toLowerCase(); }
}

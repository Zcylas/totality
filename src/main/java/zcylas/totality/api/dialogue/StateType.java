package zcylas.totality.api.dialogue;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum StateType implements StringRepresentable {
    DEFAULT("default"),
    END("end");

    public static final Codec<StateType> CODEC = StringRepresentable.fromEnum(StateType::values);

    private final String name;
    StateType(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}

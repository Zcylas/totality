package zcylas.totality.api.dialogue.conditions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dialogue.DialogueCondition;
import zcylas.totality.api.dialogue.NarrativeFlagsComponent;
import zcylas.totality.api.rpg.classes.ClassComponents;

/** Gates a choice behind the player currently having a given class (e.g. Barbarian). */
public record ClassCondition(Identifier classId) implements DialogueCondition {
    public static final MapCodec<ClassCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("class").forGetter(ClassCondition::classId)
    ).apply(i, ClassCondition::new));

    @Override
    public boolean test(ServerPlayer player, NarrativeFlagsComponent flags) {
        return ClassComponents.get(player).hasClass(classId);
    }

    @Override
    public String type() { return "class"; }
}

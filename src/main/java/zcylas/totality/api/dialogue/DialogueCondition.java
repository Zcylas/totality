package zcylas.totality.api.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dialogue.conditions.FlagCondition;

public interface DialogueCondition {
    DialogueCondition ALWAYS_TRUE = new DialogueCondition() {
        @Override public boolean test(ServerPlayer player, NarrativeFlagsComponent flags) { return true; }
        @Override public String type() { return "always_true"; }
    };

    Codec<DialogueCondition> CODEC = Codec.STRING.dispatch(
            "type",
            DialogueCondition::type,
            type -> switch (type) {
                case "flag" -> FlagCondition.MAP_CODEC;
                default -> MapCodec.unit(ALWAYS_TRUE);
            }
    );

    boolean test(ServerPlayer player, NarrativeFlagsComponent flags);
    String type();
}

package zcylas.totality.api.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dialogue.conditions.ClassCondition;
import zcylas.totality.api.dialogue.conditions.CreditCostCondition;
import zcylas.totality.api.dialogue.conditions.FlagCondition;
import zcylas.totality.api.dialogue.conditions.HasPhoneCondition;

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
                case "class" -> ClassCondition.MAP_CODEC;
                case "credit_cost" -> CreditCostCondition.MAP_CODEC;
                case "has_phone" -> HasPhoneCondition.MAP_CODEC;
                default -> MapCodec.unit(ALWAYS_TRUE);
            }
    );

    boolean test(ServerPlayer player, NarrativeFlagsComponent flags);
    String type();

    /** Message shown on a locked (visible but unpickable) choice when this condition fails. */
    default String lockReason() { return "Requirements not met"; }
}

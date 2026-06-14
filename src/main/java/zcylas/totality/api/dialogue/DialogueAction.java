package zcylas.totality.api.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dialogue.actions.SetFlagAction;

public interface DialogueAction {
    Codec<DialogueAction> CODEC = Codec.STRING.dispatch(
            "type",
            DialogueAction::type,
            type -> switch (type) {
                case "set_flag" -> SetFlagAction.MAP_CODEC;
                default -> throw new IllegalArgumentException("Unknown dialogue action type: " + type);
            }
    );

    void execute(ServerPlayer player);
    String type();
}

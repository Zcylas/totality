package zcylas.totality.api.dialogue.actions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.dialogue.DialogueAction;
import zcylas.totality.api.dialogue.DialogueComponents;

public record SetFlagAction(String flag, int value) implements DialogueAction {
    public static final MapCodec<SetFlagAction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("flag").forGetter(SetFlagAction::flag),
            Codec.INT.fieldOf("value").forGetter(SetFlagAction::value)
    ).apply(i, SetFlagAction::new));

    @Override
    public void execute(ServerPlayer player) {
        DialogueComponents.FLAGS.get((ComponentProvider) player).setFlag(flag, value);
    }

    @Override
    public String type() { return "set_flag"; }
}

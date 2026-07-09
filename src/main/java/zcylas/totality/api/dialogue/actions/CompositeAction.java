package zcylas.totality.api.dialogue.actions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dialogue.DialogueAction;

import java.util.List;

/** Runs multiple {@link DialogueAction}s from a single choice — a choice only carries one
 *  {@code action} field, but paying a cost (e.g. spend_credits) alongside a state change
 *  (e.g. set_flag) needs both to fire together. */
public record CompositeAction(List<DialogueAction> actions) implements DialogueAction {
    public static final MapCodec<CompositeAction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DialogueAction.CODEC.listOf().fieldOf("actions").forGetter(CompositeAction::actions)
    ).apply(i, CompositeAction::new));

    @Override
    public void execute(ServerPlayer player) {
        for (DialogueAction action : actions) action.execute(player);
    }

    @Override
    public String type() { return "composite"; }
}

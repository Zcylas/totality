package zcylas.totality.api.dialogue.actions;

import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dialogue.DialogueAction;
import zcylas.totality.api.quest.QuestManager;

/** Completes Mobile Banking's phone-link objective and unlocks the Bank app — the actual
 *  payoff of the Banker's "hand over your phone" dialogue choice. */
public record LinkPhoneAction() implements DialogueAction {
    public static final MapCodec<LinkPhoneAction> MAP_CODEC = MapCodec.unit(new LinkPhoneAction());

    @Override
    public void execute(ServerPlayer player) {
        QuestManager.onPhoneLinkedWithBanker(player);
    }

    @Override
    public String type() { return "link_phone"; }
}

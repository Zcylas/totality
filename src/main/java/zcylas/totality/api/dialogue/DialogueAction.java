package zcylas.totality.api.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dialogue.actions.CompositeAction;
import zcylas.totality.api.dialogue.actions.GrantQuestAction;
import zcylas.totality.api.dialogue.actions.LinkPhoneAction;
import zcylas.totality.api.dialogue.actions.OpenBankTellerAction;
import zcylas.totality.api.dialogue.actions.OpenProvisionerShopAction;
import zcylas.totality.api.dialogue.actions.OpenShopAction;
import zcylas.totality.api.dialogue.actions.SetFlagAction;
import zcylas.totality.api.dialogue.actions.SpendCreditsAction;

public interface DialogueAction {
    Codec<DialogueAction> CODEC = Codec.STRING.dispatch(
            "type",
            DialogueAction::type,
            type -> switch (type) {
                case "set_flag" -> SetFlagAction.MAP_CODEC;
                case "spend_credits" -> SpendCreditsAction.MAP_CODEC;
                case "open_bank_teller" -> OpenBankTellerAction.MAP_CODEC;
                case "open_shop" -> OpenShopAction.MAP_CODEC;
                case "open_provisioner_shop" -> OpenProvisionerShopAction.MAP_CODEC;
                case "grant_quest" -> GrantQuestAction.MAP_CODEC;
                case "link_phone" -> LinkPhoneAction.MAP_CODEC;
                case "composite" -> CompositeAction.MAP_CODEC;
                default -> throw new IllegalArgumentException("Unknown dialogue action type: " + type);
            }
    );

    void execute(ServerPlayer player);
    String type();
}

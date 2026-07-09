package zcylas.totality.api.dialogue.conditions;

import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.dialogue.DialogueCondition;
import zcylas.totality.api.dialogue.NarrativeFlagsComponent;
import zcylas.totality.api.quest.QuestManager;

/** Gates a choice behind having a Phone (any tier) equipped or anywhere in inventory. */
public record HasPhoneCondition() implements DialogueCondition {
    public static final MapCodec<HasPhoneCondition> MAP_CODEC = MapCodec.unit(new HasPhoneCondition());

    @Override
    public boolean test(ServerPlayer player, NarrativeFlagsComponent flags) {
        return QuestManager.playerHasAnyPhone(player);
    }

    @Override
    public String type() { return "has_phone"; }

    @Override
    public String lockReason() { return "Requires a phone on hand"; }
}
